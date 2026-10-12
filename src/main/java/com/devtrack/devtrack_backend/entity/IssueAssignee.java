package com.devtrack.devtrack_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue_assignees")
public class IssueAssignee {

    @EmbeddedId
    private IssueAssigneeId id;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    protected IssueAssignee() {
        // Required by JPA
    }

    public IssueAssignee(UUID issueId, UUID userId) {
        this.id = new IssueAssigneeId(issueId, userId);
    }

    public UUID getIssueId() {
        return id.getIssueId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
