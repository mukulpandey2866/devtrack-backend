package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.CreateProjectRequest;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.dto.ProjectResponse;
import com.devtrack.devtrack_backend.dto.UpdateProjectRequest;
import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.entity.ProjectMember;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.enums.ProjectStatus;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.mapper.ProjectMapper;
import com.devtrack.devtrack_backend.repository.ProjectMemberRepository;
import com.devtrack.devtrack_backend.repository.ProjectRepository;
import com.devtrack.devtrack_backend.service.ProjectAccessService.ProjectAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lifecycle policy: projects are never hard-deleted through the API. DELETE archives the project
 * (owner only); an archived project is read-only until an owner sets a non-archived status again.
 */
@Service
public class ProjectService {

    private static final Set<String> SORTABLE = Set.of("name", "projectKey", "status", "createdAt", "updatedAt");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAccessService accessService;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          ProjectAccessService accessService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
    }

    @Transactional
    public ProjectResponse create(UUID actorId, CreateProjectRequest request) {
        String key = request.projectKey().toUpperCase(Locale.ROOT);
        if (projectRepository.existsByProjectKey(key)) {
            throw new ConflictException("Project key is already in use");
        }
        Project project = new Project(request.name().trim(), key, blankToNull(request.description()), actorId);
        try {
            projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Project key is already in use");
        }
        memberRepository.save(new ProjectMember(project.getId(), actorId, ProjectRole.OWNER));
        return ProjectMapper.toResponse(project, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(UUID actorId, ProjectStatus status, Pageable pageable) {
        Pageable restricted = PageRequests.restrict(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Set<ProjectStatus> statuses = status == null ? EnumSet.allOf(ProjectStatus.class) : EnumSet.of(status);
        Page<Project> page = projectRepository.findAccessible(actorId, statuses, restricted);

        Map<UUID, ProjectRole> roles = memberRepository
                .findByIdUserIdAndIdProjectIdIn(actorId, page.getContent().stream().map(Project::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole));
        return PageResponse.of(page, project -> ProjectMapper.toResponse(project, roles.get(project.getId())));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID actorId, UUID projectId) {
        ProjectAccess access = accessService.require(projectId, actorId);
        return ProjectMapper.toResponse(access.project(), access.role());
    }

    @Transactional
    public ProjectResponse update(UUID actorId, UUID projectId, UpdateProjectRequest request) {
        ProjectAccess access = accessService.require(projectId, actorId);
        Project project = access.project();
        ProjectStatus newStatus = request.status() == null ? project.getStatus() : request.status();

        boolean changesArchivedState = (newStatus == ProjectStatus.ARCHIVED) != (project.getStatus() == ProjectStatus.ARCHIVED);
        if (changesArchivedState) {
            access.atLeast(ProjectRole.OWNER);
        } else {
            access.atLeast(ProjectRole.MANAGER).writable();
        }

        project.setName(request.name().trim());
        project.setDescription(blankToNull(request.description()));
        project.setStatus(newStatus);
        return ProjectMapper.toResponse(projectRepository.saveAndFlush(project), access.role());
    }

    @Transactional
    public void archive(UUID actorId, UUID projectId) {
        ProjectAccess access = accessService.require(projectId, actorId).atLeast(ProjectRole.OWNER);
        Project project = access.project();
        if (project.getStatus() != ProjectStatus.ARCHIVED) {
            project.setStatus(ProjectStatus.ARCHIVED);
            projectRepository.saveAndFlush(project);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
