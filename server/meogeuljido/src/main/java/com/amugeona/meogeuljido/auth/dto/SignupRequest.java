package com.amugeona.meogeuljido.auth.dto;

import com.amugeona.meogeuljido.user.NicknamePolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH, message = NicknamePolicy.LENGTH_MESSAGE)
        String nickname,

        @NotBlank
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank
        String emailVerifiedToken
) {
}
