package com.devtrack.devtrack_backend.dto;

import java.util.List;

public record AiIssueDraftResponse(
        String title,
        String description,
        List<String> acceptanceCriteria,
        List<String> suggestedLabels) {
}
