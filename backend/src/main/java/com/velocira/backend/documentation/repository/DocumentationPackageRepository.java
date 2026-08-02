package com.velocira.backend.documentation.repository;

import com.velocira.backend.documentation.model.DocumentationPackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentationPackageRepository extends JpaRepository<DocumentationPackageEntity, UUID> {
    List<DocumentationPackageEntity> findByProjectIdAndOwnerIdOrderByVersionNumberDesc(UUID projectId, UUID ownerId);
    Optional<DocumentationPackageEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);
    @Query("SELECT COALESCE(MAX(p.versionNumber), 0) FROM DocumentationPackageEntity p WHERE p.project.id = :projectId")
    int nextVersionBase(UUID projectId);
}
