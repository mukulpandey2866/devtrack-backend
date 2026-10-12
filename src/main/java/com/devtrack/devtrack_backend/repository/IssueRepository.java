package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.Issue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    @Query("select coalesce(max(i.issueNumber), 0) from Issue i where i.projectId = :projectId")
    int findMaxIssueNumber(@Param("projectId") UUID projectId);

    @Query("select i.projectId from Issue i where i.id = :id")
    Optional<UUID> findProjectIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :id")
    Optional<Issue> findByIdForUpdate(@Param("id") UUID id);
}
