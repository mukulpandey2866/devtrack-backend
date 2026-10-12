package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.IssueComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

    Page<IssueComment> findByIssueId(UUID issueId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IssueComment c where c.issueId = :issueId")
    void deleteByIssueId(@Param("issueId") UUID issueId);
}
