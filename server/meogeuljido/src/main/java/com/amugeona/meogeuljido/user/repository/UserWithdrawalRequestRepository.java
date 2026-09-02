package com.amugeona.meogeuljido.user.repository;

import com.amugeona.meogeuljido.user.entity.UserWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserWithdrawalRequestRepository extends JpaRepository<UserWithdrawalRequest, Long> {
    Optional<UserWithdrawalRequest> findByUserIdAndCancelledAtIsNullAndFinalizedAtIsNull(Long userId);
}
