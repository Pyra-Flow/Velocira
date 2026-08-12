package com.velocira.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The compact, outcome-focused input used to start a project. */
public record ProjectBriefRequest(
        @NotBlank(message = "Describe what you would like to create.")
        @Size(min = 10, max = 4_000, message = "Add a little more detail so we can create a useful first version.")
        String brief,
        @Size(max = 255, message = "Project name must be 255 characters or fewer.") String title,
        @Size(max = 500, message = "Audience must be 500 characters or fewer.") String audience,
        @Size(max = 1_000, message = "Include notes must be 1000 characters or fewer.") String include,
        @Size(max = 1_000, message = "Avoid notes must be 1000 characters or fewer.") String avoid) {
}
