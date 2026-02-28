package com.velocira.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for refreshing an access token using a refresh token.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token refresh request")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The refresh token to exchange for a new access token")
    private String refreshToken;
}
