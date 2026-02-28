package com.velocira.backend.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Centralized JWT utility for token generation, parsing, and validation.
 *
 * <p>
 * Security characteristics:
 * </p>
 * <ul>
 * <li>Uses HMAC-SHA256 (HS256) signing algorithm</li>
 * <li>Access tokens are short-lived (configurable, default 15 min)</li>
 * <li>Tokens include user ID as subject, email, and role as claims</li>
 * <li>Each token has a unique JTI (JWT ID) for traceability</li>
 * <li>Issuer claim is validated to prevent cross-service token reuse</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final String issuer;

    /**
     * Constructs the JWT provider with configuration from application properties.
     *
     * @param secret                  Base64-encoded secret key (min 256 bits)
     * @param accessTokenExpirationMs access token lifetime in milliseconds
     * @param issuer                  token issuer claim
     */
    public JwtProvider(
            @Value("${velocira.security.jwt.secret}") String secret,
            @Value("${velocira.security.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${velocira.security.jwt.issuer}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.issuer = issuer;
    }

    /**
     * Generates a short-lived JWT access token for the given user.
     *
     * @param userId the user's UUID
     * @param email  the user's email
     * @param role   the user's role name (e.g., "USER", "ADMIN")
     * @return the signed JWT access token string
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        log.debug("Generated access token for user [{}], expires at [{}]", userId, expiry);
        return token;
    }

    /**
     * Parses and validates a JWT access token.
     *
     * @param token the JWT string to parse
     * @return the parsed {@link Claims} if the token is valid
     * @throws JwtException if the token is invalid, expired, or malformed
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates a JWT token without throwing exceptions.
     *
     * @param token the JWT string to validate
     * @return {@code true} if the token is valid
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token expired: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Malformed JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.warn("JWT signature validation failed: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token is empty or null");
        }
        return false;
    }

    /**
     * Extracts the user ID (subject) from a valid JWT token.
     *
     * @param token the JWT string
     * @return the user's UUID
     */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    /**
     * Extracts the email claim from a valid JWT token.
     *
     * @param token the JWT string
     * @return the user's email
     */
    public String getEmailFromToken(String token) {
        return parseToken(token).get("email", String.class);
    }

    /**
     * Extracts the role claim from a valid JWT token.
     *
     * @param token the JWT string
     * @return the user's role
     */
    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Returns the access token expiration time in seconds.
     *
     * @return expiration in seconds
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}
