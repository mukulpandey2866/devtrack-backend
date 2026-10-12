package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.IssueLabel;
import com.devtrack.devtrack_backend.entity.IssueLabelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, IssueLabelId> {

    List<IssueLabel> findByIdIssueId(UUID issueId);

    List<IssueLabel> findByIdLabelId(UUID labelId);

    List<IssueLabel> findByIdIssueIdIn(Collection<UUID> issueIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IssueLabel l where l.id.issueId = :issueId and l.id.labelId in :labelIds")
    void deleteByIssueIdAndLabelIds(@Param("issueId") UUID issueId, @Param("labelIds") Collection<UUID> labelIds);
}
