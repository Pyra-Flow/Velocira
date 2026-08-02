package com.velocira.backend.documentation.repository;

import com.velocira.backend.documentation.model.DocumentationTraceLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentationTraceLinkRepository extends JpaRepository<DocumentationTraceLinkEntity, UUID> {
    List<DocumentationTraceLinkEntity> findByDocumentationPackageIdOrderByRequirementKeyAsc(UUID packageId);
}
