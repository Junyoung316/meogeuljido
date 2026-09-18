package com.amugeona.meogeuljido.user.batch;

import com.amugeona.meogeuljido.auth.redis.RefreshTokenRepository;
import com.amugeona.meogeuljido.auth.redis.TokenBlacklistRepository;
import com.amugeona.meogeuljido.common.event.WithdrawalCompletedEvent;
import com.amugeona.meogeuljido.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WithdrawalSessionRevocationListener {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * AFTER_COMMI: 탈퇴(deletedAt) 커밋이 실제로 끝난 뒤에만 세션을 정리
     * 커밋 전에 Redis부터 지우면, 아직 deletedAt이 안 보이는 동시 reissue() 요청이 그 틈에 새 리프레시 토큰을 발급받아
     * 삭제를 피해살 수 있음 - 순서를 뒤집으면 reissue()가 이 시점 이후엔 항상 userRepository.findById()에서 먼저 막히므로, 그 경쟁 자체가 사라짐
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawalCompleted(WithdrawalCompletedEvent event) {
        refreshTokenRepository.delete(event.userId());
        tokenBlacklistRepository.blacklistAllIssuedBefore(event.userId(), jwtTokenProvider.accessTokenValidity());
    }

}
