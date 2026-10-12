package com.devtrack.devtrack_backend.mapper;

import com.devtrack.devtrack_backend.dto.LabelResponse;
import com.devtrack.devtrack_backend.entity.Label;

public final class LabelMapper {

    private LabelMapper() {
    }

    public static LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getProjectId(), label.getName(), label.getColor(),
                label.getCreatedAt());
    }
}
