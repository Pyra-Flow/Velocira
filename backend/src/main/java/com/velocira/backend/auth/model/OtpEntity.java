package com.velocira.backend.auth.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a one-time password (OTP) for email verification or
 * password reset.
 *
 * <p>
 * Security measures:
 * </p>
 * <ul>
 * <li>OTP codes are stored as <strong>SHA-256 hashes</strong> — never plain
 * text</li>
 * <li>Each OTP has a configurable expiry (default: 10 minutes)</li>
 * <li>Failed validation attempts are tracked to prevent brute-force
 * attacks</li>
 * <li>OTPs are single-use — consumed after successful validation</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 * @see OtpType
 */
@Entity
@Table(name = "otp_tokens", indexes = {
        @Index(name = "idx_otp_user_type", columnList = "user_id, otp_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpEntity extends BaseEntity {

    /** The user this OTP belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * SHA-256 hash of the OTP code.
     * <p>
     * The plain OTP is only sent via email and never stored.
     * </p>
     */
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    /** The purpose of this OTP (email verification or password reset). */
    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false, length = 30)
    private OtpType otpType;

    /** When this OTP expires. OTPs are not usable past this time. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Whether this OTP has already been used. */
    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    /** Number of failed validation attempts against this OTP. */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /**
     * Checks whether this OTP is expired.
     *
     * @return {@code true} if the OTP's expiry time has passed
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks whether this OTP is still valid (not expired, not used).
     *
     * @return {@code true} if the OTP can still be validated
     */
    public boolean isValid() {
        return !used && !isExpired();
    }

    /**
     * Increments the failed attempt counter.
     */
    public void incrementAttempts() {
        this.attempts++;
    }
}
