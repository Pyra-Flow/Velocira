package com.velocira.backend.generation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Typed response accepted from the isolated AI service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiGenerationResponse(
        boolean success,
        String provider,
        String model,
        @JsonProperty("prompt_version") String promptVersion,
        Artifact artifact,
        Validation validation,
        Usage usage,
        @JsonProperty("latency_ms") Long latencyMs,
        Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artifact(String title, String content, List<Section> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String id, String heading, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(boolean valid, List<String> issues) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Long inputTokens,
            @JsonProperty("output_tokens") Long outputTokens,
            @JsonProperty("cost_cents") BigDecimal costCents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String message, boolean retryable) {
    }
}
