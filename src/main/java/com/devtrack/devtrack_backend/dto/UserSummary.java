package com.devtrack.devtrack_backend.dto;

import java.util.UUID;

public record UserSummary(UUID id, String fullName, String email) {
}
