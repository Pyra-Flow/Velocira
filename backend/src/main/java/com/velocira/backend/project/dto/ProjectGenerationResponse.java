package com.velocira.backend.project.dto;

/** Safe, user-facing view of the latest project generation. */
public record ProjectGenerationResponse(
        ProjectResponse project,
        ProjectGenerationJobResponse generation,
        ProjectGenerationStage stage,
        String headline,
        String detail,
        boolean canCancel,
        boolean canRetry) {
}
