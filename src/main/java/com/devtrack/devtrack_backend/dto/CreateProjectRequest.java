package com.devtrack.devtrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                message = "must be 2-10 letters or digits and start with a letter") String projectKey,
        @Size(max = 10000) String description) {
}
