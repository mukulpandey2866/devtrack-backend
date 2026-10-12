package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/** Restricts client-supplied sorting to known properties and makes paging order deterministic. */
final class PageRequests {

    private PageRequests() {
    }

    static Pageable restrict(Pageable pageable, Set<String> allowedSortProperties, Sort defaultSort) {
        Sort requested = pageable.getSort();
        for (Sort.Order order : requested) {
            if (!allowedSortProperties.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Unsupported sort property; allowed: " + new TreeSet<>(allowedSortProperties));
            }
        }
        Sort sort = requested.isUnsorted() ? defaultSort : requested;
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
