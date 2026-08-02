package com.velocira.backend.generation.repository;

import com.velocira.backend.generation.model.GenerationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence access for individual provider execution attempts. */
@Repository
public interface GenerationRunRepository extends JpaRepository<GenerationRunEntity, UUID> {

    List<GenerationRunEntity> findByGenerationJobIdOrderByAttemptNumberDesc(UUID generationJobId);

    Optional<GenerationRunEntity> findByGenerationJobIdAndAttemptNumber(UUID generationJobId, int attemptNumber);

    Optional<GenerationRunEntity> findFirstByGenerationJobIdOrderByAttemptNumberDesc(UUID generationJobId);

    long countByGenerationJobId(UUID generationJobId);

    /** Actual provider cost for a user over a time window, for cost dashboards/guards. */
    @Query("""
            SELECT COALESCE(SUM(r.costUsd), 0)
            FROM GenerationRunEntity r
            WHERE r.generationJob.owner.id = :ownerId
              AND r.createdAt >= :since
            """)
    BigDecimal sumCostUsdByOwnerSince(@Param("ownerId") UUID ownerId, @Param("since") Instant since);
}
