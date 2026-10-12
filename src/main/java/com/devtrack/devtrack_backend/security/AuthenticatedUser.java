package com.devtrack.devtrack_backend.security;

import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.PlatformRole;

import java.util.UUID;

/** Identity derived from a validated token and re-checked against the database on every request. */
public record AuthenticatedUser(UUID id, String email, PlatformRole platformRole) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPlatformRole());
    }
}
