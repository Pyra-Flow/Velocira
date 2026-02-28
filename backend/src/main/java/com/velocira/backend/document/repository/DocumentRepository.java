package com.velocira.backend.document.repository;

import com.velocira.backend.document.model.DocumentEntity;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.model.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentEntity}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

        /**
         * Finds all documents for a project with optional status and type filters.
         *
         * @param projectId the project UUID
         * @param status    optional status filter
         * @param type      optional type filter
         * @param pageable  pagination parameters
         * @return page of matching documents
         */
        @Query("""
                        SELECT d FROM DocumentEntity d
                        WHERE d.project.id = :projectId
                          AND (:status IS NULL OR d.status = :status)
                          AND (:type IS NULL OR d.type = :type)
                        """)
        Page<DocumentEntity> findByProjectFiltered(
                        @Param("projectId") UUID projectId,
                        @Param("status") DocumentStatus status,
                        @Param("type") DocumentType type,
                        Pageable pageable);

        /**
         * Finds all documents for a project ordered by type.
         */
        List<DocumentEntity> findByProjectIdOrderByTypeAsc(UUID projectId);

        /**
         * Finds a specific document by project ID and type.
         */
        Optional<DocumentEntity> findByProjectIdAndType(UUID projectId, DocumentType type);

        /**
         * Counts documents in a project.
         */
        long countByProjectId(UUID projectId);

        /**
         * Counts documents by status in a project.
         */
        long countByProjectIdAndStatus(UUID projectId, DocumentStatus status);

        /**
         * Checks if a document of a given type exists for a project.
         */
        boolean existsByProjectIdAndType(UUID projectId, DocumentType type);

        /**
         * Counts total documents across all projects (for admin analytics).
         */
        @Query("SELECT COUNT(d) FROM DocumentEntity d")
        long countAll();

        /**
         * Counts documents by status (for admin analytics).
         */
        @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.status = :status")
        long countByStatus(@Param("status") DocumentStatus status);
}
