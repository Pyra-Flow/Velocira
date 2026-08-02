package com.velocira.backend.project.repository;

import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

/**
 * Spring Data repository for {@link ProjectEntity}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

        Page<ProjectEntity> findByOwnerId(UUID ownerId, Pageable pageable);

        /**
         * Explicit query shapes avoid PostgreSQL inferring a nullable search
         * parameter as bytea inside LOWER(:search). The service selects the
         * appropriate method for the requested filter combination.
         */
        Page<ProjectEntity> findByOwnerIdAndStatusNot(UUID ownerId, ProjectStatus status, Pageable pageable);

        Page<ProjectEntity> findByOwnerIdAndStatusNotAndNameContainingIgnoreCase(
                        UUID ownerId, ProjectStatus status, String search, Pageable pageable);

        Page<ProjectEntity> findByOwnerIdAndStatus(UUID ownerId, ProjectStatus status, Pageable pageable);

        Page<ProjectEntity> findByOwnerIdAndStatusAndNameContainingIgnoreCase(
                        UUID ownerId, ProjectStatus status, String search, Pageable pageable);

        Optional<ProjectEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

        /** Serializes creation of the single discovery session allowed per project. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM ProjectEntity p WHERE p.id = :id AND p.owner.id = :ownerId")
        Optional<ProjectEntity> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

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
