package com.velocira.backend.auth.repository;

import com.velocira.backend.auth.model.OtpEntity;
import com.velocira.backend.auth.model.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OtpEntity} persistence operations.
 *
 * <p>
 * Manages OTP lifecycle: creation, validation, rate-limit checks, and cleanup.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface OtpRepository extends JpaRepository<OtpEntity, UUID> {

    /**
     * Finds the most recent valid (unused, non-expired) OTP for a user and type.
     *
     * @param userId  the user's ID
     * @param otpType the type of OTP
     * @param now     the current instant (to filter expired tokens)
     * @return an {@link Optional} containing the latest valid OTP
     */
    @Query("SELECT o FROM OtpEntity o WHERE o.user.id = :userId AND o.otpType = :otpType " +
            "AND o.used = false AND o.expiresAt > :now ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpEntity> findLatestValidOtp(
            @Param("userId") UUID userId,
            @Param("otpType") OtpType otpType,
            @Param("now") Instant now);

    /**
     * Counts OTPs sent to a user within a time window (for rate limiting).
     *
     * @param userId  the user's ID
     * @param otpType the type of OTP
     * @param since   the start of the time window
     * @return the number of OTPs sent in the window
     */
    @Query("SELECT COUNT(o) FROM OtpEntity o WHERE o.user.id = :userId AND o.otpType = :otpType " +
            "AND o.createdAt > :since")
    long countRecentOtps(
            @Param("userId") UUID userId,
            @Param("otpType") OtpType otpType,
            @Param("since") Instant since);

    /**
     * Invalidates all unused OTPs for a user and type (called before creating a new
     * OTP).
     *
     * @param userId  the user's ID
     * @param otpType the type of OTP
     */
    @Modifying
    @Query("UPDATE OtpEntity o SET o.used = true WHERE o.user.id = :userId AND o.otpType = :otpType AND o.used = false")
    void invalidateAllByUserIdAndType(
            @Param("userId") UUID userId,
            @Param("otpType") OtpType otpType);

    /**
     * Deletes expired OTPs older than the cutoff for database hygiene.
     *
     * @param cutoff OTPs expiring before this instant will be deleted
     */
    @Modifying
    @Query("DELETE FROM OtpEntity o WHERE o.expiresAt < :cutoff")
    void deleteExpired(@Param("cutoff") Instant cutoff);
}
