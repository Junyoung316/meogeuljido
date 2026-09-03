package com.amugeona.meogeuljido.user.batch;

import com.amugeona.meogeuljido.common.event.WithdrawalCompletedEvent;
import com.amugeona.meogeuljido.common.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalCompletedMailListener {

    private final MailService mailService;

    /**
     * AFTER_COMMIT: 탈퇴 확정 트랜잭션이 실제로 커밋된 뒤에만 실행, 트랜잭션이 롤백되면 이 리스너
     * 자체가 호출되지 않으므로, "커밋 안 된 삭제를 완료됐다고 안내"하는 상황이 애초에 생길 수 없음
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawalCompleted(WithdrawalCompletedEvent event) {
        try {
            String body = switch (event.reason()) {
                case DORMANT -> "%s님, 1년 이상 로그인하지 않아 계정이 자동으로 탈퇴 처리되었습니다. 그동안 이용해주셔서 감사합니다.".formatted(event.nickname());
                case REQUESTED -> "%s님, 요청하신 탈퇴 절차가 완료되었습니다. 그동안 이용해주셔서 감사합니다.".formatted(event.nickname());
            };
            mailService.send(
                    event.email(), "[먹을지도] 탈퇴가 완료되었습니다.", body
            );
        } catch (Exception ex) {
            /**
             * 삭제는 이미 커밋되어 되돌릴 필요도, 되돌릴 방법도 없다. 안내 메일 실패는 로그로만 남긴다.
             */
            log.error("[AccountLifecycle] 탈퇴 완료 메일 발송 실패: userId={}, reason={}", event.userId(), event.reason(), ex);
        }
    }

}
