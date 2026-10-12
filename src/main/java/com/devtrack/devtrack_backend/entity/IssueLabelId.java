package com.devtrack.devtrack_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IssueLabelId implements Serializable {

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "label_id", nullable = false)
    private UUID labelId;

    protected IssueLabelId() {
        // Required by JPA
    }

    public IssueLabelId(UUID issueId, UUID labelId) {
        this.issueId = issueId;
        this.labelId = labelId;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getLabelId() {
        return labelId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IssueLabelId other
                && Objects.equals(issueId, other.issueId)
                && Objects.equals(labelId, other.labelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issueId, labelId);
    }
}
