package com.amugeona.meogeuljido.auth.controller;

import com.amugeona.meogeuljido.auth.dto.*;
import com.amugeona.meogeuljido.auth.service.AuthService;
import com.amugeona.meogeuljido.common.exception.CustomException;
import com.amugeona.meogeuljido.common.exception.ErrorCode;
import com.amugeona.meogeuljido.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.amugeona.meogeuljido.common.security.AuthenticatedUser;
import com.amugeona.meogeuljido.common.security.BearerTokenExtractor;
import com.amugeona.meogeuljido.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 재발급, 로그아웃, 비밀번호 재설정, 로그인 잠금 해제")
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;

    @Operation(summary = "이메일 중복확인",
            description = "회원가입 화면에서 실시간으로 사용 가능 여부를 확인한다. 인증 불필요(Public).")
    @GetMapping("/check-email")
    public ResponseEntity<EmailAvailabilityResponse> checkEmail(@RequestParam @NotBlank @Email String email, HttpServletRequest request) {
        return ResponseEntity.ok(new EmailAvailabilityResponse(authService.isEmailAvailable(email, request.getRemoteAddr())));
    }

    @Operation(summary = "회원가입 인증코드 발송",
            description = "6자리 인증코드를 생성해 Redis에 5분 TTL로 저장하고 이메일로 발송한다. 10초 쿨다운 내 재요청 시 거부.")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS — 이미 가입된 이메일",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS — 10초 쿨다운 내 재요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup/verify/request")
    public ResponseEntity<Void> requestSignupVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        authService.sendSignupVerificationCode(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원가입 인증코드 확인",
            description = "코드가 일치하면 회원가입 시 이메일 소유권 증빙으로 쓸 단기 emailVerifiedToken(TTL 30분)을 발급한다. 5회 연속 오답이면 코드 재발급 전까지 잠김(429).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 코드 형식 오류(6자리 숫자 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "CODE_MISMATCH — 코드 불일치/만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS — 5회 연속 오답으로 잠김",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup/verify/confirm")
    public ResponseEntity<EmailVerifiedTokenResponse>
 confirmSignupVerification(@Valid @RequestBody VerifyCodeRequest request) {
        String token = authService.confirmSignupVerificationCode(request.email(), request.code());
        return ResponseEntity.ok(new EmailVerifiedTokenResponse(token));
    }

    @Operation(summary = "회원가입",
            description = "emailVerifiedToken이 요청 본문의 email과 매칭되고 Redis에 유효한 상태로 남아있는지 검증한 뒤 계정을 생성한다. 성공 시 토큰은 즉시 폐기(1회용). 가입 후 별도 로그인이 필요하다(자동 로그인 아님).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 이메일 형식/비밀번호 8자 미만 등",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "EMAIL_NOT_VERIFIED — emailVerifiedToken 누락/무효/이메일 불일치/만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS — 이미 가입된 이메일",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @Operation(summary = "로그인",
            description = "성공 시 accessToken은 응답 바디로, refreshToken은 HttpOnly 쿠키로 발급한다(§3.2). "
                    + "같은 이메일로 5회 연속 실패하면 잠기며, 시간 경과로는 자동 해제되지 않고 login/unlock/* 두 엔드포인트로 이메일 인증을 거쳐야만 풀린다(§12.1).")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS — 이메일/비밀번호 불일치",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "LOGIN_LOCKED — 5회 연속 실패로 잠김. login/unlock/* 로만 해제 가능",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthService.LoginResult result = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(
                        result.refreshToken(), result.rememberMe(), result.ttl()).toString()
                )
                .body(new LoginResponse(result.accessToken(), result.user()));
    }

    @Operation(summary = "로그인 잠금 해제 인증코드 발송",
            description = "가입 여부와 무관하게 항상 204로 응답한다(이메일 존재 여부 비노출, password-reset/request와 동일 정책). 실제로 잠긴 상태인지 여부와도 무관하게 가입된 이메일이면 항상 발송한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS — 10초 쿨다운 내 재요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login/unlock/request")
    public ResponseEntity<Void> requestLoginUnlock(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        authService.requestLoginUnlock(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "로그인 잠금 해제",
            description = "코드가 맞으면 로그인 실패 카운터를 초기화해 잠금을 해제한다. 확인 성공 시 인증코드는 즉시 폐기.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 코드 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "CODE_MISMATCH — 코드 불일치/만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login/unlock/confirm")
    public ResponseEntity<Void> confirmLoginUnlock(@Valid @RequestBody VerifyCodeRequest request) {
        authService.confirmLoginUnlock(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Access Token 재발급",
            description = "요청 바디 없음 — 브라우저가 refreshToken 쿠키를 자동 첨부한다(프론트는 credentials: 'include' 필요). "
                    + "성공 시 RT도 함께 회전(슬라이딩 갱신, §3.5) — 로그인 때와 동일한 Max-Age 정책으로 새 쿠키를 내려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED — RT 쿠키 없음/만료/블랙리스트/불일치. 프론트는 이 경우 로그인 화면으로 리다이렉트",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(
        @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        AuthService.ReissueResult result = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshTokenCookie(
                                result.refreshToken(), result.rememberMe(), result.ttl()).toString()
                )
                .body(new ReissueResponse(result.accessToken()));
    }

    @Operation(summary = "로그아웃",
            description = "Redis에서 해당 유저의 Refresh Token을 삭제하고, 현재 Access Token을 만료 시각까지 블랙리스트에 등록한다. refreshToken 쿠키도 함께 삭제(Max-Age=0).")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request
    ) {
        authService.logout(authenticatedUser.getId(), extractBearerToken(request));
        ResponseCookie expired = expiredRefreshTokenCookie();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }

    @Operation(summary = "비밀번호 재설정 인증코드 발송",
            description = "가입 여부와 무관하게 항상 204로 응답한다(이메일 존재 여부 비노출). 실제 발송은 가입된 이메일에 한해 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS — 10초 쿨다운 내 재요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        authService.sendPasswordResetCode(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 재설정 인증코드 확인",
            description = "코드가 맞으면 비밀번호 변경에 사용할 단기 resetToken(TTL 5분)을 발급한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 코드 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "CODE_MISMATCH — 코드 불일치/만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/verify")
    public ResponseEntity<ResetTokenResponse> verifyPasswordReset(@Valid @RequestBody VerifyCodeRequest request) {
        return ResponseEntity.ok(
                new ResetTokenResponse(authService.verifyPasswordResetCode(request.email(), request.code()))
        );
    }

    @Operation(summary = "비밀번호 재설정 확정",
            description = "resetToken + 새 비밀번호로 변경한다. 성공 시 기존 Refresh Token을 전량 무효화(강제 로그아웃)한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 새 비밀번호 8자 미만 등",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED — resetToken 만료/무효",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.resetToken(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie.ResponseCookieBuilder refreshTokenCookieBuilder(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Strict").path("/api/auth");
    }

    private ResponseCookie buildRefreshTokenCookie(String token, boolean rememberMe, Duration ttl) {

        var builder = refreshTokenCookieBuilder(token);

        if (rememberMe) {
            builder.maxAge(ttl);
        }

        return builder.build();
    }

    private String extractBearerToken(HttpServletRequest request) {
        return BearerTokenExtractor.extract(request)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
    }

    private ResponseCookie expiredRefreshTokenCookie() {
        return refreshTokenCookieBuilder("").maxAge(Duration.ZERO).build();
    }

}
