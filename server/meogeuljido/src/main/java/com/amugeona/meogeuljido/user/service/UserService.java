package com.amugeona.meogeuljido.user.service;

import com.amugeona.meogeuljido.common.event.AuditLogEvent;
import com.amugeona.meogeuljido.common.exception.CustomException;
import com.amugeona.meogeuljido.common.exception.ErrorCode;
import com.amugeona.meogeuljido.user.dto.UserProfileResponse;
import com.amugeona.meogeuljido.user.dto.UserProfileResponse.ActivityCounts;
import com.amugeona.meogeuljido.user.dto.UserResponse;
import com.amugeona.meogeuljido.user.dto.UserUpdateRequest;
import com.amugeona.meogeuljido.user.dto.WithdrawRequest;
import com.amugeona.meogeuljido.user.entity.User;
import com.amugeona.meogeuljido.user.entity.UserWithdrawalRequest;
import com.amugeona.meogeuljido.user.entity.WithdrawalReasonCategory;
import com.amugeona.meogeuljido.user.repository.UserRepository;
import com.amugeona.meogeuljido.user.repository.UserWithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final int WITHDRAWAL_GRACE_DAYS = 7;

    private final UserRepository userRepository;
    private final UserWithdrawalRequestRepository userWithdrawalRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserProfileResponse getMyProfile(Long userId) {
        User user = getActiveUserOrThrow(userId);
        return UserProfileResponse.of(user, resolveActivityCounts(userId));
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    @Transactional
    public UserResponse updateNickname(Long userId, UserUpdateRequest request) {
        User user = getActiveUserOrThrow(userId);
        String previousNickname = user.getNickname();
        if (!request.nickname().equals(previousNickname) && userRepository.existsByNickname(request.nickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(request.nickname());

        eventPublisher.publishEvent(new AuditLogEvent(
                userId, "UPDATE", "USER", userId, "닉네임 변경: %s -> %s".formatted(previousNickname, request.nickname()), Instant.now()
        ));

        return UserResponse.from(user);
    }

    /**
     * DELETE /api/users/me
     * 즉시 탈퇴가 아니라 유예기간(7일) 시작
     */
    @Transactional
    public void requestWithdrawal(Long userId, WithdrawRequest request) {
        User user = getActiveUserOrThrow(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (request.reasonCategory() == WithdrawalReasonCategory.OTHER && (request.reasonDetail() == null || request.reasonDetail().isBlank())) {
            throw new CustomException(ErrorCode.WITHDRAWAL_REASON_DETAIL_REQUIRED);
        }
        user.requestWithdrawal();
        userWithdrawalRequestRepository.save(
                UserWithdrawalRequest.create(userId, request.reasonCategory(), request.reasonDetail())
        );

        eventPublisher.publishEvent(new AuditLogEvent(
           userId, "UPDATE", "USER", userId, "탈퇴요청 접수 (%d일 후 확정 예정 · 사유: %s %s".formatted(WITHDRAWAL_GRACE_DAYS, request.reasonCategory(), describeDetail(request.reasonDetail())), Instant.now()
        ));

        // TODO(auth 도메인 구현 후 연동): 유예기간 중에도 이후 요청부터는 로그인/토큰 재발급을
        // 막아야 할지(즉시 로그아웃 처리) 여부는 auth 구현 시 별도 결정 — 지금은 계정이 살아있는
        // 상태이므로 기존 Access/Refresh Token은 만료 전까지 그대로 유효하다.

        // TODO(auth 도메인 구현 후 연동): auth.redis.RefreshTokenRepository에서 이 userId의
        // Refresh Token을 삭제하고, 현재 요청의 Access Token을 auth.redis.TokenBlacklistRepository에
        // 등록해야 한다(api-spec.md §3 DELETE /api/users/me 동작 설명).

    }

    /**
     * auth 도메인 구현 후 로그인 성공 시 {@code user.recordLogin()} 대신 이 메서드를 호출
     * 유예기간 중이던 탈퇴 요청을 취소 및 이력 테이블에 남아있는 "진행 중" 행도 함께 취소 처리
     */
    @Transactional
    public void recordLogAndCancelPendingWithdrawal(Long userId) {
        User user = getActiveUserOrThrow(userId);
        boolean hadPendingWithdrawal = user.getWithdrawalRequestedAt() != null;
        user.recordLogin();

        if (hadPendingWithdrawal) {
            userWithdrawalRequestRepository
                    .findByUserIdAndCancelledAtIsNullAndFinalizedAtIsNull(userId)
                    .ifPresent(UserWithdrawalRequest::cancel);

            eventPublisher.publishEvent(new AuditLogEvent(
                    userId, "UPDATE", "USER", userId, "유예기간 중 로그인으로 탈퇴 요청 취소", Instant.now()
            ));
        }
    }

    private User getActiveUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private ActivityCounts resolveActivityCounts(Long userId) {
        // TODO(restaurant/review/bookmark 도메인 구현 후 교체): 지금은 0 고정.
        return new ActivityCounts(0, 0, 0);
    }

    private String describeDetail(String reasonDetail) {
        return (reasonDetail == null || reasonDetail.isBlank()) ? "" : "(%s)".formatted(reasonDetail);
    }

}
