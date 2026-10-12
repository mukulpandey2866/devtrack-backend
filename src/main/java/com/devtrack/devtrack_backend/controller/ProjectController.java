package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.CreateProjectRequest;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.dto.ProjectResponse;
import com.devtrack.devtrack_backend.dto.UpdateProjectRequest;
import com.devtrack.devtrack_backend.enums.ProjectStatus;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(user.id(), request);
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) ProjectStatus status,
                                              @ParameterObject Pageable pageable) {
        return projectService.list(user.id(), status, pageable);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projectService.get(user.id(), projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(user.id(), projectId, request);
    }

    /** Archives the project; projects are never hard-deleted through the API. */
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        projectService.archive(user.id(), projectId);
    }
}
