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
    boolean existsByNickname(String nickname);

    /**
     * 배치용 쿼리
     */
    @Query("SELECT u FROM User u WHERE u.withdrawalRequestedAt IS NOT NULL " + "AND u.withdrawalRequestedAt <= :threshold")
    List<User> findAllPendingWithdrawalFinalization(@Param("threshold") OffsetDateTime threshold);

    @Query("SELECT u FROM User u WHERE u.withdrawalRequestedAt IS NULL " + "AND u.dormantWarningSentAt IS NULL " + "AND u.lastLoginAt IS NOT NULL AND u.lastLoginAt <= :threshold")
    List<User> findDormantUsersPendingWarning(@Param("threshold") OffsetDateTime threshold);

    @Query("SELECT u FROM User u WHERE u.withdrawalRequestedAt IS NULL " + "AND u.lastLoginAt IS NOT NULL AND u.lastLoginAt <= :threshold")
    List<User> findAllPendingDormantWithdrawal(@Param("threshold") OffsetDateTime threshold);

}
