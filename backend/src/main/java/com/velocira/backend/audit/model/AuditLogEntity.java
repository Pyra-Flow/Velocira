package com.velocira.backend.audit.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Persistent audit log entry.
 *
 * <p>
 * Every significant action in the platform (login, project creation,
 * admin operations, etc.) is recorded as an {@code AuditLogEntity} for
 * compliance, debugging, and analytics.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_logs_action", columnList = "action"),
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = true;
}
