package com.velocira.backend.generation.repository;

import com.velocira.backend.generation.model.ArtifactVersionEntity;
import com.velocira.backend.document.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence access for immutable generated artifact versions. */
@Repository
public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersionEntity, UUID> {

    Optional<ArtifactVersionEntity> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ArtifactVersionEntity> findByGenerationJobId(UUID generationJobId);

    List<ArtifactVersionEntity> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<ArtifactVersionEntity> findFirstByProjectIdAndArtifactTypeOrderByVersionNumberDesc(
            UUID projectId, DocumentType artifactType);

    long countByProjectId(UUID projectId);
}
