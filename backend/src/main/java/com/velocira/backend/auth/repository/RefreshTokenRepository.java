package com.velocira.backend.auth.repository;

import com.velocira.backend.auth.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RefreshTokenEntity} persistence
 * operations.
 *
 * <p>
 * Handles refresh token lifecycle: creation, lookup, rotation, and revocation.
 * All token lookups are performed by hash — plain tokens are never stored.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Finds a refresh token by its SHA-256 hash.
     *
     * @param tokenHash the SHA-256 hash of the refresh token
     * @return an {@link Optional} containing the token entity if found
     */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Revokes all refresh tokens for a specific user (logout from all devices).
     *
     * @param userId the user's ID
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Deletes all expired or revoked refresh tokens older than the cutoff.
     * <p>
     * Should be called periodically (e.g., via scheduled task) to clean up stale
     * tokens.
     * </p>
     *
     * @param cutoff tokens created before this instant will be deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.revoked = true OR rt.expiresAt < :cutoff")
    void deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);

    /**
     * Counts active (non-revoked, non-expired) sessions for a user.
     *
     * @param userId the user's ID
     * @param now    the current instant
     * @return the number of active sessions
     */
    @Query("SELECT COUNT(rt) FROM RefreshTokenEntity rt WHERE rt.user.id = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveSessionsByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
