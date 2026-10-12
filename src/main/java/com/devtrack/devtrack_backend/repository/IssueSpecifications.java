package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.dto.IssueFilter;
import com.devtrack.devtrack_backend.entity.Issue;
import com.devtrack.devtrack_backend.entity.IssueAssignee;
import com.devtrack.devtrack_backend.entity.IssueLabel;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class IssueSpecifications {

    private IssueSpecifications() {
    }

    public static Specification<Issue> forProject(UUID projectId, IssueFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.priority() != null && !filter.priority().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priority()));
            }
            if (filter.type() != null && !filter.type().isEmpty()) {
                predicates.add(root.get("issueType").in(filter.type()));
            }
            if (filter.reporterId() != null) {
                predicates.add(cb.equal(root.get("reporterId"), filter.reporterId()));
            }
            if (filter.dueBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueBefore()));
            }
            if (filter.dueAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueAfter()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String escaped = filter.q().trim().toLowerCase()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'));
            }
            if (filter.assigneeId() != null) {
                Subquery<Integer> assigned = query.subquery(Integer.class);
                Root<IssueAssignee> assignee = assigned.from(IssueAssignee.class);
                assigned.select(cb.literal(1)).where(
                        cb.equal(assignee.get("id").get("issueId"), root.get("id")),
                        cb.equal(assignee.get("id").get("userId"), filter.assigneeId()));
                predicates.add(cb.exists(assigned));
            }
            if (filter.labelId() != null) {
                Subquery<Integer> labelled = query.subquery(Integer.class);
                Root<IssueLabel> label = labelled.from(IssueLabel.class);
                labelled.select(cb.literal(1)).where(
                        cb.equal(label.get("id").get("issueId"), root.get("id")),
                        cb.equal(label.get("id").get("labelId"), filter.labelId()));
                predicates.add(cb.exists(labelled));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
