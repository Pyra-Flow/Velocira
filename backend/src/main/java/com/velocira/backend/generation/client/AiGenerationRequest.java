package com.velocira.backend.generation.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Typed, minimal request sent from the trusted worker to FastAPI. */
public record AiGenerationRequest(
        @JsonProperty("job_id") UUID jobId,
        ProjectContext project,
        @JsonProperty("artifact_type") String artifactType,
        PromptContext prompt,
        @JsonProperty("idempotency_key") String idempotencyKey) {

    public record ProjectContext(UUID id, String name, String description, String type) {
    }

    public record PromptContext(String key, String version, String content) {
    }
}
