package com.devtrack.devtrack_backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentResponse(UUID id, UUID issueId, UserSummary author, String content,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
