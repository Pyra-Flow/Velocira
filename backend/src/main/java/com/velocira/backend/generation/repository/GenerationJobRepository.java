package com.velocira.backend.generation.repository;

import com.velocira.backend.generation.model.GenerationJobEntity;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.document.model.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence access for durable, owner-scoped generation jobs. */
@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJobEntity, UUID> {

    /** Resolves a repeated client POST without creating duplicate provider work. */
    Optional<GenerationJobEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    /** Ownership-safe lookup used by project-scoped API endpoints. */
    Optional<GenerationJobEntity> findByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);

    /** Page of a user's jobs for one project, newest first when pageable requests it. */
    Page<GenerationJobEntity> findByProjectIdAndOwnerId(UUID projectId, UUID ownerId, Pageable pageable);

    Page<GenerationJobEntity> findByProjectIdAndOwnerIdAndStatus(
            UUID projectId, UUID ownerId, GenerationJobStatus status, Pageable pageable);

    long countByProjectIdAndStatusIn(UUID projectId, Collection<GenerationJobStatus> statuses);

    Optional<GenerationJobEntity> findFirstByProjectIdAndRequestedDocumentTypeAndStatusInOrderByCreatedAtDesc(
            UUID projectId, DocumentType requestedDocumentType, Collection<GenerationJobStatus> statuses);

    /** Used by per-account active-job guardrails. */
    long countByOwnerIdAndStatusIn(UUID ownerId, Collection<GenerationJobStatus> statuses);

    /** Supports queue-depth metrics without loading individual job rows. */
    long countByStatusIn(Collection<GenerationJobStatus> statuses);

    long countByStatus(GenerationJobStatus status);

    /**
     * Locks a specific job while a worker claims or completes it. Optimistic
     * locking on the entity remains the second line of defence.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM GenerationJobEntity j WHERE j.id = :id")
    Optional<GenerationJobEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns jobs a worker may claim. A queue consumer must still lock each
     * candidate before moving it out of QUEUED/FAILED to avoid double work.
     */
    @Query("""
            SELECT j FROM GenerationJobEntity j
            WHERE j.status IN :statuses
              AND j.cancelRequested = false
              AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now)
            ORDER BY j.queuedAt ASC, j.createdAt ASC
            """)
    List<GenerationJobEntity> findClaimableJobs(
            @Param("statuses") Collection<GenerationJobStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    /** Finds jobs interrupted while a worker was actively processing them. */
    @Query("""
            SELECT j FROM GenerationJobEntity j
            WHERE j.status IN :statuses
              AND j.updatedAt < :before
            ORDER BY j.updatedAt ASC
            """)
    List<GenerationJobEntity> findStaleActiveJobs(
            @Param("statuses") Collection<GenerationJobStatus> statuses,
            @Param("before") Instant before,
            Pageable pageable);
}
