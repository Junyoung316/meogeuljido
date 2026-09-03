package com.amugeona.meogeuljido.user.controller;

import com.amugeona.meogeuljido.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.amugeona.meogeuljido.common.security.AuthenticatedUser;
import com.amugeona.meogeuljido.user.dto.*;
import com.amugeona.meogeuljido.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "내 프로필 조회/수정, 닉네임 중복확인, 탈퇴 요청")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "닉네임 중복확인",
            description = "회원가입·닉네임 수정 화면에서 실시간으로 사용 가능 여부를 확인한다. 인증 불필요(Public).")
    @GetMapping("/check-nickname")
    public ResponseEntity<NicknameAvailabilityResponse> checkNickname(
            @Parameter(description = "확인할 닉네임 (2~12자)", example = "혼밥러버")
            @RequestParam
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
            String nickname
    ) {
        return ResponseEntity.ok(new NicknameAvailabilityResponse(userService.isNicknameAvailable(nickname)));
    }

    @Operation(summary = "내 프로필 조회", description = "활동 요약(리뷰/즐겨찾기/등록 식당 수) 포함.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
            ) {
        return ResponseEntity.ok(userService.getMyProfile(authenticatedUser.getId()));
    }

    @Operation(summary = "닉네임 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "닉네임이 2~12자를 벗어남",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_NICKNAME — 이미 사용 중인 닉네임",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateNickname(authenticatedUser.getId(), request));
    }

    @Operation(summary = "탈퇴 요청",
            description = "즉시 탈퇴가 아니라 7일 유예기간을 시작한다. 유예기간 중 로그인하면 요청이 자동 취소된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "WITHDRAWAL_REASON_DETAIL_REQUIRED — 사유가 '기타'인데 상세 사유 누락",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS — 비밀번호 불일치",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 사용자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody WithdrawRequest request
    ) {
        userService.requestWithdrawal(authenticatedUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

}
