package com.amugeona.meogeuljido.auth.service;

import com.amugeona.meogeuljido.common.exception.CustomException;
import com.amugeona.meogeuljido.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitGuard {

    private final StringRedisTemplate redisTemplate;

    /**
     * key의 누적 실패 횟수가 이미 maxAttempts 이상이면 예외, 예외 종류는 호출부가 정함(용도별로 의미가 다름)
     */
    public void checkNotLocked(String key, int maxAttempts, ErrorCode errorCode) {
        String stored = redisTemplate.opsForValue().get(key);
        int count = stored == null ? 0 : Integer.parseInt(stored);

        if (count >= maxAttempts) {
            throw new CustomException(errorCode);
        }
    }

    /**
     * TTL 없이 카운터만 증가 - 시간 경과로는 절대 안 풀리고 reset()이 명시적으로 호출되기 전까진
     * 계속 잠겨 있어야 하는 용도(로그인 잠금 등)로 사용
     */
    public long recordFailurePermanently(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        return count == null ? 0 : count;
    }

    /**
     * 실패 시 호출 - 카운터를 1 증가시키고, 첫 실패일 때만 TTL을 검
     */
    public long recordFailureWithExpiry(String key, Duration window) {
        redisTemplate.opsForValue().setIfAbsent(key, "0", window);
        Long count = redisTemplate.opsForValue().increment(key);
        return count == null ? 0 : count;
    }

    /**
     * 성공 시 호출 - 카운터를 초기화 함
     */
    public void reset(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 성공/실패 여부와 무관하게 호출 자체를 카운트
     * - recordFailureWithExpiry()와 달리 "실패했을 때만"이 아니라 매 호출마다 실행,
     * 한도 초과 시 즉시 예외를 던짐
     */
    public void checkAndCountAttempt(String key, int maxAttempts, Duration window, ErrorCode errorCode) {
        checkNotLocked(key, maxAttempts, errorCode);
        recordFailureWithExpiry(key, window);
    }

}
