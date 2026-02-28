package com.velocira.backend.document.model;

/**
 * Lifecycle status of a generated document.
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum DocumentStatus {

    /** Placeholder created, content not yet generated. */
    PENDING,

    /** AI generation is in progress. */
    GENERATING,

    /** Document has been successfully generated. */
    COMPLETED,

    /** Generation failed (user can retry). */
    FAILED,

    /** User has manually edited the generated content. */
    EDITED
}
