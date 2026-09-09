package com.amugeona.meogeuljido.auth.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(Long userId, String token, Duration ttl, boolean rememberMe) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId, new RefreshTokenValue(token, ttl.getSeconds(), rememberMe), ttl
        );
    }

    public Optional<RefreshTokenValue> find(Long userId) {
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return Optional.ofNullable((RefreshTokenValue) value);
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    public record RefreshTokenValue(String token, long ttlSeconds, boolean rememberMe) {}

}
