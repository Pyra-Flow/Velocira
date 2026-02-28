package com.velocira.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Unified API response wrapper for all endpoints.
 *
 * <p>
 * Provides a consistent JSON structure across the entire platform:
 * 
 * <pre>
 * {
 *   "success": true,
 *   "status": 200,
 *   "message": "Operation completed successfully",
 *   "data": { ... },
 *   "timestamp": "2026-02-25T12:00:00Z"
 * }
 * </pre>
 *
 * @param <T> the type of the response payload
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;

    @Schema(description = "HTTP status code", example = "200")
    private int status;

    @Schema(description = "Human-readable message", example = "Operation completed successfully")
    private String message;

    @Schema(description = "Response payload")
    private T data;

    @Schema(description = "ISO-8601 timestamp of the response")
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Creates a successful response with data.
     *
     * @param data    the payload
     * @param message a human-readable success message
     * @param status  the HTTP status code
     * @param <T>     the type of the payload
     * @return a new {@link ApiResponse} instance
     */
    public static <T> ApiResponse<T> success(T data, String message, int status) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(status)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a successful 200 OK response.
     *
     * @param data    the payload
     * @param message a human-readable success message
     * @param <T>     the type of the payload
     * @return a new {@link ApiResponse} instance
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return success(data, message, 200);
    }

    /**
     * Creates a successful 200 OK response with default message.
     *
     * @param data the payload
     * @param <T>  the type of the payload
     * @return a new {@link ApiResponse} instance
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Operation completed successfully", 200);
    }

    /**
     * Creates an error response without data.
     *
     * @param message a human-readable error message
     * @param status  the HTTP status code
     * @param <T>     the type of the payload (always null for errors)
     * @return a new {@link ApiResponse} instance
     */
    public static <T> ApiResponse<T> error(String message, int status) {
        return ApiResponse.<T>builder()
                .success(false)
                .status(status)
                .message(message)
                .data(null)
                .timestamp(Instant.now())
                .build();
    }
}
