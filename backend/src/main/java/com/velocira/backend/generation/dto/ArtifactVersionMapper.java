package com.velocira.backend.generation.dto;

import com.velocira.backend.generation.model.ArtifactVersionEntity;

/** Maps an immutable artifact version to an owner-authorized response. */
public final class ArtifactVersionMapper {

    private ArtifactVersionMapper() {
    }

    public static ArtifactVersionResponse toResponse(ArtifactVersionEntity entity) {
        if (entity == null) {
            return null;
        }

        return ArtifactVersionResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject() == null ? null : entity.getProject().getId())
                .documentId(entity.getDocument() == null ? null : entity.getDocument().getId())
                .generationJobId(entity.getGenerationJob() == null ? null : entity.getGenerationJob().getId())
                .generationRunId(entity.getGenerationRun() == null ? null : entity.getGenerationRun().getId())
                .artifactType(entity.getArtifactType())
                .versionNumber(entity.getVersionNumber())
                .title(entity.getTitle())
                .content(entity.getContent())
                .contentSha256(entity.getContentSha256())
                .validationStatus(entity.getValidationStatus())
                .validatorOutcome(entity.getValidatorOutcome())
                .provider(entity.getProvider())
                .model(entity.getModel())
                .promptTemplateKey(entity.getPromptTemplateKey())
                .promptTemplateVersion(entity.getPromptTemplateVersion())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
