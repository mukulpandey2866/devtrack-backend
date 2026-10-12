package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.enums.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByProjectKey(String projectKey);

    /** Row lock used to serialise issue-number allocation and project-level changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select p from Project p
            where p.status in :statuses
              and exists (select 1 from ProjectMember m
                          where m.id.projectId = p.id and m.id.userId = :userId)
            """)
    Page<Project> findAccessible(@Param("userId") UUID userId,
                                 @Param("statuses") Collection<ProjectStatus> statuses,
                                 Pageable pageable);
}
