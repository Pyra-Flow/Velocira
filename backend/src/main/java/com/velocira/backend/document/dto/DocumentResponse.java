package com.velocira.backend.document.dto;

import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Document response DTO returned by Document module endpoints.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Document response")
public class DocumentResponse {

    @Schema(description = "Document unique identifier")
    private UUID id;

    @Schema(description = "Parent project ID")
    private UUID projectId;

    @Schema(description = "Parent project name")
    private String projectName;

    @Schema(description = "Document type", example = "SRS")
    private DocumentType type;

    @Schema(description = "Document status", example = "COMPLETED")
    private DocumentStatus status;

    @Schema(description = "Document title")
    private String title;

    @Schema(description = "Document content in Markdown (may be null for PENDING documents)")
    private String content;

    @Schema(description = "Document version number", example = "1")
    private int version;

    @Schema(description = "Word count", example = "1520")
    private int wordCount;

    @Schema(description = "AI model used for generation", example = "gpt-4o")
    private String aiModel;

    @Schema(description = "Generation time in milliseconds")
    private Long generationTimeMs;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last modification timestamp")
    private Instant updatedAt;
}
