package com.amugeona.meogeuljido.user.repository;

import com.amugeona.meogeuljido.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * findAllPendingDormantWithdrawal와 findIdsStillPendingDormantWithdrawal이 반드시 같은
     * 조건을 검사해야 하므로 문자열을 한 곳에서만 관리
     * static final String을 "+"로 이어 붙인 결과도 컴파일타임 상수라 @Query 어노테이션 값으로 그대로 쓸 수 있음
     */
    String DORMANT_WITHDRAWAL_CONDITION = "u.withdrawalRequestedAt IS NULL AND u.dormantWarningSentAt IS NOT NULL AND u.dormantWarningSentAt <= :threshold";

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
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
    @Query("SELECT u From User u WHERE " + DORMANT_WITHDRAWAL_CONDITION)
    List<User> findAllPendingDormantWithdrawal(@Param("threshold") OffsetDateTime threshold);

    /**
     * finalizeDormantWithdrawals()가 대상 목록을 로드한 뒤 실제로 삭제를 실행하기 직전,
     * 그 사이 로그인(dormantWarningSentAt 초기화)했거나 자진 탈퇴를 새로 신청
     * (withdrawalRequestedAt 채워짐) 한 유저를 걸러내기 위한 재확인 쿼리 - 원본 목록 조회
     * (findAllPendingDormantWithdrawal)와 동일한 조건 집합은 유지해야함
     */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND " + DORMANT_WITHDRAWAL_CONDITION)
    List<Long> findIdsStillPendingDormantWithdrawal(@Param("ids") List<Long> ids, @Param("threshold") OffsetDateTime threshold);

}
