package com.velocira.backend.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtProvider}.
 *
 * <p>
 * Uses constructor injection directly since JwtProvider accepts
 * secret, expiry, and issuer via its constructor.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@DisplayName("JwtProvider Tests")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    /** Base64-encoded 256-bit test secret key. */
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHM=";
    private static final long ACCESS_TOKEN_EXPIRY = 900_000L; // 15 min
    private static final String ISSUER = "velocira-test";

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(TEST_SECRET, ACCESS_TOKEN_EXPIRY, ISSUER);
    }

    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {

        @Test
        @DisplayName("Should generate a valid access token")
        void shouldGenerateValidAccessToken() {
            UUID userId = UUID.randomUUID();
            String email = "test@velocira.com";

            String token = jwtProvider.generateAccessToken(userId, email, "USER");

            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtProvider.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("Should embed correct claims in token")
        void shouldEmbedCorrectClaims() {
            UUID userId = UUID.randomUUID();
            String email = "test@velocira.com";

            String token = jwtProvider.generateAccessToken(userId, email, "ADMIN");

            assertThat(jwtProvider.getUserIdFromToken(token)).isEqualTo(userId);
            assertThat(jwtProvider.getEmailFromToken(token)).isEqualTo(email);
            assertThat(jwtProvider.getRoleFromToken(token)).isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("Should reject a malformed token")
        void shouldRejectMalformedToken() {
            assertThat(jwtProvider.isTokenValid("not.a.valid.token")).isFalse();
        }

        @Test
        @DisplayName("Should reject a null token")
        void shouldRejectNullToken() {
            assertThat(jwtProvider.isTokenValid(null)).isFalse();
        }

        @Test
        @DisplayName("Should reject an empty token")
        void shouldRejectEmptyToken() {
            assertThat(jwtProvider.isTokenValid("")).isFalse();
        }

        @Test
        @DisplayName("Should reject an expired token")
        void shouldRejectExpiredToken() throws Exception {
            // Create provider with 0ms expiry
            JwtProvider expiredProvider = new JwtProvider(TEST_SECRET, 0L, ISSUER);

            String token = expiredProvider.generateAccessToken(
                    UUID.randomUUID(), "test@velocira.com", "USER");

            // Token should be expired immediately
            Thread.sleep(10);
            assertThat(jwtProvider.isTokenValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Claim Extraction")
    class ClaimExtraction {

        @Test
        @DisplayName("Should extract userId from token")
        void shouldExtractUserId() {
            UUID userId = UUID.randomUUID();
            String token = jwtProvider.generateAccessToken(userId, "test@velocira.com", "USER");

            assertThat(jwtProvider.getUserIdFromToken(token)).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should extract email from token")
        void shouldExtractEmail() {
            String email = "user@velocira.com";
            String token = jwtProvider.generateAccessToken(UUID.randomUUID(), email, "USER");

            assertThat(jwtProvider.getEmailFromToken(token)).isEqualTo(email);
        }

        @Test
        @DisplayName("Should extract role from token")
        void shouldExtractRole() {
            String token = jwtProvider.generateAccessToken(
                    UUID.randomUUID(), "admin@velocira.com", "ADMIN");

            assertThat(jwtProvider.getRoleFromToken(token)).isEqualTo("ADMIN");
        }
    }
}
