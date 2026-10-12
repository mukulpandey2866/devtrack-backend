package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.ProjectRole;

import java.time.OffsetDateTime;

public record MemberResponse(UserSummary user, ProjectRole role, OffsetDateTime joinedAt) {
}
