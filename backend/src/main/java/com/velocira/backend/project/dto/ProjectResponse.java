package com.velocira.backend.project.dto;

import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Project response DTO returned by the Project module endpoints.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Project response")
public class ProjectResponse {

    @Schema(description = "Project unique identifier")
    private UUID id;

    @Schema(description = "Project name", example = "E-Commerce Platform")
    private String name;

    @Schema(description = "Project description")
    private String description;

    @Schema(description = "Project type", example = "WEB_APP")
    private ProjectType type;

    @Schema(description = "Project status", example = "DRAFT")
    private ProjectStatus status;

    @Schema(description = "Preferred tech stack", example = "React, Node.js, PostgreSQL")
    private String techStack;

    @Schema(description = "Industry vertical", example = "E-Commerce / Retail")
    private String industry;

    @Schema(description = "Target audience")
    private String targetAudience;

    @Schema(description = "Team size", example = "5")
    private Integer teamSize;

    @Schema(description = "Generation progress (0–100)", example = "0")
    private int progress;

    @Schema(description = "Number of generated documents", example = "0")
    private long documentCount;

    @Schema(description = "Owner's user ID")
    private UUID ownerId;

    @Schema(description = "Owner's full name")
    private String ownerName;

    @Schema(description = "Project creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
