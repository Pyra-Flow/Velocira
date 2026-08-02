package com.velocira.backend.generation.dto;

import com.velocira.backend.generation.model.GenerationJobEntity;

/** Maps a generation job to a response without exposing internal diagnostics. */
public final class GenerationJobMapper {

    private GenerationJobMapper() {
    }

    public static GenerationJobResponse toResponse(GenerationJobEntity entity) {
        if (entity == null) {
            return null;
        }

        return GenerationJobResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject() == null ? null : entity.getProject().getId())
                .documentId(entity.getDocument() == null ? null : entity.getDocument().getId())
                .artifactVersionId(entity.getArtifactVersion() == null ? null : entity.getArtifactVersion().getId())
                .requestedDocumentType(entity.getRequestedDocumentType())
                .status(entity.getStatus())
                .idempotencyKey(entity.getIdempotencyKey())
                .attemptCount(entity.getAttemptCount())
                .maxAttempts(entity.getMaxAttempts())
                .cancelRequested(entity.isCancelRequested())
                .retryable(entity.isRetryable())
                .statusMessage(entity.getStatusMessage())
                .errorCode(entity.getErrorCode())
                .userMessage(entity.getUserMessage())
                .correlationId(entity.getCorrelationId())
                .queuedAt(entity.getQueuedAt())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .nextAttemptAt(entity.getNextAttemptAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
