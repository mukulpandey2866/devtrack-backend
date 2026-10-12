package com.devtrack.devtrack_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "issue_labels")
public class IssueLabel {

    @EmbeddedId
    private IssueLabelId id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    protected IssueLabel() {
        // Required by JPA
    }

    public IssueLabel(UUID issueId, UUID labelId, UUID projectId) {
        this.id = new IssueLabelId(issueId, labelId);
        this.projectId = projectId;
    }

    public UUID getIssueId() {
        return id.getIssueId();
    }

    public UUID getLabelId() {
        return id.getLabelId();
    }

    public UUID getProjectId() {
        return projectId;
    }
}
