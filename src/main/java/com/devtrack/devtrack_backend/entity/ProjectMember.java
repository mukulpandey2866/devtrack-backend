package com.devtrack.devtrack_backend.entity;

import com.devtrack.devtrack_backend.enums.ProjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_members")
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    protected ProjectMember() {
        // Required by JPA
    }

    public ProjectMember(UUID projectId, UUID userId, ProjectRole role) {
        this.id = new ProjectMemberId(projectId, userId);
        this.role = role;
    }

    public UUID getProjectId() {
        return id.getProjectId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
