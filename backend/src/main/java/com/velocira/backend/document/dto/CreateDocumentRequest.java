package com.velocira.backend.document.dto;

import com.velocira.backend.document.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a document placeholder within a project.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create document request")
public class CreateDocumentRequest {

    @NotNull(message = "Document type is required")
    @Schema(description = "Type of document to create", example = "SRS", requiredMode = Schema.RequiredMode.REQUIRED)
    private DocumentType type;

    @NotBlank(message = "Document title is required")
    @Size(min = 2, max = 255, message = "Title must be between 2 and 255 characters")
    @Schema(description = "Document title", example = "Software Requirements Specification")
    private String title;
}
