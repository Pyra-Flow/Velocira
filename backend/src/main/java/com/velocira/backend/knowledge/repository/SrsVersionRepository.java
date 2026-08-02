package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.SrsVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SrsVersionRepository extends JpaRepository<SrsVersionEntity, UUID> {
    List<SrsVersionEntity> findByProjectIdAndOwnerIdOrderByVersionNumberDesc(UUID projectId, UUID ownerId);
    Optional<SrsVersionEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);
    @Query("select coalesce(max(v.versionNumber), 0) from SrsVersionEntity v where v.project.id = :projectId")
    int nextVersionBase(@Param("projectId") UUID projectId);
}
