package com.amugeona.meogeuljido.auth.service;

import com.amugeona.meogeuljido.auth.EmailNormalizer;
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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    private static final String CHECK_EMAIL_PREFIX = "check-email:";
    private static final int MAX_CHECK_EMAIL_PER_WINDOW = 20;
    private static final Duration CHECK_EMAIL_WINDOW = Duration.ofMinutes(1);
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
    private static final String EMAIL_CODE_REQUEST_PREFIX = "email-code-request:";
    private static final int MAX_EMAIL_CODE_REQUEST_PER_WINDOW = 10;
    private static final Duration EMAIL_CODE_REQUEST_WINDOW = Duration.ofMinutes(1);

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

    public boolean isEmailAvailable(String email, String clientIp) {
        rateLimitGuard.checkAndCountAttempt(CHECK_EMAIL_PREFIX + clientIp, MAX_CHECK_EMAIL_PER_WINDOW, CHECK_EMAIL_WINDOW, ErrorCode.TOO_MANY_REQUESTS);
        return !emailExists(email);
    }

    public void sendSignupVerificationCode(String email, String clientIp) {

        rateLimitGuard.checkAndCountAttempt(EMAIL_CODE_REQUEST_PREFIX + clientIp, MAX_EMAIL_CODE_REQUEST_PER_WINDOW, EMAIL_CODE_REQUEST_WINDOW, ErrorCode.TOO_MANY_REQUESTS);

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

        if (userRepository.existsByNicknameIgnoreCase(request.nickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        boolean verified = emailVerificationService.hasValidEmailToken(SIGNUP_TOKEN_PREFIX, request.email(), request.emailVerifiedToken());

        if (!verified) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.create(request.email(), encodePassword(request.password()), request.nickname());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            String constraintName = extractConstraintName(ex);

            if (constraintName != null && constraintName.contains("email")) {
                throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

            if (constraintName != null && constraintName.contains("nickname")) {
                throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
            }

            throw ex;
        }

        emailVerificationService.consumeEmailToken(SIGNUP_TOKEN_PREFIX, request.email());

        eventPublisher.publishEvent(new AuditLogEvent(
                user.getId(), "CREATE", "USER", user.getId(), "회원가입", Instant.now()
        ));

        return UserResponse.from(user);
    }

    private String extractConstraintName(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cve) {
            return cve.getConstraintName();
        }
        return null;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        CustomUserDetails principal = authenticate(request.email(), request.password());

        try {
            userService.recordLogAndCancelPendingWithdrawal(principal.getId());
        } catch (CustomException ex) {
            if (ex.getErrorCode() != ErrorCode.NOT_FOUND) {
                throw ex;
            }

            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(principal.getId(), principal.getRole());
        Duration rtTtl = request.rememberMe() ? RT_TTL_REMEMBER : RT_TTL_SESSION;
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal.getId(), rtTtl);
        refreshTokenRepository.save(principal.getId(), refreshToken, rtTtl, request.rememberMe());

        return new LoginResult(accessToken, refreshToken, rtTtl, request.rememberMe(), new LoginResponse.UserSummary(principal.getId(), principal.getNickname(), principal.getRole()));
    }

    private String loginFailKey(String email) {
        return LOGIN_FAIL_PREFIX + EmailNormalizer.normalize(email);
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
        } catch (BadCredentialsException e) {
            if (emailExists(email)) {
                long failureCount = rateLimitGuard.recordFailurePermanently(attemptsKey);

                if (failureCount >= MAX_LOGIN_ATTEMPTS) {
                    throw new CustomException(ErrorCode.LOGIN_LOCKED);
                }
            }

            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    /**
     * 로그인 잠금 해제용 인증코드 발송, 계정 존재 여부와 무관하게 항상 조용히 끝남
     */
    public void requestLoginUnlock(String email, String clientIp) {

        rateLimitGuard.checkAndCountAttempt(EMAIL_CODE_REQUEST_PREFIX + clientIp, MAX_EMAIL_CODE_REQUEST_PER_WINDOW, EMAIL_CODE_REQUEST_WINDOW, ErrorCode.TOO_MANY_REQUESTS);

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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        Optional<RefreshTokenValue> found = refreshTokenRepository.findAndInvalidate(userId);
        RefreshTokenValue stored = found.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (!stored.token().equals(refreshTokenCookie)) {
            /**
             * stored는 findAndInvalidate() 한 번으로 원자적으로 얻은 값이라, 이 값의 expiresAt은
             * 그 시점 기준 실제 만료 시각 그대로임 - 별도 조회 없이 바로 남은 시간을 계산할 수 있고,
             * 그 사이 다른 요청이 끼어들 틈 자체가 없음(다른 세대의 토큰을 잘못 복원할 여지가 없음)
             */
            Duration restoreTtl = Duration.between(Instant.now(), stored.expiresAt());

            if (restoreTtl.isPositive()) {
                refreshTokenRepository.save(userId, stored.token(), restoreTtl, stored.rememberMe());
            }

            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Duration ttl = stored.rememberMe() ? RT_TTL_REMEMBER : RT_TTL_SESSION;
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

    public void sendPasswordResetCode(String email, String clientIp){

        rateLimitGuard.checkAndCountAttempt(EMAIL_CODE_REQUEST_PREFIX + clientIp, MAX_EMAIL_CODE_REQUEST_PER_WINDOW, EMAIL_CODE_REQUEST_WINDOW, ErrorCode.TOO_MANY_REQUESTS);

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

        user.changePassword(encodePassword(newPassword));

        userRepository.flush();

        refreshTokenRepository.delete(user.getId());

        tokenBlacklistRepository.blacklistAllIssuedBefore(user.getId(), Instant.now(), jwtTokenProvider.accessTokenValidity());
        rateLimitGuard.reset(loginFailKey(email));

        eventPublisher.publishEvent(new AuditLogEvent(
                user.getId(), "UPDATE", "USER", user.getId(), "비밀번호 재설정(기존 세션 전량 무효화)", Instant.now()
        ));
    }

    private String encodePassword(String rawPassword) {
        try {
            return passwordEncoder.encode(rawPassword);
        } catch(IllegalArgumentException e) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "비밀번호에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
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
