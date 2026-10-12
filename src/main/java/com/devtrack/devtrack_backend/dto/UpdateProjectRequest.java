package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The project key is immutable and therefore intentionally absent. */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 10000) String description,
        ProjectStatus status) {
}
