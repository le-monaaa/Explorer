package com.explorer.realtime.gamedatahandling.laboratory.event;

import com.explorer.realtime.gamedatahandling.laboratory.dto.ItemInfo;
import com.explorer.realtime.gamedatahandling.laboratory.dto.UserInfo;
import com.explorer.realtime.gamedatahandling.laboratory.repository.ElementLaboratoryRepository;
import com.explorer.realtime.gamedatahandling.logicserver.ToLogicServer;
import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Broadcasting;
import com.explorer.realtime.global.component.broadcasting.Unicasting;
import com.explorer.realtime.global.util.MessageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Synthesize {

    private final ToLogicServer toLogicServer;
    private final ElementLaboratoryRepository elementLaboratoryRepository;
    private final Unicasting unicasting;
    private final Broadcasting broadcasting;

    @Value("${logic.laboratory.synthesize-url}")
    private String synthesizeUrl;


    /*
     * 파라미터
     * JSONObject json : {"userId":{userId}, "channelId":{channelId}, "itemCategory" : {itemCategory}, "itemId" : {itemId}}
     */
    public Mono<Void> process(JSONObject json) {

        UserInfo userInfo = UserInfo.of(json);

        return requestElementsForSynthesize(json) // Logic 서버에 합성에 필요한 element 데이터 요청
                .flatMap(response -> checkElementsInLaboratory(response, userInfo, json))  // 현재 원소 연구소에 element가 있는지 조회
                .then();
    }

    /*
     * [LOGIC 서버에 요청 : 합성에 필요한 element 데이터 요청]
     * 파라미터 : { .. , "channelId" : {channelId}, "userId": "{userId}", "itemCategory" : "compound", "itemId" : {itemId}}
     * 반환값 :
     *  - 타입 : Mono<String>
     *  - 값 :  { {itemCategory}:{itemId} : {itemCnt}, {itemCategory}:{itemId} : {itemCnt}, .... }
     */
    private Mono<String> requestElementsForSynthesize(JSONObject json) {

        JSONObject itemInfo = new JSONObject(ItemInfo.of(json));

        log.info("Logic server Request Data: {}", itemInfo);

        return Mono.create(sink -> {
            toLogicServer.sendRequestToHttpServer(String.valueOf(itemInfo), synthesizeUrl)
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
     * [원소 연구소에 필요한 element가 있는지 확인]
     * 파라미터
     *  - String response : { {itemCategory}:{itemId} : {itemCnt}, {itemCategory}:{itemId} : {itemCnt}, .... }
     *  - UserInfo userInfo : channelId, userId
     * 반환값
     *  - 타입 : Mono<Void>
     */
    private Mono<Void> checkElementsInLaboratory(String response, UserInfo userInfo, JSONObject json) {

        JSONObject responseJson = new JSONObject(response);

        // 합성에 필요한 재료 하나씩 원소 연구소에 있는지 확인
        return Flux.fromIterable(responseJson.keySet())
                .flatMap(key ->
                        elementLaboratoryRepository.findMaterial(userInfo.getChannelId(), key, responseJson.optInt(key, 0))
                                .flatMap(found -> {
                                    log.info("Key: {}, Required Count: {}, Found: {}", key, responseJson.optInt(key, 0), found);

                                    // 특정 원소가 연구소에 없는 경우
                                    if (!found) {
                                        log.warn("Fail: Key {} with required count {} is not sufficient", key, responseJson.optInt(key, 0));
                                        return unicastingFailData(json, "noItem").then(Mono.empty());
                                    }
                                    return Mono.just(true);
                                })
                )
                .collectList() // Collect all results to ensure all elements are checked
                .flatMap(results -> {
                    // 요소가 충분하지 않으면 종료
                    if (results.isEmpty()) {
                        return Mono.empty();
                    // 모든 요소가 충분한 경우
                    } else {
                        log.info("Success: All elements are sufficient");

                        Mono<Void> useElements = useElementsInLaboratory(responseJson, userInfo);

                        // 2) 생성한 화합물 : redis-game에 연구소-compound 상태 update
                        Mono<Void> createCompound = createCompoundInLaboratory(json);

                        return Mono.when(useElements, createCompound)
                                .doOnError(error -> log.info("ERROR useElements, createCompound in checkElementsInLaboratory : {}", error.getMessage()))
                                .then(unicastingLaboratory(json));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failure: check Elements In Laboratory for Synthesize", e);
                    return Mono.empty();
                });
    }

    /*
     * [redis-game에 합성에 사용된 element 데이터 update]
     * 파라미터
     *  - JSONObject json : { {itemCategory}:{itemId} : {itemCnt}, {itemCategory}:{itemId} : {itemCnt}, .... }
     *  - UserInfo userInfo : channelId, userId
     * 반환값
     *  - 타입 : Mono<Void>
     */
    private Mono<Void> useElementsInLaboratory(JSONObject json, UserInfo userInfo) {

        return Flux.fromIterable(json.keySet())
                .flatMap(key ->
                        elementLaboratoryRepository.useMaterial(userInfo.getChannelId(), key, json.optInt(key, 0))
                )
                .then();
    }

    /*
     * [redis-game에 생성한 compound 데이터 update]
     * 파라미터 : { .. , "channelId" : {channelId}, "userId": "{userId}", "itemCategory" : "compound", "itemId" : {itemId}}
     * 반환값
     *  - 타입 : Mono<Void>
     */
    private Mono<Void> createCompoundInLaboratory(JSONObject json) {
        String channelId = json.getString("channelId");
        String itemCategory = json.getString("itemCategory");
        int itemId = json.getInt("itemId");
        return elementLaboratoryRepository.createCompound(channelId, itemCategory, itemId).then();
    }

    /*
     * [Unicasting : 변경된 연구소 element, compound 저장 상태]
     * 파라미터 : { .. , "channelId" : {channelId}, "userId": "{userId}", "itemCategory" : "compound", "itemId" : {itemId}}
     * 반환값
     *  - 타입 : Mono<Void>
     */
    private Mono<Void> unicastingLaboratory(JSONObject json) {

        String channelId = json.getString("channelId");
        Long userId = json.getLong("userId");
        Map<String, Object> dataBody = new HashMap<>();
        Mono<List<Integer>> getElements = elementLaboratoryRepository.findAllElements(json)
                .doOnNext(elementList -> {
                    dataBody.put("element", elementList);
                });
        Mono<List<Integer>> getCompounds = elementLaboratoryRepository.findAllCompounds(json)
                .doOnNext(compoundList -> {
                    dataBody.put("compound", compoundList);
                });
        return Mono.when(getElements, getCompounds)
                .then(Mono.defer(() ->
                        unicasting.unicasting(
                                channelId, userId,
                                MessageConverter.convert(Message.success("synthesizing", CastingType.UNICASTING, dataBody))
                        )));
    }

    /*
     * [Unicasting : fail output data]
     * 파라미터
     * - JSONObject json : {..., "channelId":{channelId}, "userId":{userId}, "itemCategory" : "compound", "itemId" : {itemId} }
     * - String msg : "noItem"
     */
    private Mono<Void> unicastingFailData(JSONObject json, String msg) {
        String channelId = json.getString("channelId");
        Long userId = json.getLong("userId");
        Map<String, String> dataBody = new HashMap<>();
        dataBody.put("msg", msg);

        return unicasting.unicasting(channelId, userId,
                        MessageConverter.convert(Message.fail("synthesizing", CastingType.UNICASTING, dataBody)))
                .then();
    }
}
