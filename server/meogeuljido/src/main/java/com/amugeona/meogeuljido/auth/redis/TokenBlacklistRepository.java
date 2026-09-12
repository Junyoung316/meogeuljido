package com.amugeona.meogeuljido.auth.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    /**
     * 특정 토큰이 아니라 "이 시각 이전에 발급된 건 전부 무효"라는 기준선을 저장
     * TTL은 그 시점에 이미 발급된 AT가 자연 만료될 시간(액세스 토큰 최대 유효기간)만큼만 두면 충분
     * - 그 이후엔 어차피 그 시점 이전에 발급된 토큰은 전부 자연 만료라 이 기준선 자체가 필요 없어짐
     * JWT의 iat는 초 단위로 잘려서 기록되므로, "지금"을 그대로 기준선으로 쓰면 이 메서드가 호출된
     * 것과 같은 초에 발급된 진짜 새 토큰까지 "이전"으로 오판할 수 있음 - 그 모호한 초 전체를
     * 무효화 대상에 포함시키기 위해 기준선을 다음 초의 시작 시각으로 올려잡음
     */
    public void blacklistAllIssuedBefore(Long userId, Duration accessTokenValidity) {
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        redisTemplate.opsForValue().set(INVALIDATE_BEFORE_PREFIX + userId, cutoff.toString(), accessTokenValidity);
    }

    public boolean isRejected(String accessToken, Long userId, Instant issuedAt) {
        List<String> values = redisTemplate.opsForValue().multiGet(
                List.of(KEY_PREFIX + accessToken, INVALIDATE_BEFORE_PREFIX + userId)
        );

        if (values == null) {
            return false;
        }

        if (values.get(0) != null) {
            return true;
        }

        String invalidateBefore = values.get(1);
        return invalidateBefore != null && issuedAt.isBefore(Instant.parse(invalidateBefore));
    }

}
