package com.velocira.backend.auth.model;

/**
 * Enumeration of authentication providers supported by Velocira.
 *
 * <p>
 * Tracks how a user originally registered, which affects
 * password requirements and profile update flows:
 * </p>
 * <ul>
 * <li>{@link #LOCAL} — Email + password registration</li>
 * <li>{@link #GOOGLE} — Google OAuth2 registration</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum AuthProvider {

    /** User registered with email and password. */
    LOCAL,

    /** User registered via Google OAuth2. */
    GOOGLE
}
