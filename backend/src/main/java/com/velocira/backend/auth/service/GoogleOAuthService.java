package com.velocira.backend.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.velocira.backend.auth.exceptions.GoogleAuthenticationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for validating Google OAuth2 ID tokens.
 *
 * <p>
 * Validates tokens against Google's public keys and extracts user information.
 * The Google client ID is configured via application properties.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
public class GoogleOAuthService {

    @Value("${velocira.security.oauth2.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    /**
     * Initializes the Google ID token verifier after dependency injection.
     */
    @PostConstruct
    void init() {
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    /**
     * Verifies a Google ID token and extracts user information.
     *
     * @param idTokenString the Google ID token string from the frontend
     * @return a {@link GoogleUserInfo} record with the user's details
     * @throws GoogleAuthenticationException if the token is invalid or verification
     *                                       fails
     */
    public GoogleUserInfo verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token verification returned null");
                throw new GoogleAuthenticationException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            if (!emailVerified) {
                throw new GoogleAuthenticationException("Google account email is not verified");
            }

            log.info("Google ID token verified for email [{}]", email);
            return new GoogleUserInfo(email, name, pictureUrl);

        } catch (GoogleAuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google ID token verification failed: {}", ex.getMessage(), ex);
            throw new GoogleAuthenticationException("Failed to verify Google ID token");
        }
    }

    /**
     * Record holding user information extracted from a Google ID token.
     *
     * @param email      the user's email from Google
     * @param name       the user's display name from Google
     * @param pictureUrl the user's profile picture URL from Google
     */
    public record GoogleUserInfo(String email, String name, String pictureUrl) {
    }
}
