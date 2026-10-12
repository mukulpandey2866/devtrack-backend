package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.LabelRequest;
import com.devtrack.devtrack_backend.dto.LabelResponse;
import com.devtrack.devtrack_backend.dto.SetIssueLabelsRequest;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueLabel;
import com.devtrack.devtrack_backend.entity.Label;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.LabelMapper;
import com.devtrack.devtrack_backend.repository.IssueLabelRepository;
import com.devtrack.devtrack_backend.repository.IssueRepository;
import com.devtrack.devtrack_backend.repository.LabelRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Labels are project-scoped; managing the label catalogue needs MANAGER, attaching labels needs MEMBER. */
@Service
public class LabelService {

    private static final String DEFAULT_COLOR = "#808080";
    private static final String NAME_TAKEN = "A label with this name already exists in the project";
    private static final String HISTORY_FIELD = "label";

    private final LabelRepository labelRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueRepository issueRepository;
    private final ProjectAccessService accessService;
    private final IssueHistoryRecorder historyRecorder;

    public LabelService(LabelRepository labelRepository, IssueLabelRepository issueLabelRepository,
                        IssueRepository issueRepository, ProjectAccessService accessService,
                        IssueHistoryRecorder historyRecorder) {
        this.labelRepository = labelRepository;
        this.issueLabelRepository = issueLabelRepository;
        this.issueRepository = issueRepository;
        this.accessService = accessService;
        this.historyRecorder = historyRecorder;
    }

    @Transactional
    public LabelResponse create(UUID actorId, UUID projectId, LabelRequest request) {
        accessService.require(projectId, actorId).atLeast(ProjectRole.MANAGER).writable();
        String name = request.name().trim();
        if (labelRepository.findByProjectIdAndNameIgnoreCase(projectId, name).isPresent()) {
            throw new ConflictException(NAME_TAKEN);
        }
        try {
            return LabelMapper.toResponse(labelRepository.saveAndFlush(new Label(projectId, name, normalizeColor(request.color()))));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(NAME_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> list(UUID actorId, UUID projectId) {
        accessService.require(projectId, actorId);
        return labelRepository.findByProjectIdOrderByNameAsc(projectId).stream().map(LabelMapper::toResponse).toList();
    }

    @Transactional
    public LabelResponse update(UUID actorId, UUID labelId, LabelRequest request) {
        Label label = findLabel(labelId);
        accessService.require(label.getProjectId(), actorId).atLeast(ProjectRole.MANAGER).writable();

        String name = request.name().trim();
        labelRepository.findByProjectIdAndNameIgnoreCase(label.getProjectId(), name)
                .filter(existing -> !existing.getId().equals(labelId))
                .ifPresent(existing -> {
                    throw new ConflictException(NAME_TAKEN);
                });
        label.setName(name);
        label.setColor(normalizeColor(request.color()));
        try {
            return LabelMapper.toResponse(labelRepository.saveAndFlush(label));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(NAME_TAKEN);
        }
    }

    @Transactional
    public void delete(UUID actorId, UUID labelId) {
        Label label = findLabel(labelId);
        accessService.require(label.getProjectId(), actorId).atLeast(ProjectRole.MANAGER).writable();

        // Detaching from issues is a meaningful change on those issues, so it is recorded in their history.
        List<IssueLabel> links = issueLabelRepository.findByIdLabelId(labelId);
        links.forEach(link -> historyRecorder.record(link.getIssueId(), actorId, HISTORY_FIELD, label.getName(), null));
        // issue_labels rows are removed by the ON DELETE CASCADE foreign key.
        labelRepository.delete(label);
        labelRepository.flush();
    }

    @Transactional
    public List<LabelResponse> setIssueLabels(UUID actorId, UUID issueId, SetIssueLabelsRequest request) {
        UUID projectId = issueRepository.findProjectIdById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        accessService.require(projectId, actorId).atLeast(ProjectRole.MEMBER).writable();
        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));

        Map<UUID, Label> requested = labelRepository.findByProjectIdAndIdIn(projectId, request.labelIds()).stream()
                .collect(Collectors.toMap(Label::getId, Function.identity()));
        if (requested.size() != request.labelIds().size()) {
            throw new BadRequestException("Every label must exist in the same project as the issue");
        }

        List<IssueLabel> current = issueLabelRepository.findByIdIssueId(issue.getId());
        Set<UUID> currentIds = current.stream().map(IssueLabel::getLabelId).collect(Collectors.toSet());

        Set<UUID> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(request.labelIds());
        Set<UUID> toAdd = new HashSet<>(request.labelIds());
        toAdd.removeAll(currentIds);

        Map<UUID, Label> labelsById = labelRepository.findAllById(currentIds).stream()
                .collect(Collectors.toMap(Label::getId, Function.identity()));
        if (!toRemove.isEmpty()) {
            issueLabelRepository.deleteByIssueIdAndLabelIds(issueId, toRemove);
            toRemove.forEach(id -> historyRecorder.record(issueId, actorId, HISTORY_FIELD, labelsById.get(id).getName(), null));
        }
        if (!toAdd.isEmpty()) {
            issueLabelRepository.saveAll(toAdd.stream().map(id -> new IssueLabel(issueId, id, projectId)).toList());
            issueLabelRepository.flush();
            toAdd.forEach(id -> historyRecorder.record(issueId, actorId, HISTORY_FIELD, null, requested.get(id).getName()));
        }
        return requested.values().stream()
                .sorted(Comparator.comparing(Label::getName))
                .map(LabelMapper::toResponse)
                .toList();
    }

    private Label findLabel(UUID labelId) {
        return labelRepository.findById(labelId).orElseThrow(() -> new ResourceNotFoundException("Label not found"));
    }

    private static String normalizeColor(String color) {
        return color == null || color.isBlank() ? DEFAULT_COLOR : color.toUpperCase(Locale.ROOT);
    }
}
