package com.velocira.backend.audit.repository;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.model.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data repository for {@link AuditLogEntity}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

        Page<AuditLogEntity> findByUserId(UUID userId, Pageable pageable);

        Page<AuditLogEntity> findByAction(AuditAction action, Pageable pageable);

        @Query("SELECT a FROM AuditLogEntity a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
        Page<AuditLogEntity> findRecent(@Param("since") Instant since, Pageable pageable);

        @Query("SELECT a FROM AuditLogEntity a WHERE " +
                        "(:userId IS NULL OR a.userId = :userId) AND " +
                        "(:action IS NULL OR a.action = :action) " +
                        "ORDER BY a.createdAt DESC")
        Page<AuditLogEntity> findFiltered(
                        @Param("userId") UUID userId,
                        @Param("action") AuditAction action,
                        Pageable pageable);

        @Query("SELECT a FROM AuditLogEntity a WHERE " +
                        "(:userId IS NULL OR a.userId = :userId) AND " +
                        "(:action IS NULL OR a.action = :action) AND " +
                        "a.createdAt >= :since " +
                        "ORDER BY a.createdAt DESC")
        Page<AuditLogEntity> findFilteredSince(
                        @Param("userId") UUID userId,
                        @Param("action") AuditAction action,
                        @Param("since") Instant since,
                        Pageable pageable);

        long countByAction(AuditAction action);

        @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.createdAt >= :since")
        long countSince(@Param("since") Instant since);
}
