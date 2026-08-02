package com.velocira.backend.generation.client;

/** A classified failure from the isolated AI generation service. */
public class AiGenerationException extends RuntimeException {

    private final AiFailureCode code;
    private final boolean retryable;

    public AiGenerationException(AiFailureCode code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public AiGenerationException(AiFailureCode code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public AiFailureCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
