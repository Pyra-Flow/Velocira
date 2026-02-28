package com.velocira.backend.project.dto;

import com.velocira.backend.project.model.ProjectEntity;

/**
 * Maps {@link ProjectEntity} to {@link ProjectResponse}.
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class ProjectMapper {

    private ProjectMapper() {
    }

    /**
     * Converts a {@link ProjectEntity} to a response DTO.
     *
     * @param entity the project entity (must not be null)
     * @return the response DTO
     */
    public static ProjectResponse toResponse(ProjectEntity entity) {
        if (entity == null)
            return null;

        return ProjectResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .status(entity.getStatus())
                .techStack(entity.getTechStack())
                .industry(entity.getIndustry())
                .targetAudience(entity.getTargetAudience())
                .teamSize(entity.getTeamSize())
                .progress(entity.getProgress())
                .documentCount(entity.getDocuments() != null ? entity.getDocuments().size() : 0)
                .ownerId(entity.getOwner() != null ? entity.getOwner().getId() : null)
                .ownerName(entity.getOwner() != null ? entity.getOwner().getFullName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
