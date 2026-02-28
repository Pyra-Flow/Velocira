package com.velocira.backend.user.dto;

import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Comprehensive user profile DTO returned by the User module endpoints.
 *
 * <p>
 * Extends the auth-level {@code UserDto} with additional profile fields
 * such as bio, job title, company, location, and website.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full user profile response")
public class UserProfileResponse {

    @Schema(description = "User's unique identifier")
    private UUID id;

    @Schema(description = "User's full name", example = "Omar Elrfaay")
    private String fullName;

    @Schema(description = "User's email address", example = "omar@velocira.com")
    private String email;

    @Schema(description = "User's role", example = "USER")
    private Role role;

    @Schema(description = "Authentication provider", example = "LOCAL")
    private AuthProvider authProvider;

    @Schema(description = "Whether email has been verified")
    private boolean emailVerified;

    @Schema(description = "University name", example = "Cairo University")
    private String universityName;

    @Schema(description = "Short user bio", example = "Full-stack developer passionate about AI")
    private String bio;

    @Schema(description = "Professional job title", example = "Lead Software Engineer")
    private String jobTitle;

    @Schema(description = "Company or organization name", example = "PyraFlow")
    private String company;

    @Schema(description = "User's location", example = "Cairo, Egypt")
    private String location;

    @Schema(description = "Personal website URL", example = "https://omarwageh.dev")
    private String websiteUrl;

    @Schema(description = "Total number of projects owned by this user")
    private long projectCount;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last login timestamp")
    private Instant lastLoginAt;

    @Schema(description = "Last profile update timestamp")
    private Instant updatedAt;
}
