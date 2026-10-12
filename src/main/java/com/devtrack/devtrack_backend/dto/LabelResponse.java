package com.devtrack.devtrack_backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabelResponse(UUID id, UUID projectId, String name, String color, OffsetDateTime createdAt) {
}
