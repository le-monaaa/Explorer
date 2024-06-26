package com.explorer.realtime.sessionhandling.waitingroom.repository;

import com.explorer.realtime.sessionhandling.waitingroom.dto.UserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class UserRepository {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final ReactiveHashOperations<String, Object, Object> reactiveHashOperations;

    private static final String KEY_PREFIX = "user:";

    public UserRepository(@Qualifier("channelReactiveRedisTemplate") ReactiveRedisTemplate<String, Object> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.reactiveHashOperations = reactiveRedisTemplate.opsForHash();
    }

    public Mono<Void> save(UserInfo userInfo, String teamCode, String mapId) {
        return Mono.when(
                reactiveHashOperations.put(KEY_PREFIX + userInfo.getUserId(), "nickname", userInfo.getNickname()),
                reactiveHashOperations.put(KEY_PREFIX + userInfo.getUserId(), "avatar", String.valueOf(userInfo.getAvatar())),
                reactiveHashOperations.put(KEY_PREFIX + userInfo.getUserId(), "channelId", teamCode),
                reactiveHashOperations.put(KEY_PREFIX + userInfo.getUserId(), "mapId", mapId)
        ).then();
    }

    public Mono<Boolean> delete(Long userId) {
        return reactiveHashOperations.delete(KEY_PREFIX + userId);
    }

    public Mono<Map<Object, Object>> findAll(Long userId) {
        return reactiveHashOperations.entries(KEY_PREFIX + userId).collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Void> updateUserData(Long userId, String channelId, String mapId) {
        return Mono.when(
                reactiveHashOperations.put(KEY_PREFIX + userId, "channelId", channelId),
                reactiveHashOperations.put(KEY_PREFIX + userId, "mapId", mapId)
        ).then();
    }

    public Mono<Map<String, String >> findAvatarAndNickname(Long userId) {
        List<Object> fields = Arrays.<Object>asList("nickname", "avatar");
        return reactiveHashOperations.multiGet(KEY_PREFIX + userId, fields)
                .map(values -> {
                    Map<String, String> userDetails = new HashMap<>();
                    if (values.size() == fields.size()) {
                        userDetails.put("nickname", String.valueOf(values.get(0)));
                        userDetails.put("avatar", String.valueOf(values.get(1)));
                    }
                    return userDetails;
                });
    }
}
