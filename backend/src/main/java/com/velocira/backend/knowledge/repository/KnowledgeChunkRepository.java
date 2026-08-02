package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {
    List<KnowledgeChunkEntity> findBySourceIdOrderByChunkOrdinalAsc(UUID sourceId);
    List<KnowledgeChunkEntity> findByProjectIdAndOwnerIdOrderByCreatedAtAsc(UUID projectId, UUID ownerId);
    Optional<KnowledgeChunkEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);
    void deleteBySourceId(UUID sourceId);
}
