package com.devtrack.devtrack_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IssueAssigneeId implements Serializable {

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected IssueAssigneeId() {
        // Required by JPA
    }

    public IssueAssigneeId(UUID issueId, UUID userId) {
        this.issueId = issueId;
        this.userId = userId;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IssueAssigneeId other
                && Objects.equals(issueId, other.issueId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issueId, userId);
    }
}
