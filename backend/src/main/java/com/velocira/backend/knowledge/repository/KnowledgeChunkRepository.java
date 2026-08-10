package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {
    interface SourceChunkCount {
        UUID getSourceId();
        long getChunkCount();
    }

    List<KnowledgeChunkEntity> findBySourceIdOrderByChunkOrdinalAsc(UUID sourceId);
    List<KnowledgeChunkEntity> findByProjectIdAndOwnerIdOrderByCreatedAtAsc(UUID projectId, UUID ownerId);
    Optional<KnowledgeChunkEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);

    @Query("""
            select chunk.source.id as sourceId, count(chunk) as chunkCount
            from KnowledgeChunkEntity chunk
            where chunk.source.id in :sourceIds
            group by chunk.source.id
            """)
    List<SourceChunkCount> countBySourceIds(@Param("sourceIds") List<UUID> sourceIds);

    void deleteBySourceId(UUID sourceId);
}
