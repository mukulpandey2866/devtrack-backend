package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.enums.ProjectStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String projectKey,
        String description,
        ProjectStatus status,
        UUID ownerId,
        ProjectRole currentUserRole,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
