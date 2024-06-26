package com.explorer.realtime.sessionhandling.ingame.event;

import com.explorer.realtime.gamedatahandling.component.common.mapinfo.repository.CurrentMapRepository;
import com.explorer.realtime.gamedatahandling.component.common.mapinfo.repository.MapObjectRepository;
import com.explorer.realtime.gamedatahandling.component.personal.playerInfo.event.SetInitialPlayerInfo;
import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Unicasting;
import com.explorer.realtime.global.component.session.SessionManager;
import com.explorer.realtime.global.redis.ChannelRepository;
import com.explorer.realtime.global.util.MessageConverter;
import com.explorer.realtime.sessionhandling.waitingroom.dto.UserInfo;
import com.explorer.realtime.sessionhandling.waitingroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class RestartGame {

    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final ChannelRepository channelRepository;
    private final Unicasting unicasting;
    private final LabDataMongoToRedis labDataMongoToRedis;
    private final SetInitialPlayerInfo setInitialPlayerInfo;
    private final InventoryDataMongoToRedis inventoryDataMongoToRedis;
    private final MapObjectRepository mapObjectRepository;
    private final CurrentMapRepository currentMapRepository;
    private final LabLevelDataMongoToRedis labLevelDataMongoToRedis;
    private final MapDataMongoToRedis mapDataMongoToRedis;

    public Mono<Void> process(String channelId, UserInfo userInfo, Connection connection) {
        // 사용자 정보를 Redis에 저장
        log.info("restart initial");
//        (existChannel(channelId))
        labDataMongoToRedis.process(channelId).subscribe();
        inventoryDataMongoToRedis.process(channelId, userInfo.getUserId()).subscribe();
        userRepository.save(userInfo, channelId, "1").subscribe();
        channelRepository.save(channelId, userInfo.getUserId(), 0).subscribe();
        setInitialPlayerInfo.process(channelId, 8).subscribe();
//        setInitialPlayerInfo.process(channelId, 8).subscribe();
        createConnectionInfo(channelId, userInfo, connection).subscribe();
        return Mono.empty();
    }

    private Mono<Map<String, Object>> createConnectionInfo(String channelId, UserInfo userInfo, Connection connection) {
        sessionManager.setConnection(userInfo.getUserId(), connection);
//        setInitialPlayerInfo.process(channelId, 8).subscribe();
        Map<String, Object> map = new HashMap<>();
        return mapDataMongoToRedis.process(channelId)
                .then(currentMapRepository.findMapId(channelId))
//        return currentMapRepository.findMapId(channelId)
                .flatMap(field -> {
                    Integer mapId = Integer.parseInt(String.valueOf(field));
                    return mapObjectRepository.findMapData(channelId, mapId)
                            .flatMap(mapData -> {
                                map.put("mapId", mapId);
                                map.put("mapData", mapData);
                                unicasting.unicasting(channelId, userInfo.getUserId(), MessageConverter.convert(Message.success("restartGame", CastingType.UNICASTING, map))).subscribe();
                                return Mono.just(map);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Integer mapId = 1;

                    return mapObjectRepository.findMapData(channelId, 1)
                            .flatMap(mapData -> {
                                map.put("mapId", mapId);
                                map.put("mapData", mapData);
                                labLevelDataMongoToRedis.process(channelId).subscribe();
                                currentMapRepository.save(channelId, 1).subscribe();
                                unicasting.unicasting(channelId, userInfo.getUserId(), MessageConverter.convert(Message.success("restartGame", CastingType.UNICASTING, map))).subscribe();
                                return Mono.just(map);
                            });
                }));
    }

}

