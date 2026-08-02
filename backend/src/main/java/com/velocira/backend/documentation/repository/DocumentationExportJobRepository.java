package com.velocira.backend.documentation.repository;

import com.velocira.backend.documentation.model.DocumentationExportJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentationExportJobRepository extends JpaRepository<DocumentationExportJobEntity, UUID> {
    List<DocumentationExportJobEntity> findByDocumentationPackageIdAndOwnerIdOrderByCreatedAtDesc(UUID packageId, UUID ownerId);
    Optional<DocumentationExportJobEntity> findByIdAndDocumentationPackageIdAndOwnerId(UUID id, UUID packageId, UUID ownerId);
}
