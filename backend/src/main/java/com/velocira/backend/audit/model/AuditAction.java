package com.velocira.backend.audit.model;

/**
 * Enumeration of all auditable actions in the Velocira platform.
 *
 * <p>
 * Each action represents a significant user or system event that is
 * recorded in the {@code audit_logs} table for compliance, debugging,
 * and analytics purposes.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum AuditAction {

    // ── Authentication ──────────────────────────────────────────
    USER_REGISTERED,
    USER_LOGIN,
    USER_LOGOUT,
    USER_LOGOUT_ALL,
    TOKEN_REFRESHED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    EMAIL_VERIFIED,
    GOOGLE_LOGIN,

    // ── Profile ─────────────────────────────────────────────────
    PROFILE_UPDATED,
    PASSWORD_CHANGED,
    AVATAR_UPDATED,

    // ── Project ─────────────────────────────────────────────────
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_DELETED,
    PROJECT_DUPLICATED,

    // ── Document ────────────────────────────────────────────────
    DOCUMENT_CREATED,
    DOCUMENT_UPDATED,
    DOCUMENT_DELETED,
    DOCUMENT_EXPORTED,
    DOCUMENT_GENERATION_REQUESTED,

    // ── Admin ───────────────────────────────────────────────────
    ADMIN_USER_SUSPENDED,
    ADMIN_USER_ACTIVATED,
    ADMIN_ROLE_CHANGED,
    ADMIN_USER_DELETED
}
