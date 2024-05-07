package com.explorer.realtime.sessionhandling.ingame.event;

import com.explorer.realtime.gamedatahandling.component.common.mapinfo.event.InitializeMapObject;
import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Broadcasting;
import com.explorer.realtime.global.redis.ChannelRepository;
import com.explorer.realtime.global.util.MessageConverter;
import com.explorer.realtime.sessionhandling.ingame.document.Channel;
import com.explorer.realtime.sessionhandling.ingame.repository.ChannelMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartGame {

    private final ChannelMongoRepository channelMongoRepository;
    private final ChannelRepository channelRepository;
    private final Broadcasting broadcasting;
    private final InitializeMapObject initializeMapObject;

    public Mono<Void> process(String teamCode, String channelName) {
        log.info("Processing game start for teamCode: {}", teamCode);

        Mono<String> saveChannelMono = saveChannel(teamCode, channelName);

        saveChannelMono.subscribe(channelId -> {
            transferAndInitializeChannel(teamCode, channelId)
                    .then(Mono.defer(() -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("channelId", channelId);
                        initializeMapObject.initializeMapObject(channelId).subscribe();
                        return broadcasting.broadcasting(channelId, MessageConverter.convert(Message.success("startGame", CastingType.BROADCASTING, map)));
                    }))
                    .subscribe();
        });

        return Mono.empty();
    }

    private Mono<Void> transferAndInitializeChannel(String teamCode, String channelId) {
        return channelRepository.findAll(teamCode)
                .flatMapMany(entries -> Flux.fromIterable(entries.entrySet()))
                .flatMap(entry -> channelRepository.save(channelId, Long.parseLong((String) entry.getKey()), Integer.parseInt((String) entry.getValue())))
                .then(channelRepository.deleteAll(teamCode))
                .then();
    }

    private Mono<String> saveChannel(String teamCode, String channelName) {
        return channelRepository.findAllFields(teamCode)
                .map(field -> Long.valueOf(String.valueOf(field)))
                .collectList()
                .flatMap(result -> {
                    log.info("playerList : {}", result);
                    return channelMongoRepository.save(Channel.from(channelName, new HashSet<>(result)))
                            .map(Channel::getId)
                            .doOnSuccess(channelId -> log.info("channelId : {}", channelId))
                            .flatMap(Mono::just);
                });
    }

}


