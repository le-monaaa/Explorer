package com.explorer.realtime.gamedatahandling.laboratory.event;

import com.explorer.realtime.gamedatahandling.laboratory.repository.LaboratoryLevelRepository;
import com.explorer.realtime.gamedatahandling.logicserver.ToLogicServer;
import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Unicasting;
import com.explorer.realtime.global.util.MessageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Upgrade {

    @Value("${logic.laboratory.upgrade-url}")
    private String upgradeUrl;

    private final LaboratoryLevelRepository laboratoryLevelRepository;
    private final Unicasting unicasting;
    private final ToLogicServer toLogicServer;

    public Mono<Void> process(JSONObject json) {

        return checkLabLevel(json)                                                  // 1) 연구소 레벨 조회
                .flatMap(labLevel -> {
                    if (labLevel<0 || labLevel>=3) {                                      // 2-1) 업그레이드 불가능한 레벨인 경우
                        return unicastingFailData(json, "cannotUpdate");
                    } else {                                                        // 2-2) 업그레이드 가능한 레벨인 경우
                        return requestMaterialsForUpgrade(json, labLevel)
                                .doOnNext(response -> log.info("Logic Server Response: {}", response))
                                .then();
                    }
                });
    }

    /*
     * [연구소의 레벨을 확인한다]
     *
     * 파라미터
     * - 타입 : JSONObject
     * - 값 : {..., "channelId":{channelId}, "userId":{userId}, "labId":{labId}}
     *
     * 반환값
     * - 타입 : Mono<Object>
     * - 값 : {level}
     *
     * 연구소 레벨 데이터 (redis-game)
     * key : labLevel:{channelId}:{labId}
     * value: {level}
     */
    private Mono<Integer> checkLabLevel(JSONObject json) {
        String channelId = json.getString("channelId");
        int labId = json.getInt("labId");
        return laboratoryLevelRepository.findLabLevel(channelId, labId)
                .map(levelStr -> {
                    try {
                        return Integer.parseInt(levelStr.toString());
                    } catch (NumberFormatException e) {
                        log.error("Failed to parse lab leve: {}", levelStr, e);
                        return -1;
                    }
                });
    }

    /*
     * [LOGIC 서버에 요청 : 합성에 필요한 element 데이터 요청]
     * 파라미터
     * - JSONObject json : { .. , "channelId" : {channelId}, "userId" : {userId}, "labId" : {labId}}
     * - int labLevel : {level}
     *
     * Logic Server :: POST data (String)
     * {"labId":{labId}, "labLevel":{labLevel}}
     *
     * Logic Server :: GET data (String)
     * {
     *  {itemCategory}:{itemId}  :  {itemCnt},
     *  {itemCategory}:{itemId}  :  {itemCnt},
     *                ...
     * }
     *
     * 반환값 :
     *  - 타입 : Mono<String>
     *  - 값 :  { {itemCategory}:{itemId} : {itemCnt}, {itemCategory}:{itemId} : {itemCnt}, .... }
     */
    private Mono<String> requestMaterialsForUpgrade(JSONObject json, int labLevel) {

        JSONObject request = new JSONObject().put("labId", 0).put("labLevel",labLevel);
        log.info("Logic server Request Data: {}", request);

        return Mono.create(sink -> {
            toLogicServer.sendRequestToHttpServer(String.valueOf(request), upgradeUrl)
                    .subscribe(response -> {
                        log.info("Logic server response: {}", response);
                        sink.success(response);
                    }, error -> {
                        log.error("Error in retrieving data from logic server");
                        sink.error(error);
                    });
        });
    }

    /*
     * [Unicasting : fail output data]
     * 파라미터
     * - JSONObject json : {..., "channelId":{channelId}, "userId":{userId}, "labId" : {labId}}
     * - String msg : "cannotUpgrade" 또는 "noItem"
     */
    private Mono<Void> unicastingFailData(JSONObject json, String msg) {
        String channelId = json.getString("channelId");
        Long userId = json.getLong("userId");
        Map<String, String> dataBody = new HashMap<>();
        dataBody.put("msg", msg);

        return unicasting.unicasting(channelId, userId,
                MessageConverter.convert(Message.fail("upgrade", CastingType.UNICASTING, dataBody)))
                .then();
    }

}
