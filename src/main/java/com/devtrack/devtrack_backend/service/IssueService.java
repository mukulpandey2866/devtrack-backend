package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.CreateIssueRequest;
import com.devtrack.devtrack_backend.dto.IssueFilter;
import com.devtrack.devtrack_backend.dto.IssueHistoryResponse;
import com.devtrack.devtrack_backend.dto.IssueResponse;
import com.devtrack.devtrack_backend.dto.PageResponse;
import com.devtrack.devtrack_backend.dto.UpdateIssueRequest;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueHistory;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.IssuePriority;
import com.devtrack.devtrack_backend.enums.IssueStatus;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.IssueMapper;
import com.devtrack.devtrack_backend.mapper.UserMapper;
import com.devtrack.devtrack_backend.repository.IssueCommentRepository;
import com.devtrack.devtrack_backend.repository.IssueAssigneeRepository;
import com.devtrack.devtrack_backend.repository.IssueHistoryRepository;
import com.devtrack.devtrack_backend.repository.IssueRepository;
import com.devtrack.devtrack_backend.repository.IssueSpecifications;
import com.devtrack.devtrack_backend.repository.UserRepository;
import com.devtrack.devtrack_backend.service.ProjectAccessService.ProjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deletion policy: DELETE hard-deletes the issue together with its comments, assignments, label links and
 * history (the history is part of the issue, not an independent audit log). Only MANAGER or OWNER may delete;
 * use the CANCELLED status to retire an issue while keeping its record.
 */
@Service
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);
    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "issueNumber", "dueDate", "title");
    private static final Set<String> HISTORY_SORTABLE = Set.of("changedAt");

    private final IssueRepository issueRepository;
    private final IssueCommentRepository commentRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final IssueHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService accessService;
    private final IssueHistoryRecorder historyRecorder;
    private final IssueAssembler assembler;
    private final Clock clock;

    public IssueService(IssueRepository issueRepository, IssueCommentRepository commentRepository,
                        IssueAssigneeRepository assigneeRepository, IssueHistoryRepository historyRepository,
                        UserRepository userRepository, ProjectAccessService accessService,
                        IssueHistoryRecorder historyRecorder, IssueAssembler assembler, Clock clock) {
        this.issueRepository = issueRepository;
        this.commentRepository = commentRepository;
        this.assigneeRepository = assigneeRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.historyRecorder = historyRecorder;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public IssueResponse create(UUID actorId, UUID projectId, CreateIssueRequest request) {
        // The project row lock serialises concurrent creations, making max+1 numbering race-free.
        ProjectAccess access = accessService.requireForUpdate(projectId, actorId)
                .atLeast(ProjectRole.MEMBER).writable();
        validateDueDate(request.dueDate());

        int issueNumber = issueRepository.findMaxIssueNumber(projectId) + 1;
        Issue issue = new Issue(projectId, issueNumber, request.title().trim(), request.issueType(), actorId);
        issue.setDescription(blankToNull(request.description()));
        issue.setPriority(request.priority() == null ? IssuePriority.MEDIUM : request.priority());
        issue.setDueDate(request.dueDate());
        issue.setEstimatedHours(scale(request.estimatedHours()));
        issueRepository.saveAndFlush(issue);

        historyRecorder.record(issue.getId(), actorId, "created", null,
                IssueMapper.issueKey(access.project().getProjectKey(), issueNumber));
        return assembler.assemble(issue);
    }

    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> list(UUID actorId, UUID projectId, IssueFilter filter, Pageable pageable) {
        accessService.require(projectId, actorId);
        if (filter.dueAfter() != null && filter.dueBefore() != null && filter.dueAfter().isAfter(filter.dueBefore())) {
            throw new BadRequestException("dueAfter must not be later than dueBefore");
        }
        Pageable restricted = PageRequests.restrict(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Issue> page = issueRepository.findAll(IssueSpecifications.forProject(projectId, filter), restricted);
        return PageResponse.of(page, assembler.assemble(page.getContent()));
    }

    @Transactional(readOnly = true)
    public IssueResponse get(UUID actorId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(issue.getProjectId(), actorId);
        return assembler.assemble(issue);
    }

    @Transactional
    public IssueResponse update(UUID actorId, UUID issueId, UpdateIssueRequest request) {
        Issue issue = lockForWrite(actorId, issueId, ProjectRole.MEMBER);

        String title = request.title().trim();
        String description = blankToNull(request.description());
        BigDecimal hours = scale(request.estimatedHours());
        if (!Objects.equals(request.dueDate(), issue.getDueDate())) {
            validateDueDate(request.dueDate());
        }

        historyRecorder.record(issueId, actorId, "title", issue.getTitle(), title);
        historyRecorder.record(issueId, actorId, "description", issue.getDescription(), description);
        historyRecorder.record(issueId, actorId, "issueType", issue.getIssueType(), request.issueType());
        historyRecorder.record(issueId, actorId, "priority", issue.getPriority(), request.priority());
        historyRecorder.record(issueId, actorId, "dueDate", issue.getDueDate(), request.dueDate());
        historyRecorder.record(issueId, actorId, "estimatedHours", plain(issue.getEstimatedHours()), plain(hours));

        issue.setTitle(title);
        issue.setDescription(description);
        issue.setIssueType(request.issueType());
        issue.setPriority(request.priority());
        issue.setDueDate(request.dueDate());
        issue.setEstimatedHours(hours);
        return assembler.assemble(issueRepository.saveAndFlush(issue));
    }

    @Transactional
    public IssueResponse changeStatus(UUID actorId, UUID issueId, IssueStatus status) {
        Issue issue = lockForWrite(actorId, issueId, ProjectRole.MEMBER);
        historyRecorder.record(issueId, actorId, "status", issue.getStatus(), status);
        issue.setStatus(status);
        return assembler.assemble(issueRepository.saveAndFlush(issue));
    }

    @Transactional
    public void delete(UUID actorId, UUID issueId) {
        Issue issue = lockForWrite(actorId, issueId, ProjectRole.MANAGER);
        commentRepository.deleteByIssueId(issueId);
        assigneeRepository.deleteByIssueId(issueId);
        historyRepository.deleteByIssueId(issueId);
        // issue_labels rows are removed by the ON DELETE CASCADE foreign keys.
        issueRepository.deleteById(issue.getId());
        issueRepository.flush();
        log.info("Issue {} deleted by user {}", issueId, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<IssueHistoryResponse> history(UUID actorId, UUID issueId, Pageable pageable) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(issue.getProjectId(), actorId);

        Pageable restricted = PageRequests.restrict(pageable, HISTORY_SORTABLE, Sort.by(Sort.Direction.DESC, "changedAt"));
        Page<IssueHistory> page = historyRepository.findByIssueId(issueId, restricted);
        Map<UUID, User> users = userRepository
                .findAllById(page.getContent().stream().map(IssueHistory::getChangedBy).filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, history -> IssueMapper.toHistoryResponse(history,
                history.getChangedBy() == null || !users.containsKey(history.getChangedBy())
                        ? null : UserMapper.toSummary(users.get(history.getChangedBy()))));
    }

    /** Authorises first (without locking anything), then loads the issue under a row lock. */
    private Issue lockForWrite(UUID actorId, UUID issueId, ProjectRole minimumRole) {
        UUID projectId = issueRepository.findProjectIdById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(projectId, actorId).atLeast(minimumRole).writable();
        return issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
    }

    private void validateDueDate(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("Due date must not be in the past");
        }
    }

    private static BigDecimal scale(BigDecimal hours) {
        return hours == null ? null : hours.setScale(2);
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
