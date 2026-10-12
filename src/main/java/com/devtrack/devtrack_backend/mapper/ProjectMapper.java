package com.devtrack.devtrack_backend.mapper;

import com.devtrack.devtrack_backend.dto.MemberResponse;
import com.devtrack.devtrack_backend.dto.ProjectResponse;
import com.devtrack.devtrack_backend.entity.Project;
import com.devtrack.devtrack_backend.entity.ProjectMember;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.ProjectRole;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectResponse toResponse(Project project, ProjectRole currentUserRole) {
        return new ProjectResponse(project.getId(), project.getName(), project.getProjectKey(),
                project.getDescription(), project.getStatus(), project.getOwnerId(), currentUserRole,
                project.getCreatedAt(), project.getUpdatedAt());
    }

    public static MemberResponse toMemberResponse(ProjectMember member, User user) {
        return new MemberResponse(UserMapper.toSummary(user), member.getRole(), member.getJoinedAt());
    }
}
