package com.velocira.backend.generation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.generation.model.GenerationErrorCode;
import com.velocira.backend.generation.model.GenerationRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Audit-friendly response for one provider execution attempt. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRunResponse {

    private UUID id;
    private UUID generationJobId;
    private int attemptNumber;
    private GenerationRunStatus status;
    private String provider;
    private String model;
    private String providerRequestId;
    private String promptTemplateKey;
    private String promptTemplateVersion;
    private JsonNode outputMetadata;
    private JsonNode validatorOutcome;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal costUsd;
    private Long latencyMs;
    private boolean retryable;
    private GenerationErrorCode failureCode;
    private String failureMessage;
    private Instant startedAt;
    private Instant completedAt;
}
