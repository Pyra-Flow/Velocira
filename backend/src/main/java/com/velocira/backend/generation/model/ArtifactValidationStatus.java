package com.velocira.backend.generation.model;

/** Validator outcome retained with an immutable generated artifact version. */
public enum ArtifactValidationStatus {

    /** Validation has not reached a conclusion yet. */
    PENDING,

    /** The artifact passed the configured structural and policy checks. */
    PASSED,

    /** The artifact did not satisfy a required validation rule. */
    FAILED,

    /** The artifact is structurally safe but requires a human decision. */
    NEEDS_REVIEW
}
