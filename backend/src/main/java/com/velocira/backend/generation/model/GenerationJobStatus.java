package com.velocira.backend.generation.model;

/**
 * Durable lifecycle of a generation request.
 *
 * <p>The values intentionally describe user-visible pipeline stages instead
 * of worker implementation details. A job remains in one of these states
 * across restarts and can therefore be rendered accurately after a browser
 * refresh.</p>
 */
public enum GenerationJobStatus {

    /** Accepted and waiting for a worker to claim it. */
    QUEUED,

    /** Collecting the immutable retrieval/context snapshot. */
    RETRIEVING,

    /** Calling the provider to draft structured content. */
    DRAFTING,

    /** Validating the structured response before publishing an artifact. */
    VALIDATING,

    /** Additional user information is required before a safe retry. */
    NEEDS_INPUT,

    /** A validated artifact version is available. */
    READY,

    /** The job cannot continue without an explicit retry or new request. */
    FAILED,

    /** Cancellation was requested and the worker has stopped processing. */
    CANCELLED;

    /**
     * Whether no worker should continue this job unless a new retry is
     * explicitly created.
     */
    public boolean isTerminal() {
        return this == READY || this == FAILED || this == CANCELLED || this == NEEDS_INPUT;
    }

    /** Whether a cancellation request may still be honoured by a worker. */
    public boolean isCancellable() {
        return this == QUEUED || this == RETRIEVING || this == DRAFTING || this == VALIDATING;
    }
}
