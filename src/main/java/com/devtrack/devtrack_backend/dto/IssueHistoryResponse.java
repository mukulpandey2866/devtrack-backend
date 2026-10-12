package com.devtrack.devtrack_backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssueHistoryResponse(UUID id, UUID issueId, UserSummary changedBy, String fieldName,
                                   String oldValue, String newValue, OffsetDateTime changedAt) {
}
