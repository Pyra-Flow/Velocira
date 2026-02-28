package com.velocira.backend.auth.model;

/**
 * Enumeration of user roles in the Velocira platform.
 *
 * <p>
 * Roles determine authorization levels across the application:
 * </p>
 * <ul>
 * <li>{@link #USER} — Standard authenticated user (default on
 * registration)</li>
 * <li>{@link #ADMIN} — Platform administrator with full management access</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum Role {

    /**
     * Standard authenticated user with access to project creation and document
     * generation.
     */
    USER,

    /**
     * Platform administrator with access to admin panel, user management, and
     * analytics.
     */
    ADMIN
}
