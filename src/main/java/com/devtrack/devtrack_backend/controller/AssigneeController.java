package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.AddAssigneeRequest;
import com.devtrack.devtrack_backend.dto.AssigneeResponse;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.AssigneeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/{issueId}/assignees")
public class AssigneeController {

    private final AssigneeService assigneeService;

    public AssigneeController(AssigneeService assigneeService) {
        this.assigneeService = assigneeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssigneeResponse add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId,
                                @Valid @RequestBody AddAssigneeRequest request) {
        return assigneeService.add(user.id(), issueId, request);
    }

    @GetMapping
    public List<AssigneeResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId) {
        return assigneeService.list(user.id(), issueId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId,
                       @PathVariable UUID userId) {
        assigneeService.remove(user.id(), issueId, userId);
    }
}
