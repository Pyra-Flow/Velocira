package com.velocira.backend.audit.service;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.model.AuditLogEntity;
import com.velocira.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for recording and querying audit log entries.
 *
 * <p>
 * Audit writes are executed asynchronously in a separate transaction
 * so they never interfere with the calling business operation.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit event asynchronously.
     *
     * @param userId     the acting user's ID (may be {@code null} for
     *                   unauthenticated actions)
     * @param email      the acting user's email
     * @param action     the action being audited
     * @param details    free-text detail string (max 1000 chars)
     * @param ipAddress  client IP address
     * @param deviceInfo user-agent / device info
     * @param success    whether the action succeeded
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String email, AuditAction action,
            String details, String ipAddress, String deviceInfo, boolean success) {
        try {
            AuditLogEntity entry = AuditLogEntity.builder()
                    .userId(userId)
                    .email(email)
                    .action(action)
                    .details(truncate(details, 1000))
                    .ipAddress(ipAddress)
                    .deviceInfo(truncate(deviceInfo, 512))
                    .success(success)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit recorded: action={}, user={}, success={}", action, email, success);
        } catch (Exception ex) {
            log.error("Failed to record audit entry: action={}, user={}, error={}",
                    action, email, ex.getMessage(), ex);
        }
    }

    /**
     * Convenience overload for successful actions without device/IP context.
     */
    public void record(UUID userId, String email, AuditAction action, String details) {
        record(userId, email, action, details, null, null, true);
    }

    /**
     * Queries audit logs with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogEntity> findFiltered(UUID userId, AuditAction action,
            Instant since, Pageable pageable) {
        return auditLogRepository.findFiltered(userId, action, since, pageable);
    }

    /**
     * Returns all audit logs for a specific user, newest first.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogEntity> findByUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    private String truncate(String value, int maxLength) {
        if (value == null)
            return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
