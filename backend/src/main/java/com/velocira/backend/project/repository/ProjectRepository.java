package com.velocira.backend.project.repository;

import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ProjectEntity}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

        Page<ProjectEntity> findByOwnerId(UUID ownerId, Pageable pageable);

        @Query(value = "SELECT p.* FROM projects p WHERE p.owner_id = :ownerId " +
                        "AND (CAST(:status AS VARCHAR) IS NULL OR p.status = CAST(:status AS VARCHAR)) " +
                        "AND (CAST(:search AS VARCHAR) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) "
                        +
                        "ORDER BY p.created_at DESC", countQuery = "SELECT COUNT(*) FROM projects p WHERE p.owner_id = :ownerId "
                                        +
                                        "AND (CAST(:status AS VARCHAR) IS NULL OR p.status = CAST(:status AS VARCHAR)) "
                                        +
                                        "AND (CAST(:search AS VARCHAR) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')))", nativeQuery = true)
        Page<ProjectEntity> findByOwnerFiltered(
                        @Param("ownerId") UUID ownerId,
                        @Param("status") String status,
                        @Param("search") String search,
                        Pageable pageable);

        Optional<ProjectEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

        long countByOwnerId(UUID ownerId);

        long countByStatus(ProjectStatus status);

        @Query("SELECT COUNT(p) FROM ProjectEntity p")
        long countAll();

        @Query("SELECT p.type, COUNT(p) FROM ProjectEntity p GROUP BY p.type")
        java.util.List<Object[]> countByType();

        boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

        /**
         * Counts projects created since a given timestamp (for admin analytics).
         */
        @Query("SELECT COUNT(p) FROM ProjectEntity p WHERE p.createdAt >= :since")
        long countCreatedSince(@Param("since") java.time.Instant since);
}
