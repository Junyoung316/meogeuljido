package com.amugeona.meogeuljido.auth.dto;

import com.amugeona.meogeuljido.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank
        String resetToken,

        @NotBlank
        @Size(min = PasswordPolicy.MIN_PASSWORD_LENGTH, max = PasswordPolicy.MAX_PASSWORD_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.ALLOWED_CHARS_PATTERN, message = PasswordPolicy.ALLOWED_CHARS_MESSAGE)
        String newPassword
) {
}
