package com.amugeona.meogeuljido.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.SecretKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofHours(1);
    private static final String CLAIM_ROLE = "role";

    /**
     * 토큰 종류를 구분하는 claim. Refresh Token이 Access Token으로 재생(replay)되는 것을
     * 막기 위해 발급 시 타입을 명시, 파싱 시 기대한 타입과 일치하는지 반드시 검증
     */
    private static final String CLAIM_TYPE= "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String role) {
        return generateToken(userId, role, TYPE_ACCESS, ACCESS_TOKEN_VALIDITY);
    }

    public String generateRefreshToken(Long userId, Duration validity) {
        return generateToken(userId, null,TYPE_REFRESH, validity);
    }

    private String generateToken(Long userId, String role, String type, Duration validity) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity.toMillis()));
        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.signWith(key).compact();
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        return parseTyped(token, TYPE_ACCESS, claims -> new AccessTokenClaims(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_ROLE, String.class)));
    }

    public Optional <RefreshTokenClaims> parseRefreshToken(String token) {
        return parseTyped(token, TYPE_REFRESH, claims -> {
           long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
           return new RefreshTokenClaims(
                   Long.valueOf(claims.getSubject()),
                   Duration.ofMillis(Math.max(remainingMillis, 0))
           );
        });
    }

    private <T> Optional<T> parseTyped(String token, String expectedType, Function<Claims, T> mapper) {
        try {
            Claims claims = parseClaims(token);
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                log.warn("Unexpected token type presented: expected {}", expectedType);
                return Optional.empty();
            }
            return Optional.of(mapper.apply(claims));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid {} token: {}",  expectedType, e.getMessage());
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record AccessTokenClaims(Long userId, String role) {
    }

    public record RefreshTokenClaims(Long userId, Duration remainingValidity) {
    }
}
