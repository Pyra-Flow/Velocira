package com.velocira.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for resending an email verification OTP.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resend email verification OTP request")
public class ResendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "User's email address", example = "omar@velocira.com")
    private String email;
}
