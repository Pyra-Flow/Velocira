package com.velocira.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Google OAuth2 token-based authentication.
 *
 * <p>
 * The frontend obtains a Google ID token and sends it to the backend.
 * The backend validates the token with Google's servers and either creates
 * a new account or logs in the existing user.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Google OAuth2 login request")
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    @Schema(description = "Google ID token obtained from frontend OAuth flow")
    private String idToken;
}
