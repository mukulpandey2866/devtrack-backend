package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.enums.ProjectRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsAndRolesTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "title");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void unsortedRequestsUseDefaultSortWithStableTieBreaker() {
        Pageable result = PageRequests.restrict(PageRequest.of(2, 10), ALLOWED, DEFAULT_SORT);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getSort().stream().map(Sort.Order::getProperty)).containsExactly("createdAt", "id");
    }

    @Test
    void whitelistedSortIsKeptAndUnknownSortIsRejected() {
        Pageable ok = PageRequests.restrict(PageRequest.of(0, 5, Sort.by("title")), ALLOWED, DEFAULT_SORT);
        assertThat(ok.getSort().getOrderFor("title")).isNotNull();

        assertThatThrownBy(() -> PageRequests.restrict(PageRequest.of(0, 5, Sort.by("passwordHash")), ALLOWED, DEFAULT_SORT))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void roleHierarchyIsOrderedFromViewerToOwner() {
        assertThat(ProjectRole.OWNER.outranks(ProjectRole.MANAGER)).isTrue();
        assertThat(ProjectRole.MANAGER.outranks(ProjectRole.MANAGER)).isFalse();
        assertThat(ProjectRole.MANAGER.isAtLeast(ProjectRole.MANAGER)).isTrue();
        assertThat(ProjectRole.VIEWER.isAtLeast(ProjectRole.MEMBER)).isFalse();
        assertThat(ProjectRole.MEMBER.isAtLeast(ProjectRole.VIEWER)).isTrue();
    }
}
