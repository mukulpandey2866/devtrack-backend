package com.devtrack.devtrack_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Replaces the complete label set of an issue; an empty set removes all labels. */
public record SetIssueLabelsRequest(@NotNull @Size(max = 50) Set<UUID> labelIds) {
}
