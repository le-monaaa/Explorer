package com.explorer.realtime.gamedatahandling.inventory.event;

import com.explorer.realtime.gamedatahandling.component.personal.inventoryInfo.dto.InventoryInfo;
import com.explorer.realtime.gamedatahandling.component.personal.inventoryInfo.repository.InventoryRepository;
import com.explorer.realtime.gamedatahandling.component.personal.playerInfo.repository.PlayerInfoRepository;
import com.explorer.realtime.gamedatahandling.inventory.exception.InventoryErrorCode;
import com.explorer.realtime.gamedatahandling.inventory.exception.InventoryException;
import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Unicasting;
import com.explorer.realtime.global.util.MessageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UseItemInInventory {

    private final InventoryRepository inventoryRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final Unicasting unicasting;

    private static final String eventName = "useItemInInventory";

    public Mono<Void> process(JSONObject json) {
        String channelId = json.getString("channelId");
        Long userId = json.getLong("userId");
        int inventoryIdx = json.getInt("inventoryIdx");
        log.info("[process] channelId : {}, userId : {}, inventoryIdx : {}", channelId, userId, inventoryIdx);

        return getInventoryCntByUserId(channelId, userId)
                .flatMap(maxCnt -> {
                    if (inventoryIdx < 0 || inventoryIdx >= maxCnt) {
                        return Mono.error(new InventoryException(InventoryErrorCode.OUT_OF_RANGE_INDEX));
                    }
                    return inventoryRepository.findByInventoryIdx(channelId, userId, inventoryIdx);
                })
                .flatMap(inventory -> {
                    if (String.valueOf(inventory).isEmpty()) {
                        return Mono.error(new InventoryException(InventoryErrorCode.EMPTY_INVENTORY));
                    }

                    InventoryInfo inventoryInfo = InventoryInfo.ofString(inventoryIdx, String.valueOf(inventory));
                    log.info("[process] inventoryInfo : {}", inventoryInfo);

                    int result = inventoryInfo.getItemCnt() - 1;
                    if (result > 0) {
                        inventoryInfo.setItemCnt(result);
                        return inventoryRepository.save(channelId, userId, inventoryInfo)
                                .flatMap(savedItem -> {
                                    log.info("[process] 결과 반영 후 아이템이 있는 경우 : {}", inventoryInfo);
                                    return unicasting(channelId, userId, inventoryInfo);
                                });
                    } else {
                        return inventoryRepository.deleteByInventoryIdx(channelId, userId, inventoryIdx)
                                .flatMap(deletedCount -> {
                                    InventoryInfo response = InventoryInfo.ofString(inventoryIdx, "");
                                    log.info("[process] 결과 반영 후 아이템이 없는 경우 : {}", response);
                                    return unicasting(channelId, userId, response);
                                });
                    }
                })
                .onErrorResume(InventoryException.class, error -> {
                    log.info("[process] errorCode : {}, errorMessage : {}", error.getErrorCode(), error.getMessage());
                    return unicasting.unicasting(
                            channelId,
                            userId,
                            MessageConverter.convert(Message.fail(eventName, CastingType.UNICASTING, String.valueOf(error.getErrorCode()), error.getMessage()))
                    ).then();
                });
    }

    private Mono<Integer> getInventoryCntByUserId(String channelId, Long userId) {
        log.info("[getInventoryCntByUserId] channelId : {}, userId : {}", channelId, userId);

        return playerInfoRepository.findInventoryCnt(channelId, userId)
                .map(map -> Integer.parseInt(String.valueOf(map)));
    }

    public Mono<Void> unicasting(String channelId, Long userId, InventoryInfo inventoryInfo) {
        log.info("[unicasting] channelId : {}, userId : {}, inventoryInfo : {}", channelId, userId, inventoryInfo);

        return unicasting.unicasting(
                channelId,
                userId,
                MessageConverter.convert(Message.success(eventName, CastingType.UNICASTING, inventoryInfo))
        );
    }

}
