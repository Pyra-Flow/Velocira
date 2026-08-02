package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.KnowledgeSourceEntity;
import com.velocira.backend.knowledge.model.KnowledgeSourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSourceEntity, UUID> {
    List<KnowledgeSourceEntity> findByProjectIdAndOwnerIdAndStatusNotOrderByCreatedAtDesc(
            UUID projectId, UUID ownerId, KnowledgeSourceStatus status);
    Optional<KnowledgeSourceEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);
    @Query("""
            select s from KnowledgeSourceEntity s
            where s.project.id = :projectId and s.owner.id = :ownerId and s.status = :status
              and (s.expiresAt is null or s.expiresAt > :now)
            order by s.createdAt asc
            """)
    List<KnowledgeSourceEntity> findCurrentByProjectOwnerAndStatus(
            @Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
            @Param("status") KnowledgeSourceStatus status, @Param("now") Instant now);
}
