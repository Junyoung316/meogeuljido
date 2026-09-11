package com.amugeona.meogeuljido.auth.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(Long userId, String token, Duration ttl, boolean rememberMe) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId, new RefreshTokenValue(token, Instant.now().plus(ttl), rememberMe), ttl
        );
    }

    /**
     * 조회와 동시에 삭제(원자적 GETDEL) - 재발급은 "이번 한 번만 유효한 티켓을 소비"하는 구조
     * 동시 요청 중 하나만 통과, 이미 소비된 토큰은 재사용되면 즉시 거부
     */
    public Optional<RefreshTokenValue> findAndInvalidate(Long userId) {
        Object value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + userId);
        return Optional.ofNullable((RefreshTokenValue) value);
    }

    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    public record RefreshTokenValue(String token, Instant expiresAt, boolean rememberMe) {}

}
