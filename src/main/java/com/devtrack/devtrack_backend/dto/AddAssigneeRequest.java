package com.devtrack.devtrack_backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddAssigneeRequest(@NotNull UUID userId) {
}
