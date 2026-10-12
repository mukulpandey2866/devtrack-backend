package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.AddMemberRequest;
import com.devtrack.devtrack_backend.dto.MemberResponse;
import com.devtrack.devtrack_backend.dto.UpdateMemberRoleRequest;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.ProjectMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    public ProjectMemberController(ProjectMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return memberService.list(user.id(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                              @Valid @RequestBody AddMemberRequest request) {
        return memberService.add(user.id(), projectId, request);
    }

    @PutMapping("/{userId}")
    public MemberResponse changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                     @PathVariable UUID userId, @Valid @RequestBody UpdateMemberRoleRequest request) {
        return memberService.changeRole(user.id(), projectId, userId, request.role());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                       @PathVariable UUID userId) {
        memberService.remove(user.id(), projectId, userId);
    }
}
