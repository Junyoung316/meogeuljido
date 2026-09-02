package com.amugeona.meogeuljido.common.event;

public record WithdrawalCompletedEvent(
        Long userId,
        String email,
        String nickname,
        Reason reason
) {

    public enum Reason {
        REQUESTED, DORMANT
    }
}
