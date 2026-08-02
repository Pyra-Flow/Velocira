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
    PROJECT_ARCHIVED,
    PROJECT_RESTORED,
    PROJECT_STATUS_CHANGED,

    // ── Document ────────────────────────────────────────────────
    DOCUMENT_CREATED,
    DOCUMENT_UPDATED,
    DOCUMENT_DELETED,
    DOCUMENT_EXPORTED,
    DOCUMENT_GENERATION_REQUESTED,

    // ── Durable generation jobs ────────────────────────────────
    GENERATION_JOB_REQUESTED,
    GENERATION_JOB_CANCELLED,
    GENERATION_JOB_RETRIED,
    GENERATION_JOB_COMPLETED,
    GENERATION_JOB_FAILED,

    // ── Requirements discovery ─────────────────────────────────
    INTERVIEW_STARTED,
    INTERVIEW_ANSWER_RECORDED,
    INTERVIEW_BRIEF_CONFIRMED,
    INTERVIEW_REOPENED,

    // ── Governed evidence and SRS ─────────────────────────────
    KNOWLEDGE_SOURCE_UPLOADED,
    KNOWLEDGE_SOURCE_APPROVED,
    KNOWLEDGE_SOURCE_REJECTED,
    KNOWLEDGE_SOURCE_DELETED,
    SRS_GENERATED,
    SRS_APPROVED,
    SRS_CHANGE_REQUESTED,

    // ── Linked documentation package ──────────────────────────
    DOCUMENTATION_PACKAGE_GENERATED,
    DOCUMENTATION_PACKAGE_APPROVED,
    DOCUMENTATION_PACKAGE_EXPORTED,

    // ── Admin ───────────────────────────────────────────────────
    ADMIN_USER_SUSPENDED,
    ADMIN_USER_ACTIVATED,
    ADMIN_ROLE_CHANGED,
    ADMIN_USER_DELETED
}
