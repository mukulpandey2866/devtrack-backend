package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.PlatformRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(UUID id, String fullName, String email, PlatformRole platformRole, OffsetDateTime createdAt) {
}
