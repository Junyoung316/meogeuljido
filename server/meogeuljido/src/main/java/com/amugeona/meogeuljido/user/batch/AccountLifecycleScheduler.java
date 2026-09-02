package com.amugeona.meogeuljido.user.batch;

import com.amugeona.meogeuljido.common.event.AuditLogEvent;
import com.amugeona.meogeuljido.common.event.WithdrawalCompletedEvent;
import com.amugeona.meogeuljido.user.entity.User;
import com.amugeona.meogeuljido.user.entity.UserWithdrawalRequest;
import com.amugeona.meogeuljido.user.repository.UserRepository;
import com.amugeona.meogeuljido.user.repository.UserWithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLifecycleScheduler {

    private static final int WITHDRAWAL_GRACE_DAYS = 7;
    private static final int DORMANCY_YEARS = 1;
    private static final int DORMANCY_WARNING_LEAD_DAYS = 7;

    private final UserRepository userRepository;
    private final UserWithdrawalRequestRepository userWithdrawalRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DormantWarningSender dormantWarningSender;

    /**
     * 자진 탈퇴 유예기간(7일) 만료자를 실제로 탈퇴 처리
     * (매일 09:00)
     */
    @Transactional
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void finalizeRequestedWithdrawals() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(WITHDRAWAL_GRACE_DAYS);
        List<User> targets = userRepository.findAllPendingWithdrawalFinalization(threshold);

        if (targets.isEmpty()) {
            log.info("[AccountLifecycle] 자진 탈퇴 확정 처리: 0건");
            return;
        }

        List<Long> userIds = targets.stream().map(User::getId).toList();
        Map<Long, UserWithdrawalRequest> requests = userWithdrawalRequestRepository
                .findByUserIdInAndCancelledAtIsNullAndFinalizedAtIsNull(userIds).stream()
                .collect(Collectors.toMap(UserWithdrawalRequest::getUserId, r -> r));

        for (User user : targets) {
            /**
             * withdraw() 이후에도 이 User 인스턴스는 같은 트랜잭션 안에서 이미 로드돼 있으므로
             * email/nickname을 계속 읽을 수 있다 - delete_at이 찍혀도 필드 값 자체는 그대로다.
             */
            String email = user.getEmail();
            String nickname = user.getNickname();
            user.withdraw();
            UserWithdrawalRequest pending = requests.get(user.getId());
            if (pending != null) {
                pending.finalizeRequest();
            }
            eventPublisher.publishEvent(new AuditLogEvent(
                    user.getId(), "DELETE", "USER", user.getId(), "탈퇴 유예기간 만료로 확정 처리", Instant.now()
            ));
            eventPublisher.publishEvent(new WithdrawalCompletedEvent(
                    user.getId(), email, nickname, WithdrawalCompletedEvent.Reason.REQUESTED
            ));
        }
        log.info("[AccountLifecycle] 자진 탈퇴 확정 처리: {}건", targets.size());
    }

    /**
     * 마지막 로그인 후 (1년 - 7일, 365 - 7 = 358일) 경과자에게 자동 탈퇴 예정 메일 발송
     * (매일 10:00)
     */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void warnDormantUsers() {
        OffsetDateTime threshold = OffsetDateTime.now()
                .minusYears(DORMANCY_YEARS)
                .plusDays(DORMANCY_WARNING_LEAD_DAYS);
        List<User> targets = userRepository.findDormantUsersPendingWarning(threshold);
        int sentCount = 0;
        for (User user : targets) {
            try {
                dormantWarningSender.sendAndMark(user.getId(), user.getEmail(), user.getNickname(), DORMANCY_WARNING_LEAD_DAYS);
                sentCount++;
            } catch(Exception e) {
                /**
                 * 한 유저의 메일 발송 실패가 다른 유저의 markDormantWarningSent()까지 롤백시키지
                 * 않도록 여기서 격리 실패한 유저는 dormantWarningSentAt이 null로 남아
                 * 다음 배치(익일 10:00)에 자동으로 재시도
                 */
                log.error("[AccountLifecycle] 휴면 경고 메일 발송 실패: userId={}", user.getId(), e);
            }
        }
        log.info("[AccountLifecycle] 휴면 탈퇴 예정 안내 메일 발송: {}/{}건", sentCount, targets.size());
    }

    /**
     * 마지막 로그인 후 1년 경과자 자동 탈퇴 처리
     * (매일 09:30)
     */
    @Transactional
    @Scheduled(cron = "0 30 9 * * *", zone = "Asia/Seoul")
    public void finalizeDormantWithdrawals() {
        /**
         * 경고 발송 시각 기준 + 유예기간(7일) 경과자만 대상
         * lastLoginAt 기준이 아니므로 재시도로 발송이 며칠 늦어져도
         * 그 유저의 유예기간은 항상 7일을 보장
         */
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(DORMANCY_WARNING_LEAD_DAYS);
        List<User> targets = userRepository.findAllPendingDormantWithdrawal(threshold);
        for (User user : targets) {
            String email = user.getEmail();
            String nickname = user.getNickname();
            user.withdraw();
            eventPublisher.publishEvent(new AuditLogEvent(
                    user.getId(), "DELETE", "USER", user.getId(), "휴면(%d년 이상 미로그인)으로 자동 탈퇴 처리".formatted(DORMANCY_YEARS), Instant.now()
            ));
            eventPublisher.publishEvent(new WithdrawalCompletedEvent(
                    user.getId(), email, nickname, WithdrawalCompletedEvent.Reason.DORMANT
            ));
        }
        log.info("[AccountLifecycle] 휴면 자동 탈퇴 처리: {}건", targets.size());
    }


}
