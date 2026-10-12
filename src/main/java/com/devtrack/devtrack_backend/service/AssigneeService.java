package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.AddAssigneeRequest;
import com.devtrack.devtrack_backend.dto.AssigneeResponse;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueAssignee;
import com.devtrack.devtrack_backend.entity.IssueAssigneeId;
import com.devtrack.devtrack_backend.entity.ProjectMemberId;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.UserMapper;
import com.devtrack.devtrack_backend.repository.IssueAssigneeRepository;
import com.devtrack.devtrack_backend.repository.IssueRepository;
import com.devtrack.devtrack_backend.repository.ProjectMemberRepository;
import com.devtrack.devtrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssigneeService {

    private final IssueRepository issueRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService accessService;
    private final IssueHistoryRecorder historyRecorder;

    public AssigneeService(IssueRepository issueRepository, IssueAssigneeRepository assigneeRepository,
                           ProjectMemberRepository memberRepository, UserRepository userRepository,
                           ProjectAccessService accessService, IssueHistoryRecorder historyRecorder) {
        this.issueRepository = issueRepository;
        this.assigneeRepository = assigneeRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.historyRecorder = historyRecorder;
    }

    @Transactional(readOnly = true)
    public List<AssigneeResponse> list(UUID actorId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(issue.getProjectId(), actorId);

        List<IssueAssignee> assignments = assigneeRepository.findByIdIssueIdOrderByAssignedAtAsc(issueId);
        Map<UUID, User> users = userRepository.findAllById(assignments.stream().map(IssueAssignee::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return assignments.stream()
                .map(a -> new AssigneeResponse(UserMapper.toSummary(users.get(a.getUserId())), a.getAssignedAt()))
                .toList();
    }

    @Transactional
    public AssigneeResponse add(UUID actorId, UUID issueId, AddAssigneeRequest request) {
        Issue issue = lockForWrite(actorId, issueId);
        UUID userId = request.userId();

        if (!memberRepository.existsById(new ProjectMemberId(issue.getProjectId(), userId))) {
            throw new BadRequestException("The user is not a member of this project");
        }
        if (assigneeRepository.existsById(new IssueAssigneeId(issueId, userId))) {
            throw new ConflictException("The user is already assigned to this issue");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("The user is not a member of this project"));
        IssueAssignee assignment = assigneeRepository.saveAndFlush(new IssueAssignee(issueId, userId));
        historyRecorder.record(issueId, actorId, "assignee", null, userId);
        return new AssigneeResponse(UserMapper.toSummary(user), assignment.getAssignedAt());
    }

    @Transactional
    public void remove(UUID actorId, UUID issueId, UUID userId) {
        lockForWrite(actorId, issueId);
        IssueAssigneeId id = new IssueAssigneeId(issueId, userId);
        if (!assigneeRepository.existsById(id)) {
            throw new ResourceNotFoundException("The user is not assigned to this issue");
        }
        assigneeRepository.deleteById(id);
        assigneeRepository.flush();
        historyRecorder.record(issueId, actorId, "assignee", userId, null);
    }

    private Issue lockForWrite(UUID actorId, UUID issueId) {
        UUID projectId = issueRepository.findProjectIdById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(projectId, actorId).atLeast(ProjectRole.MEMBER).writable();
        return issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
    }
}
