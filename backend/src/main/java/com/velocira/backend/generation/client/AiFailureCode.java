package com.velocira.backend.generation.client;

/** Stable, non-provider-specific error categories returned by the AI boundary. */
public enum AiFailureCode {
    UNAVAILABLE,
    TIMEOUT,
    INVALID_OUTPUT,
    SAFETY_REJECTED,
    INVALID_REQUEST,
    INTERNAL_ERROR
}
