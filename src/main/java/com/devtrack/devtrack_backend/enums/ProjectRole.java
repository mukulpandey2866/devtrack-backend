package com.devtrack.devtrack_backend.enums;

/** Declared from least to most privileged; {@link #rank} drives the role hierarchy. */
public enum ProjectRole {
    VIEWER(1),
    MEMBER(2),
    MANAGER(3),
    OWNER(4);

    private final int rank;

    ProjectRole(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(ProjectRole other) {
        return rank >= other.rank;
    }

    public boolean outranks(ProjectRole other) {
        return rank > other.rank;
    }
}
