package com.velocira.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A focused request to improve the next project-plan version. */
public record ProjectRefinementRequest(
        @NotBlank(message = "Tell us what you would like to change.")
        @Size(max = 2_000, message = "Keep your change request under 2000 characters.")
        String message) {
}
