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

    private User(String email, String paswordHash, String nickname) {
        this.email = email;
        this.passwordHash = paswordHash;
        this.nickname = nickname;
        this.role = Role.USER;
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
     * 로그인 성공 시 auth가 호출, 대기 중인 자진 탈퇴 요청이 있다면 함께 취소
     */
    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
        this.withdrawalRequestedAt = null;
    }

    /**
     * 휴면 자동탈퇴 예정 안내 메일 발송 시각
     */
    public void markDormantWarningSent() {
        this.dormantWarningSentAt = OffsetDateTime.now();
    }

}
