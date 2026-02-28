package com.velocira.backend.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a document's content or title.
 *
 * <p>
 * All fields are optional — only non-null fields are applied.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update document request")
public class UpdateDocumentRequest {

    @Size(min = 2, max = 255, message = "Title must be between 2 and 255 characters")
    @Schema(description = "Updated document title")
    private String title;

    @Schema(description = "Updated document content in Markdown format")
    private String content;
}
