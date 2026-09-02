package com.amugeona.meogeuljido.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "닉네임은 필수 입니다.")
        @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
        String nickname
) {
}
