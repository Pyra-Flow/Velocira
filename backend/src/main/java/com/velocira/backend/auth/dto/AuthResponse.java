package com.velocira.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO containing JWT tokens after successful authentication.
 *
 * <p>
 * Returned after login, registration, token refresh, and OAuth2 flows.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authentication token response")
public class AuthResponse {

    @Schema(description = "Short-lived JWT access token (15 min)")
    private String accessToken;

    @Schema(description = "Long-lived refresh token (7 days)")
    private String refreshToken;

    @Schema(description = "Token type", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Access token expiry in seconds", example = "900")
    private long expiresIn;

    @Schema(description = "Authenticated user details")
    private UserDto user;
}
