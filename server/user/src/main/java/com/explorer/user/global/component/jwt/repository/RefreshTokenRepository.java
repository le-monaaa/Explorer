package com.explorer.user.global.component.jwt.repository;

import com.explorer.user.global.component.jwt.JwtProps;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProps jwtProps;

    private static final String KEY_PREFIX = "refreshToken:";

    public void save(String userId, String refreshToken) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + userId, refreshToken, jwtProps.refreshExpiration());
    }

    public Optional<String> find(String userId) {
        String token = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    public boolean exist(String userId) {
        return redisTemplate.hasKey(KEY_PREFIX + userId);
    }

    public void delete(String userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

}