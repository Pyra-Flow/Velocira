package com.velocira.backend.document.model;

/**
 * Enumeration of document types that can be generated for a project.
 *
 * <p>
 * These map to the AI-generated deliverables described in the
 * Velocira SRS documentation. Each type represents a specific
 * software engineering artifact.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum DocumentType {

    /** Software Requirements Specification */
    SRS,

    /** Business Requirements Document */
    BRD,

    /** Technical Architecture Document */
    TECHNICAL_ARCHITECTURE,

    /** API Specification / Design Document */
    API_SPECIFICATION,

    /** Database Schema / ERD Document */
    DATABASE_SCHEMA,

    /** User Interface / UX Wireframe Document */
    UI_UX_DESIGN,

    /** Test Plan and Test Cases */
    TEST_PLAN,

    /** Deployment and DevOps Pipeline Document */
    DEPLOYMENT_GUIDE,

    /** Project Timeline and Milestones */
    PROJECT_TIMELINE,

    /** Risk Assessment Document */
    RISK_ASSESSMENT,

    /** User Manual / End-User Documentation */
    USER_MANUAL
}
