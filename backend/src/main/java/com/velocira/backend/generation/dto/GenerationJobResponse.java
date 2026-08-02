package com.velocira.backend.generation.dto;

import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.generation.model.GenerationErrorCode;
import com.velocira.backend.generation.model.GenerationJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Safe client-facing representation of a durable generation job. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Asynchronous generation job status")
public class GenerationJobResponse {

    private UUID id;
    private UUID projectId;
    private UUID documentId;
    private UUID artifactVersionId;

    @Schema(description = "Requested artifact type", example = "SRS")
    private DocumentType requestedDocumentType;

    @Schema(description = "Durable pipeline stage", example = "DRAFTING")
    private GenerationJobStatus status;

    /** Returned so a caller can correlate a resolved idempotent POST. */
    private String idempotencyKey;

    private int attemptCount;
    private int maxAttempts;
    private boolean cancelRequested;
    private boolean retryable;

    @Schema(description = "Safe text describing the current state")
    private String statusMessage;

    @Schema(description = "Stable failure category, if any")
    private GenerationErrorCode errorCode;

    @Schema(description = "Safe, helpful failure text, if any")
    private String userMessage;

    @Schema(description = "Trace identifier suitable for support requests")
    private String correlationId;

    private Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant updatedAt;
}
