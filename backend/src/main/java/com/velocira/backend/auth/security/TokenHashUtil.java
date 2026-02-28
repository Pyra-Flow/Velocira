package com.velocira.backend.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Utility class for token and OTP hashing operations.
 *
 * <p>
 * Uses SHA-256 for hashing refresh tokens and OTP codes.
 * These are <strong>one-way hashes</strong> — the original values
 * cannot be recovered from the hash.
 * </p>
 *
 * <p>
 * Thread-safe: all methods are stateless and use local variables.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class TokenHashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHashUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Computes the SHA-256 hash of the given input string.
     *
     * @param input the string to hash
     * @return the lowercase hex-encoded SHA-256 hash (64 characters)
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    /**
     * Generates a cryptographically secure random token string.
     *
     * @param byteLength the number of random bytes (token will be 2x this length in
     *                   hex)
     * @return a hex-encoded random token
     */
    public static String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Generates a numeric OTP code of the specified length.
     *
     * @param length the number of digits (e.g., 6)
     * @return a zero-padded numeric OTP string
     */
    public static String generateOtp(int length) {
        int max = (int) Math.pow(10, length);
        int otp = SECURE_RANDOM.nextInt(max);
        return String.format("%0" + length + "d", otp);
    }
}
