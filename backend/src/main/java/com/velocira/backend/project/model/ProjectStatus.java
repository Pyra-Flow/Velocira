package com.velocira.backend.project.model;

/**
 * Lifecycle status of a project.
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum ProjectStatus {

    /** Project exists but the owner has not begun discovery. */
    DRAFT,

    /** Requirements and supporting evidence are being gathered. */
    DISCOVERY,

    /** Discovery is sufficient for a generation job to be requested. */
    READY_FOR_GENERATION,

    /** AI document generation is currently in progress. */
    GENERATING,

    /** Generated material is awaiting an owner review. */
    NEEDS_REVIEW,

    /** The owner has approved the reviewed project materials. */
    APPROVED,

    /** Document generation failed — user can retry. */
    FAILED,

    /** The project is hidden from the normal workspace but can be restored. */
    ARCHIVED
}
