package com.velocira.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating the authenticated user's profile.
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
@Schema(description = "Update profile request — all fields optional")
public class UpdateProfileRequest {

    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    @Schema(description = "Updated full name", example = "Omar Wageh")
    private String fullName;

    @Size(max = 255, message = "University name must not exceed 255 characters")
    @Schema(description = "Updated university name", example = "Ain Shams University")
    private String universityName;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    @Schema(description = "Short user bio / about text", example = "Full-stack developer passionate about AI")
    private String bio;

    @Size(max = 100, message = "Job title must not exceed 100 characters")
    @Schema(description = "Professional job title", example = "Lead Software Engineer")
    private String jobTitle;

    @Size(max = 100, message = "Company name must not exceed 100 characters")
    @Schema(description = "Company or organization name", example = "PyraFlow")
    private String company;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    @Schema(description = "User's location", example = "Cairo, Egypt")
    private String location;

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    @Schema(description = "Personal website or portfolio URL", example = "https://omarwageh.dev")
    private String websiteUrl;
}
