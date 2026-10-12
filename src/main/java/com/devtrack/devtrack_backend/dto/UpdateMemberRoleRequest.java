package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull ProjectRole role) {
}
