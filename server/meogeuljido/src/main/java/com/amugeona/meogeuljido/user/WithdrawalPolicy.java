package com.amugeona.meogeuljido.user;

/**
 * user 도메인 전반에서 공유하는 탈퇴 정책 상수
 * AccountLifecycleScheduler(실제 배치 판정)와 UserService(감사 로그 문구)가
 * 각자 값을 따로 들고 있다가 어긋날 위험이 있어
 * UserRepository.DORMANT_WITHDRAWAL_CONDITION과 같은 이유로 한곳에 모음
 */
public final class WithdrawalPolicy {

    public static final int GRACE_DAYS = 7;

    private WithdrawalPolicy() {
    }

}
