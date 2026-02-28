package com.velocira.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for verifying an email OTP code.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email OTP verification request")
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "User's email address", example = "omar@velocira.com")
    private String email;

    @NotBlank(message = "OTP code is required")
    @Schema(description = "6-digit OTP code sent to email", example = "123456")
    private String otp;
}
