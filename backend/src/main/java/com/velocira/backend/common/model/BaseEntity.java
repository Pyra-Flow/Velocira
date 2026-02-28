package com.velocira.backend.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base entity providing common audit fields for all JPA entities.
 *
 * <p>
 * Every entity in the Velocira platform inherits:
 * </p>
 * <ul>
 * <li>{@code id} — UUID primary key (auto-generated)</li>
 * <li>{@code createdAt} — creation timestamp (set once, immutable)</li>
 * <li>{@code updatedAt} — last modification timestamp (auto-updated)</li>
 * </ul>
 *
 * <p>
 * Requires
 * {@link org.springframework.data.jpa.repository.config.EnableJpaAuditing}
 * to be enabled in the application configuration.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * Universally unique identifier for the entity.
     * Auto-generated using PostgreSQL's {@code gen_random_uuid()} or JPA.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Timestamp when the entity was first persisted.
     * Set automatically by JPA auditing; never updated afterward.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the most recent update to the entity.
     * Updated automatically by JPA auditing on every save.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
