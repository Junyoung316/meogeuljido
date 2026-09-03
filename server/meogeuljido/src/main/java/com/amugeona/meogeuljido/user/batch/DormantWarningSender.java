package com.amugeona.meogeuljido.user.batch;

import com.amugeona.meogeuljido.common.mail.MailService;
import com.amugeona.meogeuljido.user.entity.User;
import com.amugeona.meogeuljido.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DormantWarningSender {

    private final UserRepository userRepository;
    private final MailService mailService;

    /**
     * REQUIRES_NEW: 호출자(warnDormantUsers)의 트랜잭션과 완전히 분리된 새 트랜잭션에서 실행
     * 메일 발송에 성공한 유저는 이 메서드가 끝나는 즉시 markDormantWarningSent()가 커밋
     * 이후 다른 유저 처리중 배치 전체가 죽더라도 이미 처리된 유저의 기록은 남음
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAndMark(Long userId, String email, String nickname, int leadDays) {
        mailService.send(
                email, "[먹을지도] 장기 미접속으로 곧 계정이 탈퇴 처리됩니다.", ("%s님, 1년 이상 로그인하지 않아 %d일 후 계정이 자동으로 탈퇴 처리될 예정입니다. " +
                        "계속 이용하시려면 로그인해주세요.").formatted(nickname, leadDays)
        );
        userRepository.findById(userId).ifPresent(User::markDormantWarningSent);
    }

}
