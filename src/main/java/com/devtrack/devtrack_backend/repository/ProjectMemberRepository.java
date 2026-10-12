package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.ProjectMember;
import com.devtrack.devtrack_backend.entity.ProjectMemberId;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByIdProjectIdOrderByJoinedAtAsc(UUID projectId);

    List<ProjectMember> findByIdUserIdAndIdProjectIdIn(UUID userId, Collection<UUID> projectIds);

    List<ProjectMember> findByIdProjectIdAndRoleOrderByJoinedAtAsc(UUID projectId, ProjectRole role);

    long countByIdProjectIdAndRole(UUID projectId, ProjectRole role);
}
