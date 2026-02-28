package com.velocira.backend.auth.service;

import com.velocira.backend.auth.exceptions.InvalidTokenException;
import com.velocira.backend.auth.model.RefreshTokenEntity;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.RefreshTokenRepository;
import com.velocira.backend.auth.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service managing refresh token lifecycle: creation, rotation, revocation, and
 * validation.
 *
 * <p>
 * Implements production-grade token management:
 * </p>
 * <ul>
 * <li><strong>Token rotation</strong>: each refresh creates a new token and
 * revokes the old one</li>
 * <li><strong>Theft detection</strong>: if a revoked token is reused, all user
 * sessions are killed</li>
 * <li><strong>Hashed storage</strong>: tokens are stored as SHA-256 hashes</li>
 * <li><strong>Device tracking</strong>: each token records device info and IP
 * for session management</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${velocira.security.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /**
     * Creates a new refresh token for the given user and device.
     *
     * @param user       the user to create the token for
     * @param deviceInfo device/client identifier (User-Agent)
     * @param ipAddress  client IP address
     * @return the plain-text refresh token (to be sent to the client)
     */
    @Transactional
    public String createRefreshToken(UserEntity user, String deviceInfo, String ipAddress) {
        String plainToken = TokenHashUtil.generateSecureToken(32);
        String tokenHash = TokenHashUtil.sha256(plainToken);

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .build();

        refreshTokenRepository.save(entity);
        log.info("Refresh token created for user [{}] from IP [{}]", user.getEmail(), ipAddress);

        return plainToken;
    }

    /**
     * Rotates a refresh token: validates the old token, revokes it, and creates a
     * new one.
     *
     * <p>
     * If the old token has already been revoked (potential token theft), this
     * method
     * kills <strong>all</strong> sessions for the user as a security precaution.
     * </p>
     *
     * @param plainToken the plain-text refresh token from the client
     * @param deviceInfo device/client identifier
     * @param ipAddress  client IP address
     * @return a {@link RotationResult} containing the new token and the associated
     *         user
     * @throws InvalidTokenException if the token is invalid, expired, or revoked
     */
    @Transactional
    public RotationResult rotateRefreshToken(String plainToken, String deviceInfo, String ipAddress) {
        String tokenHash = TokenHashUtil.sha256(plainToken);

        RefreshTokenEntity existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found for hash rotation");
                    return new InvalidTokenException();
                });

        // Theft detection: if a revoked token is reused, kill all sessions
        if (existingToken.isRevoked()) {
            log.error("SECURITY ALERT: Revoked refresh token reused for user [{}]. " +
                    "Revoking all sessions.", existingToken.getUser().getEmail());
            refreshTokenRepository.revokeAllByUserId(existingToken.getUser().getId());
            throw new InvalidTokenException(
                    "Security violation detected. All sessions have been terminated. Please log in again.");
        }

        // Check expiry
        if (existingToken.isExpired()) {
            log.warn("Expired refresh token used by user [{}]", existingToken.getUser().getEmail());
            existingToken.setRevoked(true);
            refreshTokenRepository.save(existingToken);
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        // Create new token
        String newPlainToken = TokenHashUtil.generateSecureToken(32);
        String newTokenHash = TokenHashUtil.sha256(newPlainToken);

        // Revoke old token and link to new
        existingToken.setRevoked(true);
        existingToken.setReplacedByToken(newTokenHash);
        refreshTokenRepository.save(existingToken);

        // Save new token
        RefreshTokenEntity newEntity = RefreshTokenEntity.builder()
                .user(existingToken.getUser())
                .tokenHash(newTokenHash)
                .expiresAt(Instant.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .build();

        refreshTokenRepository.save(newEntity);

        log.info("Refresh token rotated for user [{}]", existingToken.getUser().getEmail());
        return new RotationResult(newPlainToken, existingToken.getUser());
    }

    /**
     * Revokes a specific refresh token (single device logout).
     *
     * @param plainToken the plain-text refresh token to revoke
     */
    @Transactional
    public void revokeToken(String plainToken) {
        String tokenHash = TokenHashUtil.sha256(plainToken);
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidTokenException::new);

        token.setRevoked(true);
        refreshTokenRepository.save(token);
        log.info("Refresh token revoked for user [{}]", token.getUser().getEmail());
    }

    /**
     * Revokes all refresh tokens for a user (logout from all devices).
     *
     * @param user the user whose sessions should be terminated
     */
    @Transactional
    public void revokeAllTokens(UserEntity user) {
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("All refresh tokens revoked for user [{}]", user.getEmail());
    }

    /**
     * Result record for token rotation — contains the new token and the associated
     * user.
     *
     * @param newPlainToken the new plain-text refresh token
     * @param user          the user entity
     */
    public record RotationResult(String newPlainToken, UserEntity user) {
    }
}
