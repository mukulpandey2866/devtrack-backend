package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.IssuePriority;
import com.devtrack.devtrack_backend.enums.IssueStatus;
import com.devtrack.devtrack_backend.enums.IssueType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        String issueKey,
        int issueNumber,
        String title,
        String description,
        IssueType issueType,
        IssueStatus status,
        IssuePriority priority,
        UserSummary reporter,
        List<UserSummary> assignees,
        List<LabelResponse> labels,
        LocalDate dueDate,
        BigDecimal estimatedHours,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
