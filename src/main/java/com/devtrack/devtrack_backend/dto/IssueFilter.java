package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.IssuePriority;
import com.devtrack.devtrack_backend.enums.IssueStatus;
import com.devtrack.devtrack_backend.enums.IssueType;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Query-string filters for the issue list; list-valued filters may be repeated (status=TODO&status=DONE). */
public record IssueFilter(
        @Size(max = 10) List<IssueStatus> status,
        @Size(max = 10) List<IssuePriority> priority,
        @Size(max = 10) List<IssueType> type,
        UUID reporterId,
        UUID assigneeId,
        UUID labelId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
        @Size(max = 100) String q) {
}
