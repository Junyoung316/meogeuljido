package com.amugeona.meogeuljido.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "user_withdrawal_requests")
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", nullable = false, length = 20)
    private WithdrawalReasonCategory reasonCategory;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    private UserWithdrawalRequest(Long userId, WithdrawalReasonCategory reasonCategory, String reasonDetail) {
        this.userId = userId;
        this.reasonCategory = reasonCategory;
        this.reasonDetail = reasonDetail;
        this.requestedAt = OffsetDateTime.now();
    }

    public static UserWithdrawalRequest create(Long userId, WithdrawalReasonCategory reasonCategory, String reasonDetail) {
        return new UserWithdrawalRequest(userId, reasonCategory, reasonDetail);
    }

    /**
     * 유예기간 중 로그인으로 탈퇴 틔사 철회 시 호출
     */
    public void cancel() {
        this.cancelledAt = OffsetDateTime.now();
    }

    /**
     * 유예기간 만료(또는 휴면)로 탈퇴가 실제 확정 시 호출
     */
    public void finalizeRequest() {
        this.finalizedAt = OffsetDateTime.now();
    }

}
