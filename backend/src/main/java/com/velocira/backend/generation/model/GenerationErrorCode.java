package com.velocira.backend.generation.model;

/**
 * Stable, non-sensitive failure categories exposed by generation APIs.
 * Provider-specific diagnostics belong only in protected structured logs and
 * {@code errorDetails}; user-facing text must use {@code userMessage}.
 */
public enum GenerationErrorCode {

    PROVIDER_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    INVALID_PROVIDER_OUTPUT,
    VALIDATION_FAILED,
    CONTENT_SAFETY_BLOCKED,
    RETRIEVAL_FAILED,
    CANCELLED,
    RETRIES_EXHAUSTED,
    REQUEST_INVALID,
    INTERNAL_ERROR
}
