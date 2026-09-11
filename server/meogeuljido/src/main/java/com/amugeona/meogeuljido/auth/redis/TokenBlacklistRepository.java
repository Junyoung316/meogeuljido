package com.amugeona.meogeuljido.auth.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";
    private static final String INVALIDATE_BEFORE_PREFIX = "invalidate-before:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String accessToken, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + accessToken, "1", ttl);
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accessToken));
    }

    /**
     * 특정 토큰이 아니라 "이 시각 이전에 발급된 건 전부 무효"라는 기준선을 저장
     * TTL은 그 시점에 이미 발급된 AT가 자연 만료될 시간(액세스 토큰 최대 유효기간)만큼만 두면 충분
     * - 그 이후엔 어차피 그 시점 이전에 발급된 토큰은 전부 자연 만료라 이 기준선 자체가 필요 없어짐
     */
    public void blacklistAllIssuedBefore(Long userId, Instant cutoff,  Duration accessTokenValidity) {
        redisTemplate.opsForValue().set(INVALIDATE_BEFORE_PREFIX + userId, cutoff.toString(), accessTokenValidity);
    }

    public boolean isIssuedBeforeInvalidation(Long userId, Instant issuedAt) {
        String stored = redisTemplate.opsForValue().get(INVALIDATE_BEFORE_PREFIX + userId);
        return stored != null && issuedAt.isBefore(Instant.parse(stored));
    }

}
