package com.amugeona.meogeuljido.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofHours(2);
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String role) {
        return generateToken(userId, role, ACCESS_TOKEN_VALIDITY);
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, null, REFRESH_TOKEN_VALIDITY);
    }

    private String generateToken(Long userId, String role, Duration validity) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity.toMillis()));
        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.signWith(key).compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    public Duration getRemainingValidity(String token) {
        Date expiration = parseClaims(token).getExpiration();
        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(remainingMillis, 0));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
