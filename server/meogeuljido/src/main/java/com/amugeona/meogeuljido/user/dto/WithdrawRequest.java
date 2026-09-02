package com.amugeona.meogeuljido.user.dto;

import com.amugeona.meogeuljido.user.entity.WithdrawalReasonCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequest(
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,
        @NotNull(message = "탈퇴 사유를 선택해주세요.")
        WithdrawalReasonCategory reasonCategory,
        String reasonDetail
) {
}
