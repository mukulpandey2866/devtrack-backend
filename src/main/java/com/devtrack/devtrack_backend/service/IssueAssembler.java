package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.IssueResponse;
import com.devtrack.devtrack_backend.dto.LabelResponse;
import com.devtrack.devtrack_backend.dto.UserSummary;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueAssignee;
import com.devtrack.devtrack_backend.entity.IssueLabel;
import com.devtrack.devtrack_backend.entity.Label;
import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.mapper.IssueMapper;
import com.devtrack.devtrack_backend.mapper.LabelMapper;
import com.devtrack.devtrack_backend.mapper.UserMapper;
import com.devtrack.devtrack_backend.repository.IssueAssigneeRepository;
import com.devtrack.devtrack_backend.repository.IssueLabelRepository;
import com.devtrack.devtrack_backend.repository.LabelRepository;
import com.devtrack.devtrack_backend.repository.ProjectRepository;
import com.devtrack.devtrack_backend.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds issue responses for a whole page with a fixed number of queries (no per-issue lookups). */
@Component
public class IssueAssembler {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;

    public IssueAssembler(ProjectRepository projectRepository, UserRepository userRepository,
                          IssueAssigneeRepository assigneeRepository, IssueLabelRepository issueLabelRepository,
                          LabelRepository labelRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.assigneeRepository = assigneeRepository;
        this.issueLabelRepository = issueLabelRepository;
        this.labelRepository = labelRepository;
    }

    public IssueResponse assemble(Issue issue) {
        return assemble(List.of(issue)).get(0);
    }

    public List<IssueResponse> assemble(List<Issue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        List<UUID> issueIds = issues.stream().map(Issue::getId).toList();
        Set<UUID> projectIds = issues.stream().map(Issue::getProjectId).collect(Collectors.toSet());

        Map<UUID, String> projectKeys = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectKey));
        List<IssueAssignee> assignments = assigneeRepository.findByIdIssueIdIn(issueIds);
        List<IssueLabel> issueLabels = issueLabelRepository.findByIdIssueIdIn(issueIds);

        Set<UUID> userIds = new HashSet<>();
        issues.forEach(issue -> userIds.add(issue.getReporterId()));
        assignments.forEach(assignment -> userIds.add(assignment.getUserId()));
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Label> labels = labelRepository
                .findAllById(issueLabels.stream().map(IssueLabel::getLabelId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Label::getId, Function.identity()));

        Map<UUID, List<UserSummary>> assigneesByIssue = assignments.stream().collect(Collectors.groupingBy(
                IssueAssignee::getIssueId,
                Collectors.mapping(a -> UserMapper.toSummary(users.get(a.getUserId())), Collectors.toList())));
        Map<UUID, List<LabelResponse>> labelsByIssue = issueLabels.stream().collect(Collectors.groupingBy(
                IssueLabel::getIssueId,
                Collectors.mapping(l -> LabelMapper.toResponse(labels.get(l.getLabelId())), Collectors.toList())));

        return issues.stream()
                .map(issue -> IssueMapper.toResponse(
                        issue,
                        projectKeys.get(issue.getProjectId()),
                        UserMapper.toSummary(users.get(issue.getReporterId())),
                        assigneesByIssue.getOrDefault(issue.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(UserSummary::fullName)).toList(),
                        labelsByIssue.getOrDefault(issue.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(LabelResponse::name)).toList()))
                .toList();
    }
}
