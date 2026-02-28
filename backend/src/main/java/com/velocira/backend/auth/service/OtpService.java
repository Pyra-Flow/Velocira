package com.velocira.backend.auth.service;

import com.velocira.backend.auth.exceptions.InvalidOtpException;
import com.velocira.backend.auth.exceptions.OtpMaxAttemptsException;
import com.velocira.backend.auth.exceptions.OtpRateLimitException;
import com.velocira.backend.auth.model.OtpEntity;
import com.velocira.backend.auth.model.OtpType;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.OtpRepository;
import com.velocira.backend.auth.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service managing OTP (One-Time Password) lifecycle: generation, sending,
 * validation, and rate-limit enforcement.
 *
 * <p>
 * Security measures:
 * </p>
 * <ul>
 * <li>OTPs are stored as SHA-256 hashes — plain codes are only sent via
 * email</li>
 * <li>Rate limiting: max {@code N} OTPs per hour per user per type</li>
 * <li>Attempt limiting: max {@code N} validation attempts per OTP</li>
 * <li>Single-use: OTPs are marked as used after successful validation</li>
 * <li>Old OTPs are invalidated when a new one is generated</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;

    @Value("${velocira.security.otp.length}")
    private int otpLength;

    @Value("${velocira.security.otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Value("${velocira.security.otp.max-resend-per-hour}")
    private int maxResendPerHour;

    @Value("${velocira.security.otp.max-validation-attempts}")
    private int maxValidationAttempts;

    /**
     * Generates a new OTP for the given user and sends it via email.
     *
     * <p>
     * Before generating a new OTP, this method:
     * </p>
     * <ol>
     * <li>Checks the rate limit (max sends per hour)</li>
     * <li>Invalidates any existing unused OTPs of the same type</li>
     * <li>Creates a new OTP with a hashed code</li>
     * <li>Sends the plain-text code via email</li>
     * </ol>
     *
     * @param user    the user to send the OTP to
     * @param otpType the purpose of the OTP
     * @throws OtpRateLimitException if the rate limit has been exceeded
     */
    @Transactional
    public void generateAndSendOtp(UserEntity user, OtpType otpType) {
        // Check rate limit
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = otpRepository.countRecentOtps(user.getId(), otpType, oneHourAgo);

        if (recentCount >= maxResendPerHour) {
            log.warn("OTP rate limit exceeded for user [{}], type [{}]. Count: {}",
                    user.getEmail(), otpType, recentCount);
            throw new OtpRateLimitException();
        }

        // Invalidate existing OTPs of the same type
        otpRepository.invalidateAllByUserIdAndType(user.getId(), otpType);

        // Generate new OTP
        String plainOtp = TokenHashUtil.generateOtp(otpLength);
        String otpHash = TokenHashUtil.sha256(plainOtp);

        OtpEntity otpEntity = OtpEntity.builder()
                .user(user)
                .otpHash(otpHash)
                .otpType(otpType)
                .expiresAt(Instant.now().plus(otpExpirationMinutes, ChronoUnit.MINUTES))
                .build();

        otpRepository.save(otpEntity);

        // Send via email (async)
        emailService.sendOtpEmail(user, plainOtp, otpType);

        log.info("OTP generated and sent for user [{}], type [{}]", user.getEmail(), otpType);
    }

    /**
     * Validates an OTP code against the stored hash.
     *
     * @param user    the user who submitted the OTP
     * @param code    the plain-text OTP code to validate
     * @param otpType the expected OTP type
     * @throws InvalidOtpException     if no valid OTP exists or the code doesn't
     *                                 match
     * @throws OtpMaxAttemptsException if max validation attempts are exceeded
     */
    @Transactional
    public void validateOtp(UserEntity user, String code, OtpType otpType) {
        OtpEntity otpEntity = otpRepository.findLatestValidOtp(
                user.getId(), otpType, Instant.now())
                .orElseThrow(() -> {
                    log.warn("No valid OTP found for user [{}], type [{}]", user.getEmail(), otpType);
                    return new InvalidOtpException("No active OTP found. Please request a new one.");
                });

        // Check attempt limit
        if (otpEntity.getAttempts() >= maxValidationAttempts) {
            otpEntity.setUsed(true);
            otpRepository.save(otpEntity);
            log.warn("Max OTP attempts exceeded for user [{}], type [{}]", user.getEmail(), otpType);
            throw new OtpMaxAttemptsException();
        }

        // Verify hash
        String submittedHash = TokenHashUtil.sha256(code);
        if (!submittedHash.equals(otpEntity.getOtpHash())) {
            otpEntity.incrementAttempts();
            otpRepository.save(otpEntity);
            int remaining = maxValidationAttempts - otpEntity.getAttempts();
            log.warn("Invalid OTP attempt for user [{}], type [{}]. Remaining attempts: {}",
                    user.getEmail(), otpType, remaining);
            throw new InvalidOtpException(
                    "Invalid OTP code. " + remaining + " attempt(s) remaining.");
        }

        // Mark as used
        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);
        log.info("OTP validated successfully for user [{}], type [{}]", user.getEmail(), otpType);
    }
}
