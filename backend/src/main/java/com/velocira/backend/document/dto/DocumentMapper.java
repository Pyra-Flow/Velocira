package com.velocira.backend.document.dto;

import com.velocira.backend.document.model.DocumentEntity;

/**
 * Maps {@link DocumentEntity} to {@link DocumentResponse}.
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class DocumentMapper {

    private DocumentMapper() {
    }

    /**
     * Converts a {@link DocumentEntity} to a response DTO.
     *
     * @param entity the document entity (must not be null)
     * @return the response DTO
     */
    public static DocumentResponse toResponse(DocumentEntity entity) {
        if (entity == null)
            return null;

        return DocumentResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject() != null ? entity.getProject().getId() : null)
                .projectName(entity.getProject() != null ? entity.getProject().getName() : null)
                .type(entity.getType())
                .status(entity.getStatus())
                .title(entity.getTitle())
                .content(entity.getContent())
                .version(entity.getVersion())
                .wordCount(entity.getWordCount())
                .aiModel(entity.getAiModel())
                .generationTimeMs(entity.getGenerationTimeMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a {@link DocumentEntity} to a lightweight response (no content).
     * Useful for list endpoints where content is not needed.
     *
     * @param entity the document entity
     * @return the response DTO without content
     */
    public static DocumentResponse toSummaryResponse(DocumentEntity entity) {
        if (entity == null)
            return null;

        return DocumentResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProject() != null ? entity.getProject().getId() : null)
                .projectName(entity.getProject() != null ? entity.getProject().getName() : null)
                .type(entity.getType())
                .status(entity.getStatus())
                .title(entity.getTitle())
                .version(entity.getVersion())
                .wordCount(entity.getWordCount())
                .aiModel(entity.getAiModel())
                .generationTimeMs(entity.getGenerationTimeMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
