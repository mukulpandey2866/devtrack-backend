package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.AddMemberRequest;
import com.devtrack.devtrack_backend.dto.MemberResponse;
import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.entity.ProjectMember;
import com.devtrack.devtrack_backend.entity.ProjectMemberId;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.ForbiddenOperationException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.ProjectMapper;
import com.devtrack.devtrack_backend.repository.IssueAssigneeRepository;
import com.devtrack.devtrack_backend.repository.ProjectMemberRepository;
import com.devtrack.devtrack_backend.repository.ProjectRepository;
import com.devtrack.devtrack_backend.repository.UserRepository;
import com.devtrack.devtrack_backend.service.ProjectAccessService.ProjectAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Role rules: an OWNER may manage anyone and grant any role; a MANAGER may only manage members ranked
 * below them and only grant roles ranked below their own; nobody else may change membership except to
 * leave the project themselves. A project always keeps at least one OWNER.
 */
@Service
public class ProjectMemberService {

    private static final String MEMBER_NOT_FOUND = "Member not found";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final ProjectAccessService accessService;
    private final IssueHistoryRecorder historyRecorder;

    public ProjectMemberService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                                UserRepository userRepository, IssueAssigneeRepository assigneeRepository,
                                ProjectAccessService accessService, IssueHistoryRecorder historyRecorder) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.assigneeRepository = assigneeRepository;
        this.accessService = accessService;
        this.historyRecorder = historyRecorder;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UUID actorId, UUID projectId) {
        accessService.require(projectId, actorId);
        List<ProjectMember> members = memberRepository.findByIdProjectIdOrderByJoinedAtAsc(projectId);
        Map<UUID, User> users = userRepository.findAllById(members.stream().map(ProjectMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .map(member -> ProjectMapper.toMemberResponse(member, users.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public MemberResponse add(UUID actorId, UUID projectId, AddMemberRequest request) {
        ProjectAccess access = accessService.requireForUpdate(projectId, actorId)
                .atLeast(ProjectRole.MANAGER).writable();
        requireCanGrant(access.role(), request.role());

        User user = userRepository.findByEmailIgnoreCase(AuthService.normalizeEmail(request.email()))
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (memberRepository.existsById(new ProjectMemberId(projectId, user.getId()))) {
            throw new ConflictException("The user is already a member of this project");
        }
        ProjectMember member = memberRepository.saveAndFlush(new ProjectMember(projectId, user.getId(), request.role()));
        return ProjectMapper.toMemberResponse(member, user);
    }

    @Transactional
    public MemberResponse changeRole(UUID actorId, UUID projectId, UUID targetUserId, ProjectRole newRole) {
        ProjectAccess access = accessService.requireForUpdate(projectId, actorId)
                .atLeast(ProjectRole.MANAGER).writable();
        ProjectMember target = findMember(projectId, targetUserId);

        if (access.role() != ProjectRole.OWNER) {
            requireCanManage(access.role(), target.getRole());
            requireCanGrant(access.role(), newRole);
        }
        if (target.getRole() == ProjectRole.OWNER && newRole != ProjectRole.OWNER) {
            requireAnotherOwner(projectId);
        }
        target.setRole(newRole);
        memberRepository.saveAndFlush(target);
        syncPrimaryOwner(projectId);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ProjectMapper.toMemberResponse(target, user);
    }

    @Transactional
    public void remove(UUID actorId, UUID projectId, UUID targetUserId) {
        ProjectAccess access = accessService.requireForUpdate(projectId, actorId).writable();
        ProjectMember target = findMember(projectId, targetUserId);

        if (!actorId.equals(targetUserId)) {
            access.atLeast(ProjectRole.MANAGER);
            if (access.role() != ProjectRole.OWNER) {
                requireCanManage(access.role(), target.getRole());
            }
        }
        if (target.getRole() == ProjectRole.OWNER) {
            requireAnotherOwner(projectId);
        }

        // Keep the invariant that assignees are project members.
        List<UUID> assignedIssueIds = assigneeRepository.findIssueIdsAssignedToUserInProject(targetUserId, projectId);
        if (!assignedIssueIds.isEmpty()) {
            assigneeRepository.deleteByUserIdAndIssueIds(targetUserId, assignedIssueIds);
            assignedIssueIds.forEach(issueId -> historyRecorder.record(issueId, actorId, "assignee", targetUserId, null));
        }
        memberRepository.deleteById(new ProjectMemberId(projectId, targetUserId));
        memberRepository.flush();
        syncPrimaryOwner(projectId);
    }

    private ProjectMember findMember(UUID projectId, UUID userId) {
        return memberRepository.findById(new ProjectMemberId(projectId, userId))
                .orElseThrow(() -> new ResourceNotFoundException(MEMBER_NOT_FOUND));
    }

    private static void requireCanManage(ProjectRole actorRole, ProjectRole targetRole) {
        if (!actorRole.outranks(targetRole)) {
            throw new ForbiddenOperationException("You cannot manage a member with an equal or higher role");
        }
    }

    private static void requireCanGrant(ProjectRole actorRole, ProjectRole granted) {
        if (actorRole != ProjectRole.OWNER && !actorRole.outranks(granted)) {
            throw new ForbiddenOperationException("You cannot grant the " + granted + " role");
        }
    }

    private void requireAnotherOwner(UUID projectId) {
        if (memberRepository.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new ConflictException("A project must keep at least one owner");
        }
    }

    /** projects.owner_id points at the primary owner; keep it pointing at a current OWNER member. */
    private void syncPrimaryOwner(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        boolean primaryStillOwner = memberRepository.findById(new ProjectMemberId(projectId, project.getOwnerId()))
                .map(member -> member.getRole() == ProjectRole.OWNER)
                .orElse(false);
        if (!primaryStillOwner) {
            memberRepository.findByIdProjectIdAndRoleOrderByJoinedAtAsc(projectId, ProjectRole.OWNER)
                    .stream().findFirst()
                    .ifPresent(owner -> {
                        project.setOwnerId(owner.getUserId());
                        projectRepository.saveAndFlush(project);
                    });
        }
    }
}
