package com.amugeona.meogeuljido.auth.dto;

public record LoginResponse(
        String accessToken,
        UserSummary user
) {
    public record UserSummary(
            Long id,
            String nickname,
            String role
    ) {

    }
}
