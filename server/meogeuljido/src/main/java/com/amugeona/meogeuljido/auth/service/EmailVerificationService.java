package com.amugeona.meogeuljido.auth.service;

import com.amugeona.meogeuljido.common.exception.CustomException;
import com.amugeona.meogeuljido.common.exception.ErrorCode;
import com.amugeona.meogeuljido.common.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;
    private final RateLimitGuard rateLimitGuard;

    /**
     * 6자리 인증코드를 생성해 Redis에 저장하고 메일로 발송한다. 60초 쿨다운 내 재요청 시 예외
     */
    public void issueCode(String keyPrefix, String email, String subject, String bodyFormat) {
        String cooldownKey = keyPrefix + "cooldown" + email;
        Boolean firstRequest = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", COOLDOWN);
        if (Boolean.FALSE.equals(firstRequest)) {
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
        }
        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(keyPrefix + email, code, CODE_TTL);
        rateLimitGuard.reset(attemptsKey(keyPrefix, email));
        mailService.send(email, subject, bodyFormat.formatted(code));
    }

    /**
     * 코드가 일치하면 소비(삭제), 불일치/만료 시 예외, 5회 연속 오답이면 코드 재발급 전까지 잠금
     */
    public void verifyCode(String keyPrefix, String email, String code) {
        String attemptsKey = attemptsKey(keyPrefix, email);
        rateLimitGuard.checkNotLocked(attemptsKey, MAX_VERIFY_ATTEMPTS, ErrorCode.TOO_MANY_REQUESTS);

        String key = keyPrefix + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            rateLimitGuard.recordFailure(attemptsKey, CODE_TTL);
            throw new CustomException(ErrorCode.CODE_MISMATCH);
        }
        rateLimitGuard.reset(attemptsKey);
        redisTemplate.delete(key);
    }

    /**
     *  이메일 -> 토큰 방향 저장(후속 요청이 이메일을 함께 보내는 흐름, 예: 회원가입)
     */
    public String issueTokenKeyedByEmail(String keyPrefix, String email, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(keyPrefix + email, token, ttl);
        return token;
    }

    public boolean consumeIfEmailTokenMatches(String keyPrefix, String email, String token) {
        String key = keyPrefix + email;
        String stored = redisTemplate.opsForValue().get(key);
        boolean matches = stored != null && stored.equals(token);
        if (matches) {
            redisTemplate.delete(key);
        }
        return matches;
    }

    /**
     * 토큰 -> 이메일 방향 저장(후속 요청이 토큰만 보내는 흐름, 예: 비밀번호 재설정)
     */
    public String issueTokenKeyedByToken(String keyPrefix, String email, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(keyPrefix + token, email, ttl);
        return token;
    }

    public Optional<String> consumeTokenKeyedByToken(String keyPrefix, String token) {
        String key = keyPrefix + token;
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        }
        return Optional.ofNullable(email);
    }

    private String attemptsKey(String keyPrefix, String email) {
        return keyPrefix + "attempts:" + email;
    }

}
