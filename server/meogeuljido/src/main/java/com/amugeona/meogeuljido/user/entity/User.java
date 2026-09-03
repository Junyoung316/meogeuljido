package com.amugeona.meogeuljido.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "withdrawal_requested_at")
    private OffsetDateTime withdrawalRequestedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "dormant_warning_sent_at")
    private OffsetDateTime dormantWarningSentAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.USER;
        /**
         * 가입 직후에는 "방급 활동했다"는 의미로 lastLoginAt을 가입 시각으로 초기화
         * 이렇게 해야 가입 후 한 번도 로그인하지 않은 계정도 1년 뒤 정상적으로 휴면
         * 경고, 자동 탈퇴 대상이 됨 - 초기화하지 않으면 findDormantUsersPendingWarning의 "lastLoginAt IS NOT NULL" 조건에 걸려 영구히 정책 대상에서 빠짐
         */
        this.lastLoginAt = OffsetDateTime.now();
    }

    public static User create(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, nickname);
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 자진 탈퇴 요청 - 즉시 삭제하지 않고 유예기간 시작 시각만 기록
     */
    public void requestWithdrawal() {
        this.withdrawalRequestedAt = OffsetDateTime.now();
    }

    /**
     * 유예기간/휴면 판정 배치가 실제로 계정을 탈퇴처리할 때 호출
     */
    public void withdraw() {
        this.deletedAt = OffsetDateTime.now();
    }

    /**
     * 이 메서드를 직접 호출하지 말것. auth 도메인의 로그인 성공 처리는 반드
     * {@link com.amugeona.meogeuljido.user.service.UserService#recordLogAndCancelPendingWithdrawal}을
     * 통해서만 호출 - 이 메서드는 User 엔티티 상태만 초기화
     * user_withdrawal_requests 테이블의 "진행 중" 행을 취소 처리하지 않음
     * 직접 호출하면 진행 중 상태로 남아 uq_user_withdrawal_requests_pending 제약으로 인해
     * 해당 유저의 탈퇴 요청 자체가 막힘
     */
    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
        this.withdrawalRequestedAt = null;
        this.dormantWarningSentAt = null;
    }

    /**
     * 휴면 자동탈퇴 예정 안내 메일 발송 시각
     */
    public void markDormantWarningSent() {
        this.dormantWarningSentAt = OffsetDateTime.now();
    }

}
