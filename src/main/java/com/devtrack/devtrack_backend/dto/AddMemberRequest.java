package com.devtrack.devtrack_backend.dto;

import com.devtrack.devtrack_backend.enums.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull ProjectRole role) {

    public AddMemberRequest {
        email = email == null ? null : email.strip();
    }
}
