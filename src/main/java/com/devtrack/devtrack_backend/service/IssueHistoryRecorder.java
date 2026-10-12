package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.entity.IssueHistory;
import com.devtrack.devtrack_backend.repository.IssueHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** Only writes within the caller's transaction, so a change and its history commit or roll back together. */
@Component
public class IssueHistoryRecorder {

    private final IssueHistoryRepository historyRepository;

    public IssueHistoryRecorder(IssueHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID issueId, UUID actorId, String fieldName, Object oldValue, Object newValue) {
        String oldText = oldValue == null ? null : oldValue.toString();
        String newText = newValue == null ? null : newValue.toString();
        if (Objects.equals(oldText, newText)) {
            return;
        }
        historyRepository.save(new IssueHistory(issueId, actorId, fieldName, oldText, newText));
    }
}
