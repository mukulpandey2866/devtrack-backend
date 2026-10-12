package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.IssueAssignee;
import com.devtrack.devtrack_backend.entity.IssueAssigneeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IssueAssigneeRepository extends JpaRepository<IssueAssignee, IssueAssigneeId> {

    List<IssueAssignee> findByIdIssueIdOrderByAssignedAtAsc(UUID issueId);

    List<IssueAssignee> findByIdIssueIdIn(Collection<UUID> issueIds);

    @Query("""
            select a.id.issueId from IssueAssignee a
            where a.id.userId = :userId
              and a.id.issueId in (select i.id from Issue i where i.projectId = :projectId)
            """)
    List<UUID> findIssueIdsAssignedToUserInProject(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IssueAssignee a where a.id.issueId = :issueId")
    void deleteByIssueId(@Param("issueId") UUID issueId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IssueAssignee a where a.id.issueId in :issueIds and a.id.userId = :userId")
    void deleteByUserIdAndIssueIds(@Param("userId") UUID userId, @Param("issueIds") Collection<UUID> issueIds);
}
