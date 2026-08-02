package com.velocira.backend.generation.dto;

import com.velocira.backend.generation.model.GenerationRunEntity;

/** Maps execution attempts while intentionally excluding their raw input snapshot. */
public final class GenerationRunMapper {

    private GenerationRunMapper() {
    }

    public static GenerationRunResponse toResponse(GenerationRunEntity entity) {
        if (entity == null) {
            return null;
        }

        return GenerationRunResponse.builder()
                .id(entity.getId())
                .generationJobId(entity.getGenerationJob() == null ? null : entity.getGenerationJob().getId())
                .attemptNumber(entity.getAttemptNumber())
                .status(entity.getStatus())
                .provider(entity.getProvider())
                .model(entity.getModel())
                .providerRequestId(entity.getProviderRequestId())
                .promptTemplateKey(entity.getPromptTemplateKey())
                .promptTemplateVersion(entity.getPromptTemplateVersion())
                .outputMetadata(entity.getOutputMetadata())
                .validatorOutcome(entity.getValidatorOutcome())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .totalTokens(entity.getTotalTokens())
                .costUsd(entity.getCostUsd())
                .latencyMs(entity.getLatencyMs())
                .retryable(entity.isRetryable())
                .failureCode(entity.getFailureCode())
                .failureMessage(entity.getFailureMessage())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
