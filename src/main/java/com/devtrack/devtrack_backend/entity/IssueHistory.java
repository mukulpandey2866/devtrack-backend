package com.devtrack.devtrack_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only audit record; deliberately has no setters. */
@Entity
@Table(name = "issue_history")
public class IssueHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(name = "field_name", nullable = false, updatable = false, length = 50)
    private String fieldName;

    @Column(name = "old_value", updatable = false, columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", updatable = false, columnDefinition = "text")
    private String newValue;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    protected IssueHistory() {
        // Required by JPA
    }

    public IssueHistory(UUID issueId, UUID changedBy, String fieldName, String oldValue, String newValue) {
        this.issueId = issueId;
        this.changedBy = changedBy;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }
}
