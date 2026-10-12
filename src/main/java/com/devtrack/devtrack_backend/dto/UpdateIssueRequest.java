package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.IssuePriority;
import com.devtrack.devtrack_backend.enums.IssueType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Full replacement of the editable fields; status changes go through the dedicated status endpoint. */
public record UpdateIssueRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @NotNull IssueType issueType,
        @NotNull IssuePriority priority,
        LocalDate dueDate,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal estimatedHours) {
}
