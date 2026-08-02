package com.velocira.backend.generation.dto;

import com.velocira.backend.document.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for a trusted, server-orchestrated generation job. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to asynchronously generate one project artifact")
public class CreateGenerationJobRequest {

    /** The artifact is selected from the existing safe document type vocabulary. */
    @NotNull(message = "Document type is required")
    @Schema(description = "Type of document to generate", example = "SRS", requiredMode = Schema.RequiredMode.REQUIRED)
    private DocumentType documentType;

    /**
     * Optional owner-provided context. The backend combines it with a server
     * snapshot of project data; clients never select a provider, model, or
     * prompt template.
     */
    @Size(max = 10_000, message = "Additional instructions must not exceed 10000 characters")
    @Schema(description = "Optional additional context for this request", example = "Focus on the first-release scope.")
    private String additionalInstructions;
}
