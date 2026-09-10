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
import java.util.Optional;

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

        boolean verified = emailVerificationService.hasValidEmailToken(SIGNUP_TOKEN_PREFIX, request.email(), request.emailVerifiedToken());

        if (!verified) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.nickname());
        userRepository.save(user);

        emailVerificationService.consumeEmailToken(SIGNUP_TOKEN_PREFIX, request.email());

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
        refreshTokenRepository.save(principal.getId(), refreshToken, rtTtl, request.rememberMe());

        return new LoginResult(accessToken, refreshToken, rtTtl, request.rememberMe(), new LoginResponse.UserSummary(principal.getId(), principal.getNickname(), principal.getRole()));
    }

    private String loginFailKey(String email) {
        return LOGIN_FAIL_PREFIX + email.toLowerCase();
    }

    private CustomUserDetails authenticate(String email, String password) {
        String attemptsKey = loginFailKey(email);
        rateLimitGuard.checkNotLocked(attemptsKey, MAX_LOGIN_ATTEMPTS, ErrorCode.LOGIN_LOCKED);

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
            rateLimitGuard.reset(attemptsKey);
            return (CustomUserDetails) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            long failureCount = rateLimitGuard.recordFailurePermanently(attemptsKey);

            if (failureCount >= MAX_LOGIN_ATTEMPTS) {
                throw new CustomException(ErrorCode.LOGIN_LOCKED);
            }

            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    /**
     * 로그인 잠금 해제용 인증코드 발송, 계정 존재 여부와 무관하게 항상 조용히 끝남
     */
    public void requestLoginUnlock(String email) {
        emailVerificationService.issueCodeIfExists(
                LOGIN_UNLOCK_CODE_PREFIX, email, "[먹을지도] 로그인 잠금 해제 인증코드", "인증코드: %s (5분 이내 입력해주세요.)", emailExists(email)
        );
    }

    /**
     * 코드가 맞으면 로그인 실패 카운터를 리셋 후 잠금 해제
     */
    public void confirmLoginUnlock(String email, String code) {
        emailVerificationService.verifyCode(LOGIN_UNLOCK_CODE_PREFIX, email, code);
        rateLimitGuard.reset(loginFailKey(email));
    }

    @Transactional
    public ReissueResult reissue(String refreshTokenCookie) {
        if (refreshTokenCookie == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtTokenProvider.parseRefreshToken(refreshTokenCookie)
                .map(JwtTokenProvider.RefreshTokenClaims::userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        Optional<RefreshTokenValue> found = refreshTokenRepository.findAndInvalidate(userId);
        RefreshTokenValue stored = found.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (!stored.token().equals(refreshTokenCookie)) {
            /**
             * GETDEL이 이미 지워버린 값이 요청 토큰과 다름 = 다른 요청이 먼저 회전시킨 최신 토큰이었다는 뜻
             * 그 값을 삭제된 채로 두면 방급 회전에 성공한 진짜 세션까지 로그아웃되므로 즉시 복원
             */
            refreshTokenRepository.save(userId, stored.token(), Duration.ofSeconds(stored.ttlSeconds()), stored.rememberMe());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        Duration ttl = Duration.ofSeconds(stored.ttlSeconds());
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), ttl);
        refreshTokenRepository.save(user.getId(), newRefreshToken, ttl, stored.rememberMe());

        return new ReissueResult(newAccessToken, newRefreshToken, ttl, stored.rememberMe());
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
        emailVerificationService.issueCodeIfExists(
                RESET_CODE_PREFIX, email, "[먹을지도] 비밀번호 재설정 인증코드", "인증코드: %s (5분 이내 입력해주세요.)", emailExists(email)
        );
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
