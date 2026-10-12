package com.devtrack.devtrack_backend.mapper;

import com.devtrack.devtrack_backend.dto.CommentResponse;
import com.devtrack.devtrack_backend.dto.IssueHistoryResponse;
import com.devtrack.devtrack_backend.dto.IssueResponse;
import com.devtrack.devtrack_backend.dto.LabelResponse;
import com.devtrack.devtrack_backend.dto.UserSummary;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueComment;
import com.devtrack.devtrack_backend.entity.IssueHistory;

import java.util.List;

public final class IssueMapper {

    private IssueMapper() {
    }

    public static IssueResponse toResponse(Issue issue, String projectKey, UserSummary reporter,
                                           List<UserSummary> assignees, List<LabelResponse> labels) {
        return new IssueResponse(issue.getId(), issue.getProjectId(), issueKey(projectKey, issue.getIssueNumber()),
                issue.getIssueNumber(), issue.getTitle(), issue.getDescription(), issue.getIssueType(),
                issue.getStatus(), issue.getPriority(), reporter, assignees, labels, issue.getDueDate(),
                issue.getEstimatedHours(), issue.getCreatedAt(), issue.getUpdatedAt());
    }

    public static CommentResponse toCommentResponse(IssueComment comment, UserSummary author) {
        return new CommentResponse(comment.getId(), comment.getIssueId(), author, comment.getContent(),
                comment.getCreatedAt(), comment.getUpdatedAt());
    }

    public static IssueHistoryResponse toHistoryResponse(IssueHistory history, UserSummary changedBy) {
        return new IssueHistoryResponse(history.getId(), history.getIssueId(), changedBy, history.getFieldName(),
                history.getOldValue(), history.getNewValue(), history.getChangedAt());
    }

    public static String issueKey(String projectKey, int issueNumber) {
        return projectKey + "-" + issueNumber;
    }
}
