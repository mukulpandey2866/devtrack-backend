package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIssueStatusRequest(@NotNull IssueStatus status) {
}
