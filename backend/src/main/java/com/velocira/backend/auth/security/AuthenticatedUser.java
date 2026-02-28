package com.velocira.backend.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Lightweight principal object stored in the
 * {@link org.springframework.security.core.context.SecurityContext}
 * after successful JWT authentication.
 *
 * <p>
 * This replaces the need for a full
 * {@link org.springframework.security.core.userdetails.UserDetails}
 * implementation since we authenticate via stateless JWT tokens (not sessions).
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    /** The authenticated user's UUID. */
    private final UUID userId;

    /** The authenticated user's email. */
    private final String email;

    /** The authenticated user's role name. */
    private final String role;

    @Override
    public String toString() {
        return "AuthenticatedUser{userId=" + userId + ", email='" + email + "', role='" + role + "'}";
    }
}
