package com.devtrack.devtrack_backend.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** At least one of title and description must be non-blank; that rule is enforced in the service. */
public record AiIssueDraftRequest(
        UUID projectId,
        @Size(max = 200) String title,
        @Size(max = 5000) String description) {
}
