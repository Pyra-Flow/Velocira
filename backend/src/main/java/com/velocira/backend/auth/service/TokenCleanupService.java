package com.velocira.backend.auth.service;

import com.velocira.backend.auth.repository.OtpRepository;
import com.velocira.backend.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled service for cleaning up expired tokens and OTPs.
 *
 * <p>
 * Runs periodically to remove stale data from the database,
 * preventing unbounded table growth in production environments.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpRepository otpRepository;

    /**
     * Cleans up expired and revoked refresh tokens.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("Starting refresh token cleanup");
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        log.info("Refresh token cleanup completed");
    }

    /**
     * Cleans up expired OTP tokens.
     * Runs every 30 minutes.
     */
    @Scheduled(fixedRate = 1800000) // Every 30 minutes
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Starting OTP cleanup");
        otpRepository.deleteExpired(Instant.now());
        log.info("OTP cleanup completed");
    }
}
