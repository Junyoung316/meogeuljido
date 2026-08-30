package com.amugeona.meogeuljido.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.lang.System.currentTimeMillis;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofHours(2);
    private static final String CLAIM_ROLE = "role";
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
        try {
            Claims claims = parseClaims(token);
            if(!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                log.warn("Refresh token presented where access token was expected");
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional <RefreshTokenClaims> parseRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if(!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
                log.warn("Access token presented where refresh token was expected");
                return Optional.empty();
            }
            long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Optional.of(new RefreshTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    Duration.ofMillis(Math.max(remainingMillis, 0))));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
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
