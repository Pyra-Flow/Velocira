package com.velocira.backend.project.dto;

import com.velocira.backend.project.model.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating an existing project.
 *
 * <p>
 * All fields are optional — only non-null values are applied.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update project request — all fields optional")
public class UpdateProjectRequest {

    @Size(min = 2, max = 255, message = "Project name must be between 2 and 255 characters")
    @Schema(description = "Updated project name", example = "E-Commerce Platform v2")
    private String name;

    @Size(min = 50, max = 2000, message = "Description must be between 50 and 2000 characters")
    @Schema(description = "Updated description")
    private String description;

    @Schema(description = "Updated project type", example = "WEB_APP")
    private ProjectType type;

    @Size(max = 500, message = "Tech stack must not exceed 500 characters")
    @Schema(description = "Updated tech stack")
    private String techStack;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Schema(description = "Updated industry")
    private String industry;

    @Size(max = 500, message = "Target audience must not exceed 500 characters")
    @Schema(description = "Updated target audience")
    private String targetAudience;

    @Min(value = 1, message = "Team size must be at least 1")
    @Max(value = 100, message = "Team size must not exceed 100")
    @Schema(description = "Updated team size")
    private Integer teamSize;
}
