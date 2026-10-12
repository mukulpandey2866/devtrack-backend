package com.devtrack.devtrack_backend.dto;

import java.time.OffsetDateTime;

public record AssigneeResponse(UserSummary user, OffsetDateTime assignedAt) {
}
