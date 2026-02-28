package com.velocira.backend.auth.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a refresh token for JWT-based authentication.
 *
 * <p>
 * Design decisions for production-grade token management:
 * </p>
 * <ul>
 * <li>Tokens are stored as <strong>SHA-256 hashes</strong> — never plain
 * text</li>
 * <li>Each token is tied to a specific device/session via
 * {@code deviceInfo}</li>
 * <li><strong>Token rotation</strong>: each refresh generates a new token and
 * invalidates the old one</li>
 * <li>The {@code revoked} flag supports immediate token invalidation on
 * logout</li>
 * <li>The {@code replacedByToken} field creates an audit chain for
 * rotation</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 * @see UserEntity
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity extends BaseEntity {

    /**
     * The user who owns this refresh token.
     * Loaded lazily to avoid unnecessary joins.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * SHA-256 hash of the actual refresh token string.
     * <p>
     * The plain token is only sent to the client and never stored server-side.
     * </p>
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** When this refresh token expires. Tokens are not usable past this time. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Whether this token has been explicitly revoked (e.g., on logout). */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /**
     * Hash of the token that replaced this one during rotation.
     * <p>
     * Creates an audit trail: if a revoked token is reused, we can detect
     * potential token theft and invalidate the entire chain.
     * </p>
     */
    @Column(name = "replaced_by_token", length = 64)
    private String replacedByToken;

    /**
     * Device or client information (User-Agent, IP, etc.) for session management.
     */
    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    /** IP address of the client that created this token. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Checks whether this refresh token is expired.
     *
     * @return {@code true} if the token's expiry time has passed
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks whether this refresh token is usable (not expired and not revoked).
     *
     * @return {@code true} if the token can still be used
     */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
