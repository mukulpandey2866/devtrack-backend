package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.entity.ProjectMember;
import com.devtrack.devtrack_backend.entity.ProjectMemberId;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.enums.ProjectStatus;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.ForbiddenOperationException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.repository.ProjectMemberRepository;
import com.devtrack.devtrack_backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central resource-level authorization. Callers who are not project members always receive 404 so that
 * the existence of private projects cannot be probed; members with too little privilege receive 403.
 */
@Service
public class ProjectAccessService {

    private static final String PROJECT_NOT_FOUND = "Project not found";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public record ProjectAccess(Project project, ProjectRole role) {

        public ProjectAccess atLeast(ProjectRole required) {
            if (!role.isAtLeast(required)) {
                throw new ForbiddenOperationException("This action requires the " + required + " role or higher");
            }
            return this;
        }

        public ProjectAccess writable() {
            if (project.getStatus() == ProjectStatus.ARCHIVED) {
                throw new ConflictException("The project is archived and read-only");
            }
            return this;
        }
    }

    @Transactional(readOnly = true)
    public ProjectAccess require(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(PROJECT_NOT_FOUND));
        return withMembership(project, userId);
    }

    /** Same as {@link #require} but takes a row lock on the project to serialise concurrent changes. */
    @Transactional
    public ProjectAccess requireForUpdate(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(PROJECT_NOT_FOUND));
        return withMembership(project, userId);
    }

    private ProjectAccess withMembership(Project project, UUID userId) {
        ProjectMember member = memberRepository.findById(new ProjectMemberId(project.getId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException(PROJECT_NOT_FOUND));
        return new ProjectAccess(project, member.getRole());
    }
}
