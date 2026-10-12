package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.CreateIssueRequest;
import com.devtrack.devtrack_backend.dto.IssueFilter;
import com.devtrack.devtrack_backend.dto.IssueHistoryResponse;
import com.devtrack.devtrack_backend.dto.IssueResponse;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.dto.UpdateIssueRequest;
import com.devtrack.devtrack_backend.dto.UpdateIssueStatusRequest;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.IssueService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping("/api/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                @Valid @RequestBody CreateIssueRequest request) {
        return issueService.create(user.id(), projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/issues")
    public PageResponse<IssueResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID projectId,
                                            @Valid @ParameterObject IssueFilter filter,
                                            @ParameterObject Pageable pageable) {
        return issueService.list(user.id(), projectId, filter, pageable);
    }

    @GetMapping("/api/issues/{issueId}")
    public IssueResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId) {
        return issueService.get(user.id(), issueId);
    }

    @PutMapping("/api/issues/{issueId}")
    public IssueResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId,
                                @Valid @RequestBody UpdateIssueRequest request) {
        return issueService.update(user.id(), issueId, request);
    }

    @PatchMapping("/api/issues/{issueId}/status")
    public IssueResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId,
                                      @Valid @RequestBody UpdateIssueStatusRequest request) {
        return issueService.changeStatus(user.id(), issueId, request.status());
    }

    @DeleteMapping("/api/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId) {
        issueService.delete(user.id(), issueId);
    }

    /** Read-only: history is written by the services and cannot be modified through the API. */
    @GetMapping("/api/issues/{issueId}/history")
    public PageResponse<IssueHistoryResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable UUID issueId,
                                                      @ParameterObject Pageable pageable) {
        return issueService.history(user.id(), issueId, pageable);
    }
}
