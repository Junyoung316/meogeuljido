package com.amugeona.meogeuljido.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRequest(
    @NotBlank
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    String email
) {
}
