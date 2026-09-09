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
     * 실패 시 호출 - 카운터를 1 증가시키고, 첫 실패일 때만 TTL을 검
     */
    public void recordFailure(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    /**
     * 성공 시 호출 - 카운터를 초기화 함
     */
    public void reset(String key) {
        redisTemplate.delete(key);
    }

}
