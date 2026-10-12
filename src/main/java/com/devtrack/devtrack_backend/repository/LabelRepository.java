package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByProjectIdOrderByNameAsc(UUID projectId);

    List<Label> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    Optional<Label> findByProjectIdAndNameIgnoreCase(UUID projectId, String name);
}
