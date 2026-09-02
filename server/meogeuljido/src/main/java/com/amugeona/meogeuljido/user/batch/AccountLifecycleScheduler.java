package com.amugeona.meogeuljido.user.batch;

import com.amugeona.meogeuljido.common.event.AuditLogEvent;
import com.amugeona.meogeuljido.common.mail.MailService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLifecycleScheduler {

    private static final int WITHDRAWAL_GRACE_DAYS = 7;
    private static final int DORMANCY_YEARS = 1;
    private static final int DORMANCY_WARNING_LEAD_DAYS = 7;

    private final UserRepository userRepository;
    private final UserWithdrawalRequestRepository userWithdrawalRequestRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 자진 탈퇴 유예기간(7일) 만료자를 실제로 탈퇴 처리
     * 매일 03:00
     */
    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void finalizeRequestedWithdrawals() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(WITHDRAWAL_GRACE_DAYS);
        List<User> targets = userRepository.findAllPendingWithdrawalFinalization(threshold);
        for (User user : targets) {
            user.withdraw();
            userWithdrawalRequestRepository
                    .findByUserIdAndCancelledAtIsNullAndFinalizedAtIsNull(user.getId())
                    .ifPresent(UserWithdrawalRequest::finalizeRequest);
            eventPublisher.publishEvent(new AuditLogEvent(
                    user.getId(), "DELETE", "USER", user.getId(), "탈퇴 유예기간 만료로 확정 처리", Instant.now()
            ));
        }
        log.info("[AccountLifecycle] 자진 탈퇴 확정 처리: {}건", targets.size());
    }

    /**
     * 마지막 로그인 후 (1년 - 7일, 365 - 7 = 358일) 경과자에게 자동 탈퇴 예정 메일 발송
     * 매일 03:30
     */
    @Transactional
    @Scheduled(cron = "0 30 3 * * *")
    public void warnDormantUsers() {
        OffsetDateTime threshold = OffsetDateTime.now()
                .minusYears(DORMANCY_YEARS)
                .plusDays(DORMANCY_WARNING_LEAD_DAYS);
        List<User> targets = userRepository.findDormantUsersPendingWarning(threshold);
        for (User user : targets) {
            mailService.send(user.getEmail(),
                    "[먹을지도] 장기 미접속으로 곧 계정이 탈퇴 처리됩니다.",
                    ("%s님, 1년 이상 로그인하지 않아 %d일 후 계정이 자동으로 탈퇴 처리될 예정입니다. " +
                            "계속 이용하시려면 로그인해주세요.").formatted(user.getNickname(), DORMANCY_WARNING_LEAD_DAYS));
            user.markDormantWarningSent();
        }
        log.info("[AccountLifecycle] 휴면 탈퇴 예정 안내 메일 발송: {}건", targets.size());
    }

    /**
     * 마지막 로그인 후 1년 경과자 자동 탈퇴 처리
     * 매일 04:00
     */
    @Transactional
    @Scheduled(cron = "0 0 4 * * *")
    public void finalizeDormantWithdrawals() {
        OffsetDateTime threshold = OffsetDateTime.now().minusYears(DORMANCY_YEARS);
        List<User> targets = userRepository.findAllPendingDormantWithdrawal(threshold);
        for (User user : targets) {
            user.withdraw();
            eventPublisher.publishEvent(new AuditLogEvent(
                    user.getId(), "DELETE", "USER", user.getId(), "휴면(%d년 이상 미로그인)으로 자동 탈퇴 처리".formatted(DORMANCY_YEARS), Instant.now()
            ));
        }
        log.info("[AccountLifecycle] 휴면 자동 탈퇴 처리: {}건", targets.size());
    }


}
