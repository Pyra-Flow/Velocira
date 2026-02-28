package com.velocira.backend.admin.dto;

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
 * Admin-facing user response DTO with full account details.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin user response with full account details")
public class AdminUserResponse {

    @Schema(description = "User ID")
    private UUID id;

    @Schema(description = "Full name")
    private String fullName;

    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Role", example = "USER")
    private Role role;

    @Schema(description = "Auth provider", example = "LOCAL")
    private AuthProvider authProvider;

    @Schema(description = "Email verified")
    private boolean emailVerified;

    @Schema(description = "Account locked")
    private boolean accountLocked;

    @Schema(description = "Account enabled")
    private boolean enabled;

    @Schema(description = "Last login timestamp")
    private Instant lastLoginAt;

    @Schema(description = "University name")
    private String universityName;

    @Schema(description = "Number of projects owned")
    private long projectCount;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last updated timestamp")
    private Instant updatedAt;
}
