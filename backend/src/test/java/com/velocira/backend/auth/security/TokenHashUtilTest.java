package com.velocira.backend.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TokenHashUtil}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@DisplayName("TokenHashUtil Tests")
class TokenHashUtilTest {

    @Nested
    @DisplayName("SHA-256 Hashing")
    class Sha256Hashing {

        @Test
        @DisplayName("Should produce consistent hash for same input")
        void shouldProduceConsistentHash() {
            String input = "test-token-value";
            String hash1 = TokenHashUtil.sha256(input);
            String hash2 = TokenHashUtil.sha256(input);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("Should produce different hash for different input")
        void shouldProduceDifferentHash() {
            String hash1 = TokenHashUtil.sha256("token-1");
            String hash2 = TokenHashUtil.sha256("token-2");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should produce 64-character hex string")
        void shouldProduceCorrectLength() {
            String hash = TokenHashUtil.sha256("any-input");

            // SHA-256 produces 32 bytes = 64 hex characters
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("Secure Token Generation")
    class SecureTokenGeneration {

        @Test
        @DisplayName("Should generate token of expected length")
        void shouldGenerateTokenOfExpectedLength() {
            String token = TokenHashUtil.generateSecureToken(32);

            // 32 bytes = 64 hex characters
            assertThat(token).hasSize(64);
        }

        @Test
        @DisplayName("Should generate unique tokens")
        void shouldGenerateUniqueTokens() {
            String token1 = TokenHashUtil.generateSecureToken(32);
            String token2 = TokenHashUtil.generateSecureToken(32);

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("OTP Generation")
    class OtpGeneration {

        @Test
        @DisplayName("Should generate OTP of requested length")
        void shouldGenerateOtpOfRequestedLength() {
            String otp = TokenHashUtil.generateOtp(6);

            assertThat(otp).hasSize(6);
        }

        @Test
        @DisplayName("Should generate numeric-only OTP")
        void shouldGenerateNumericOtp() {
            String otp = TokenHashUtil.generateOtp(6);

            assertThat(otp).matches("\\d+");
        }

        @Test
        @DisplayName("Should generate varying OTPs")
        void shouldGenerateVaryingOtps() {
            // Generate 10 OTPs — they shouldn't all be the same
            long uniqueCount = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(i -> TokenHashUtil.generateOtp(6))
                    .distinct()
                    .count();

            assertThat(uniqueCount).isGreaterThan(1);
        }
    }
}
