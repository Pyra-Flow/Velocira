package com.velocira.backend.auth.dto;

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
 * DTO representing a user in API responses.
 *
 * <p>
 * This DTO contains only the fields safe to expose to clients.
 * Sensitive fields like password hash are never included.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User details returned in API responses")
public class UserDto {

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

    @Schema(description = "University name (optional)", example = "Cairo University")
    private String universityName;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last login timestamp")
    private Instant lastLoginAt;
}
