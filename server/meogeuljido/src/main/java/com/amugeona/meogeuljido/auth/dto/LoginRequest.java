package com.amugeona.meogeuljido.auth.dto;

import com.amugeona.meogeuljido.auth.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = PasswordPolicy.MAX_PASSWORD_LENGTH)
        String password,

        boolean rememberMe
) {
}
