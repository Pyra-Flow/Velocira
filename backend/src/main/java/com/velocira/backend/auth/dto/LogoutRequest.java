package com.velocira.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for logging out (invalidating a specific refresh token /
 * session).
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Logout request — invalidates the provided refresh token")
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The refresh token to invalidate")
    private String refreshToken;
}
