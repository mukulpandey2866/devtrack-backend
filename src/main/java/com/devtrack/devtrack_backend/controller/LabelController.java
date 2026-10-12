package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.LabelRequest;
import com.devtrack.devtrack_backend.dto.LabelResponse;
import com.devtrack.devtrack_backend.dto.SetIssueLabelsRequest;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.LabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping("/api/projects/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                @Valid @RequestBody LabelRequest request) {
        return labelService.create(user.id(), projectId, request);
    }

    @GetMapping("/api/projects/{projectId}/labels")
    public List<LabelResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return labelService.list(user.id(), projectId);
    }

    @PutMapping("/api/labels/{labelId}")
    public LabelResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID labelId,
                                @Valid @RequestBody LabelRequest request) {
        return labelService.update(user.id(), labelId, request);
    }

    @DeleteMapping("/api/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID labelId) {
        labelService.delete(user.id(), labelId);
    }

    @PutMapping("/api/issues/{issueId}/labels")
    public List<LabelResponse> setIssueLabels(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID issueId,
                                              @Valid @RequestBody SetIssueLabelsRequest request) {
        return labelService.setIssueLabels(user.id(), issueId, request);
    }
}
