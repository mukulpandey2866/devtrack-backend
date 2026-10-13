package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.AiIssueDraftRequest;
import com.devtrack.devtrack_backend.dto.AiIssueDraftResponse;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.AiIssueDraftService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final AiIssueDraftService draftService;

    public AiController(AiIssueDraftService draftService) {
        this.draftService = draftService;
    }

    /** Generates a draft only; saving still goes through POST /api/projects/{projectId}/issues. */
    @PostMapping("/api/ai/issues/draft")
    public AiIssueDraftResponse draftIssue(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody AiIssueDraftRequest request) {
        return draftService.generate(user.id(), request);
    }
}
