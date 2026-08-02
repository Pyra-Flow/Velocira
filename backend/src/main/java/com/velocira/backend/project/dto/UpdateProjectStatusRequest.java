package com.velocira.backend.project.dto;

import com.velocira.backend.project.model.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request to move a project through an allowed lifecycle transition. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requested project lifecycle state")
public class UpdateProjectStatusRequest {

    @NotNull(message = "Project status is required")
    private ProjectStatus status;
}
