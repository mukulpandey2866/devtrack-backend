package com.devtrack.devtrack_backend.controller;

import com.devtrack.devtrack_backend.dto.CommentRequest;
import com.devtrack.devtrack_backend.dto.CommentResponse;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.security.AuthenticatedUser;
import com.devtrack.devtrack_backend.service.CommentService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/issues/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID issueId,
                                  @Valid @RequestBody CommentRequest request) {
        return commentService.create(user.id(), issueId, request);
    }

    @GetMapping("/api/issues/{issueId}/comments")
    public PageResponse<CommentResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID issueId, @ParameterObject Pageable pageable) {
        return commentService.list(user.id(), issueId, pageable);
    }

    @PutMapping("/api/comments/{commentId}")
    public CommentResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId,
                                  @Valid @RequestBody CommentRequest request) {
        return commentService.update(user.id(), commentId, request);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId) {
        commentService.delete(user.id(), commentId);
    }
}
