package com.velocira.backend.project.model;

/**
 * Lifecycle status of a project.
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum ProjectStatus {

    /** Project created but no document generation initiated yet. */
    DRAFT,

    /** AI document generation is currently in progress. */
    GENERATING,

    /** All requested documents have been generated successfully. */
    COMPLETE,

    /** Document generation failed — user can retry. */
    FAILED
}
