package com.amugeona.meogeuljido.user.repository;

import com.amugeona.meogeuljido.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByNicknameIgnoreCase(String nickname);

    /**
     * 배치용 쿼리
     */
    @Query("SELECT u FROM User u WHERE u.withdrawalRequestedAt IS NOT NULL " + "AND u.withdrawalRequestedAt <= :threshold")
    List<User> findAllPendingWithdrawalFinalization(@Param("threshold") OffsetDateTime threshold);

    @Query("SELECT u FROM User u WHERE u.withdrawalRequestedAt IS NULL " + "AND u.dormantWarningSentAt IS NULL " + "AND u.lastLoginAt IS NOT NULL AND u.lastLoginAt <= :threshold")
    List<User> findDormantUsersPendingWarning(@Param("threshold") OffsetDateTime threshold);

    /**
     * 삭제 기준은 lastLoginAt이 아니라 "경고 메일이 실제로 발송된 시각"이어야 한다.
     * dormantWarningSentAt이 채워진 유저만 대상(recordLogin()이 로그인 시 이를 초기화)
     * 재로그인한 유저는 자동으로 이 목록에서 제외
     */
    @Query("SELECT u From User u WHERE u.withdrawalRequestedAt IS NULL AND u.dormantWarningSentAt IS NOT NULL AND u.dormantWarningSentAt <= :threshold")
    List<User> findAllPendingDormantWithdrawal(@Param("threshold") OffsetDateTime threshold);

}
