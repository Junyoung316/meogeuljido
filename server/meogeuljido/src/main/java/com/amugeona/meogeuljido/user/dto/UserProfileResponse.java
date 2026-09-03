package com.amugeona.meogeuljido.user.dto;

import com.amugeona.meogeuljido.user.entity.User;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String role,
        int reviewCount,
        int bookmarkCount,
        int restaurantCount,
        OffsetDateTime createdAt
) {
    public static UserProfileResponse of(User user, ActivityCounts counts) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name(), counts.reviewCount(),  counts.bookmarkCount(), counts.restaurantCount(), user.getCreatedAt());
    }

    public record ActivityCounts(int reviewCount, int bookmarkCount, int restaurantCount) {}
}
