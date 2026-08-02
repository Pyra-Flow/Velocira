package com.velocira.backend.generation.model;

/** Outcome of one provider execution attempt for a generation job. */
public enum GenerationRunStatus {

    /** The worker has started this attempt. */
    RUNNING,

    /** The provider returned a usable response. */
    SUCCEEDED,

    /** The provider or orchestration failed with a non-timeout error. */
    FAILED,

    /** The attempt exceeded its configured deadline. */
    TIMED_OUT,

    /** The attempt stopped in response to a cancellation request. */
    CANCELLED;

    /** Whether the run cannot be updated with further provider output. */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
