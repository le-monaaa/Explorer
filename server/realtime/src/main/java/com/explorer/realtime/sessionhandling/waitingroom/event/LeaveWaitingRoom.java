package com.explorer.realtime.sessionhandling.waitingroom.event;

import com.explorer.realtime.global.common.dto.Message;
import com.explorer.realtime.global.common.enums.CastingType;
import com.explorer.realtime.global.component.broadcasting.Broadcasting;
import com.explorer.realtime.global.component.broadcasting.Unicasting;
import com.explorer.realtime.global.component.session.SessionManager;
import com.explorer.realtime.global.redis.ChannelRepository;
import com.explorer.realtime.global.util.MessageConverter;
import com.explorer.realtime.sessionhandling.waitingroom.exception.WaitingRoomErrorCode;
import com.explorer.realtime.sessionhandling.waitingroom.exception.WaitingRoomException;
import com.explorer.realtime.sessionhandling.waitingroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveWaitingRoom {

    private final ChannelRepository channelRepository;
    private final SessionManager sessionManager;
    private final UserRepository userRepository;
    private final Unicasting unicasting;
    private final Broadcasting broadcasting;

    private static final String eventName = "leaveWaitingRoom";

    public Mono<Void> process(JSONObject json, Connection connection) {
        String teamCode = json.getString("teamCode");
        Long userId = json.getLong("userId");
        boolean isLeader = json.getBoolean("isLeader");
        log.info("[process] teamCode : {}, userId : {}, isLeader : {}", teamCode, userId, isLeader);

        return existByTeamCode(teamCode)
                .flatMap(exists -> {
                    if (isLeader) {
                        return deleteWaitingRoom(teamCode);
                    } else {
                        return leaveWaitingRoom(teamCode, userId);
                    }
                })
                .onErrorResume(WaitingRoomException.class, error -> {
                    if (Objects.requireNonNull(error.getErrorCode()) == WaitingRoomErrorCode.NOT_EXIST_TEAMCODE) {
                        unicasting.unicasting(
                                connection,
                                userId,
                                MessageConverter.convert(Message.fail(eventName, CastingType.UNICASTING, String.valueOf(error.getErrorCode()), error.getMessage()))
                        ).subscribe();
                        return Mono.empty();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Boolean> existByTeamCode(String teamCode) {
        log.info("[existByTeamCode] teamCode : {}", teamCode);

        if (teamCode == null || teamCode.isEmpty()) {
            return Mono.error(new WaitingRoomException(WaitingRoomErrorCode.NOT_EXIST_TEAMCODE));
        }

        return channelRepository.exist(teamCode)
                .flatMap(isExist -> {
                    if (!isExist) {
                        return Mono.error(new WaitingRoomException(WaitingRoomErrorCode.NOT_EXIST_TEAMCODE));
                    } else {
                        return Mono.just(true);
                    }
                });
    }

    private Mono<Void> deleteWaitingRoom(String teamCode) {
        log.info("[deleteWaitingRoom] teamCode : {}", teamCode);

        return broadcasting.broadcasting(
                        teamCode,
                        MessageConverter.convert(Message.success(eventName, CastingType.BROADCASTING))
                )
                .then(channelRepository.findAllFields(teamCode)
                        .flatMap(value -> userRepository.delete(Long.valueOf(String.valueOf(value))))
                        .then(channelRepository.deleteAll(teamCode))
                ).then();
    }

    private Mono<Void> leaveWaitingRoom(String teamCode, Long userId) {
        log.info("[leaveWaitingRoom] teamCode : {}, userId : {}", teamCode, userId);

        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);

        return broadcasting.broadcasting(
                        teamCode,
                        MessageConverter.convert(Message.success(eventName, CastingType.BROADCASTING, map))
                )
                .then(userRepository.delete(userId))
                .then(channelRepository.deleteByUserId(teamCode, userId))
                .then(Mono.fromRunnable(() -> sessionManager.removeConnection(userId)));
    }

}
