package com.devtrack.devtrack_backend.mapper;

import com.devtrack.devtrack_backend.dto.UserResponse;
import com.devtrack.devtrack_backend.dto.UserSummary;
import com.devtrack.devtrack_backend.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getPlatformRole(), user.getCreatedAt());
    }

    public static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
