package com.amugeona.meogeuljido.auth.dto;

import com.amugeona.meogeuljido.auth.PasswordPolicy;
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
        @Size(min = PasswordPolicy.MIN_PASSWORD_LENGTH, max = PasswordPolicy.MAX_PASSWORD_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        String password,

        @NotBlank
        String emailVerifiedToken
) {
}
