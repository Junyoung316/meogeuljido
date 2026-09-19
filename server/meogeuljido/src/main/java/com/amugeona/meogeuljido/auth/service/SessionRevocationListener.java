package com.amugeona.meogeuljido.auth.service;

import com.amugeona.meogeuljido.common.event.SessionRevocationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SessionRevocationListener {

    private final AuthService authService;

    /**
     * AFTER_COMMIT: 세션 무효화를 요청한 토랜잭션이 실제로 커밋된 뒤에만 실행
     * confirmPasswordReset()/requestWithdrawal()/finalizeWithdrawal() 셋 다 이 이벤트만
     * 발행하고, 실제 Redis 무혀화는 항상 여기 한 곳에서만 일어남
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionRevocationRequested(SessionRevocationRequestedEvent event) {
        authService.revokeAllSessions(event.userId());
    }
}
