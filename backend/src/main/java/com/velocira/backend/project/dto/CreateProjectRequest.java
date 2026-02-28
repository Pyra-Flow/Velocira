package com.velocira.backend.project.dto;

import com.velocira.backend.project.model.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new project.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create new project request")
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 2, max = 255, message = "Project name must be between 2 and 255 characters")
    @Schema(description = "Project name", example = "E-Commerce Platform")
    private String name;

    @NotBlank(message = "Project description is required")
    @Size(min = 50, max = 2000, message = "Description must be between 50 and 2000 characters")
    @Schema(description = "Free-text project idea description (50–2000 chars)", example = "A full-stack e-commerce platform with product catalog, shopping cart, payment processing, user reviews, and admin dashboard for managing inventory and orders.")
    private String description;

    @NotNull(message = "Project type is required")
    @Schema(description = "Type of project", example = "WEB_APP")
    private ProjectType type;

    @Size(max = 500, message = "Tech stack must not exceed 500 characters")
    @Schema(description = "Preferred tech stack (comma-separated)", example = "React, Node.js, PostgreSQL")
    private String techStack;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Schema(description = "Target industry vertical", example = "E-Commerce / Retail")
    private String industry;

    @Size(max = 500, message = "Target audience must not exceed 500 characters")
    @Schema(description = "Target audience description", example = "Small to medium online retailers")
    private String targetAudience;

    @Min(value = 1, message = "Team size must be at least 1")
    @Max(value = 100, message = "Team size must not exceed 100")
    @Schema(description = "Estimated team size", example = "5")
    private Integer teamSize;
}
