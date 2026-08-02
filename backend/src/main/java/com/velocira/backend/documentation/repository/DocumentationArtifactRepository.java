package com.velocira.backend.documentation.repository;

import com.velocira.backend.documentation.model.DocumentationArtifactEntity;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentationArtifactRepository extends JpaRepository<DocumentationArtifactEntity, UUID> {
    List<DocumentationArtifactEntity> findByDocumentationPackageIdOrderByArtifactTypeAsc(UUID packageId);
    Optional<DocumentationArtifactEntity> findByDocumentationPackageIdAndArtifactType(UUID packageId, DocumentationArtifactType artifactType);
}
