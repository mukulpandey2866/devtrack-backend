package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.CommentRequest;
import com.devtrack.devtrack_backend.dto.CommentResponse;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueComment;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.exception.ForbiddenOperationException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.IssueMapper;
import com.devtrack.devtrack_backend.mapper.UserMapper;
import com.devtrack.devtrack_backend.repository.IssueCommentRepository;
import com.devtrack.devtrack_backend.repository.IssueRepository;
import com.devtrack.devtrack_backend.repository.UserRepository;
import com.devtrack.devtrack_backend.service.ProjectAccessService.ProjectAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comment rules: MEMBER or higher can comment; only the author can edit a comment (while still a MEMBER or
 * higher); the author or a MANAGER/OWNER can delete it. Viewers can read but never write.
 */
@Service
public class CommentService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt");

    private final IssueCommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService accessService;

    public CommentService(IssueCommentRepository commentRepository, IssueRepository issueRepository,
                          UserRepository userRepository, ProjectAccessService accessService) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @Transactional
    public CommentResponse create(UUID actorId, UUID issueId, CommentRequest request) {
        Issue issue = findIssue(issueId);
        accessService.require(issue.getProjectId(), actorId).atLeast(ProjectRole.MEMBER).writable();
        IssueComment comment = commentRepository.saveAndFlush(new IssueComment(issueId, actorId, request.content().strip()));
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(UUID actorId, UUID issueId, Pageable pageable) {
        Issue issue = findIssue(issueId);
        accessService.require(issue.getProjectId(), actorId);

        Pageable restricted = PageRequests.restrict(pageable, SORTABLE, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<IssueComment> page = commentRepository.findByIssueId(issueId, restricted);
        Map<UUID, User> authors = userRepository
                .findAllById(page.getContent().stream().map(IssueComment::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, comment -> IssueMapper.toCommentResponse(comment,
                UserMapper.toSummary(authors.get(comment.getAuthorId()))));
    }

    @Transactional
    public CommentResponse update(UUID actorId, UUID commentId, CommentRequest request) {
        IssueComment comment = findComment(commentId);
        Issue issue = findIssue(comment.getIssueId());
        accessService.require(issue.getProjectId(), actorId).atLeast(ProjectRole.MEMBER).writable();
        if (!comment.getAuthorId().equals(actorId)) {
            throw new ForbiddenOperationException("Only the author can edit a comment");
        }
        comment.setContent(request.content().strip());
        return toResponse(commentRepository.saveAndFlush(comment));
    }

    @Transactional
    public void delete(UUID actorId, UUID commentId) {
        IssueComment comment = findComment(commentId);
        Issue issue = findIssue(comment.getIssueId());
        ProjectAccess access = accessService.require(issue.getProjectId(), actorId).writable();
        boolean isAuthor = comment.getAuthorId().equals(actorId);
        if (isAuthor) {
            access.atLeast(ProjectRole.MEMBER);
        } else if (!access.role().isAtLeast(ProjectRole.MANAGER)) {
            throw new ForbiddenOperationException("Only the author or a project manager can delete a comment");
        }
        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(IssueComment comment) {
        User author = userRepository.findById(comment.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return IssueMapper.toCommentResponse(comment, UserMapper.toSummary(author));
    }

    private Issue findIssue(UUID issueId) {
        return issueRepository.findById(issueId).orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
    }

    private IssueComment findComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
    }
}
