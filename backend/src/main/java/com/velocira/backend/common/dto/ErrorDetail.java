package com.velocira.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Detailed error response for validation and business-rule failures.
 *
 * <p>
 * Extends the standard {@link ApiResponse} pattern with field-level errors:
 * 
 * <pre>
 * {
 *   "success": false,
 *   "status": 400,
 *   "message": "Validation failed",
 *   "errors": [
 *     { "field": "email", "message": "Email is already registered" }
 *   ],
 *   "path": "/api/v1/auth/register",
 *   "timestamp": "2026-02-25T12:00:00Z"
 * }
 * </pre>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed error response")
public class ErrorDetail {

    @Schema(description = "Whether the operation succeeded", example = "false")
    private boolean success;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Human-readable error summary", example = "Validation failed")
    private String message;

    @Schema(description = "List of field-level errors")
    private List<FieldError> errors;

    @Schema(description = "Request path that caused the error", example = "/api/v1/auth/register")
    private String path;

    @Schema(description = "ISO-8601 timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Represents a single field-level validation error.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Field-level error detail")
    public static class FieldError {

        @Schema(description = "Field name", example = "email")
        private String field;

        @Schema(description = "Error message for this field", example = "Email is already registered")
        private String message;
    }
}
