package com.amugeona.meogeuljido.user.dto;

import com.amugeona.meogeuljido.user.entity.User;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String role,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name(), user.getCreatedAt());
    }
}
