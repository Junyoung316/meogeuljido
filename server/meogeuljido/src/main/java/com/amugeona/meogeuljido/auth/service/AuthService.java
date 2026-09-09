package com.amugeona.meogeuljido.auth.service;

import com.amugeona.meogeuljido.auth.dto.LoginRequest;
import com.amugeona.meogeuljido.auth.dto.LoginResponse;
import com.amugeona.meogeuljido.auth.dto.SignupRequest;
import com.amugeona.meogeuljido.auth.redis.RefreshTokenRepository.RefreshTokenValue;
import com.amugeona.meogeuljido.auth.redis.RefreshTokenRepository;
import com.amugeona.meogeuljido.auth.redis.TokenBlacklistRepository;
import com.amugeona.meogeuljido.auth.security.CustomUserDetails;
import com.amugeona.meogeuljido.common.event.AuditLogEvent;
import com.amugeona.meogeuljido.common.exception.CustomException;
import com.amugeona.meogeuljido.common.exception.ErrorCode;
import com.amugeona.meogeuljido.common.security.JwtTokenProvider;
import com.amugeona.meogeuljido.user.dto.UserResponse;
import com.amugeona.meogeuljido.user.entity.User;
import com.amugeona.meogeuljido.user.repository.UserRepository;
import com.amugeona.meogeuljido.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String SIGNUP_CODE_PREFIX = "signup:verify:";
    private static final String SIGNUP_TOKEN_PREFIX = "signup:verified:";
    private static final String RESET_CODE_PREFIX = "password-reset:verify:";
    private static final String RESET_TOKEN_PREFIX = "password-reset:token:";
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String LOGIN_UNLOCK_CODE_PREFIX = "login:unlock:";
    private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration RT_TTL_REMEMBER = Duration.ofDays(14);
    private static final Duration RT_TTL_SESSION = Duration.ofHours(3);
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 시간이 지나도 저절로 풀리지 앟음 - 이메일 인증(requestLoginUnlock/confirmLoginUnlock)만이
     * 유일한 해제 경로, 이 TTL은 "보안 정책"이 아니라 Redis에 죽은 카운터가 무기한 남지 않도록 하는 순수한
     * 정리 용도, 충분히 길게 잡아 실질적인 후회 수단이 되지 않게 함
     */
    private static final Duration LOGIN_FAIL_TTL = Duration.ofDays(1);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final EmailVerificationService emailVerificationService;
    private final RateLimitGuard rateLimitGuard;
    private final ApplicationEventPublisher eventPublisher;

    public boolean emailExists(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    public void sendSignupVerificationCode(String email) {
        if (emailExists(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        emailVerificationService.issueCode(SIGNUP_CODE_PREFIX, email, "[먹을지도] 회원가입 인증코드", "인증코드: %s (5분 이내 입력해주세요)");
    }

    public String confirmSignupVerificationCode(String email, String code) {
        emailVerificationService.verifyCode(SIGNUP_CODE_PREFIX, email, code);
        return emailVerificationService.issueTokenKeyedByEmail(SIGNUP_TOKEN_PREFIX, email, SIGNUP_TOKEN_TTL);
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (emailExists(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        boolean verified = emailVerificationService.consumeIfEmailTokenMatches(SIGNUP_TOKEN_PREFIX, request.email(), request.emailVerifiedToken());

        if (!verified) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.nickname());
        userRepository.save(user);

        eventPublisher.publishEvent(new AuditLogEvent(
                user.getId(), "CREATE", "USER", user.getId(), "회원가입", Instant.now()
        ));

        return UserResponse.from(user);
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        CustomUserDetails principal = authenticate(request.email(), request.password());

        userService.recordLogAndCancelPendingWithdrawal(principal.getId());

        String accessToken = jwtTokenProvider.generateAccessToken(principal.getId(), principal.getRole());
        Duration rtTtl = request.rememberMe() ? RT_TTL_REMEMBER : RT_TTL_SESSION;
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal.getId(), rtTtl);
        refreshTokenRepository.save(principal.getId(), refreshToken, rtTtl);

        User user = userRepository.findById(principal.getId()).orElseThrow();
        return new LoginResult(accessToken, refreshToken, rtTtl, request.rememberMe(), new LoginResponse.UserSummary(user.getId(), user.getNickname(), user.getRole().name()));
    }

    private CustomUserDetails authenticate(String email, String password) {
        String attemptsKey = LOGIN_FAIL_PREFIX + email;
        rateLimitGuard.checkNotLocked(attemptsKey, MAX_LOGIN_ATTEMPTS, ErrorCode.LOGIN_LOCKED);

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
            rateLimitGuard.reset(attemptsKey);
            return (CustomUserDetails) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            rateLimitGuard.recordFailure(attemptsKey, LOGIN_FAIL_TTL);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    /**
     * 로그인 잠금 해제용 인증코드 발송, 계정 존재 여부와 무관하게 항상 조용히 끝남
     */
    public void requestLoginUnlock(String email) {
        if (emailExists(email)) {
            emailVerificationService.issueCode(
                    LOGIN_UNLOCK_CODE_PREFIX, email, "[먹을지도] 로그인 잠금 해제 인증코드", "인증코드: %s (5분 이내 입력해주세요.)"
            );
        }
    }

    /**
     * 코드가 맞으면 로그인 실패 카운터를 리셋 후 잠금 해제
     */
    public void confirmLoginUnlock(String email, String code) {
        emailVerificationService.verifyCode(LOGIN_UNLOCK_CODE_PREFIX, email, code);
        rateLimitGuard.reset(LOGIN_FAIL_PREFIX + email);
    }

    @Transactional
    public ReissueResult reissue(String refreshTokenCookie) {
        if (refreshTokenCookie == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtTokenProvider.parseRefreshToken(refreshTokenCookie)
                .map(JwtTokenProvider.RefreshTokenClaims::userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        RefreshTokenValue stored = refreshTokenRepository.find(userId)
                .filter(v -> v.token().equals(refreshTokenCookie))
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        Duration ttl = Duration.ofSeconds(stored.ttlSeconds());
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), ttl);
        refreshTokenRepository.save(user.getId(), newRefreshToken, ttl);

        boolean rememberMe = ttl.compareTo(RT_TTL_SESSION) > 0;
        return new ReissueResult(newAccessToken, newRefreshToken, ttl, rememberMe);
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        refreshTokenRepository.delete(userId);
        jwtTokenProvider.parseAccessToken(accessToken)
                .ifPresent(claims -> {
                    tokenBlacklistRepository.blacklist(accessToken, claims.remainingValidity());
                });
    }

    public void sendPasswordResetCode(String email){
        if (emailExists(email)) {
            emailVerificationService.issueCode(
                    RESET_CODE_PREFIX, email, "[먹을지도] 비밀번호 재설정 인증코드", "인증코드: %s (5분 이내 입력새주세요.)"
            );
        }
        /**
         * 가입 여부와 무관하게 항상 204- 존재하지 않으면 조용히 아무 것도 하지 않음(이메일 존재 여부 비노출)
         */
    }

    public String verifyPasswordResetCode(String email, String code) {
        emailVerificationService.verifyCode(RESET_CODE_PREFIX, email, code);
        return emailVerificationService.issueTokenKeyedByToken(RESET_TOKEN_PREFIX, email, RESET_TOKEN_TTL);
    }

    @Transactional
    public void confirmPasswordReset(String resetToken, String newPassword) {
        String email = emailVerificationService.consumeTokenKeyedByToken(RESET_TOKEN_PREFIX, resetToken)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.delete(user.getId());

        eventPublisher.publishEvent(new AuditLogEvent(
                user.getId(), "UPDATE", "USER", user.getId(), "비밀번호 재설정(기존 세션 전량 무효화", Instant.now()
        ));
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            Duration ttl,
            boolean rememberMe,
            LoginResponse.UserSummary user
    ) {
    }

    public record ReissueResult(
            String accessToken,
            String refreshToken,
            Duration ttl,
            boolean rememberMe
    ) {
    }

}
