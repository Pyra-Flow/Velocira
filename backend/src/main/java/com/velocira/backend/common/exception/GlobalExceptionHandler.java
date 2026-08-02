package com.velocira.backend.common.exception;

import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.common.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for the entire Velocira platform.
 *
 * <p>
 * Catches all exceptions thrown by controllers and maps them to
 * consistent, user-friendly JSON responses. Handles:
 * </p>
 * <ul>
 * <li>Application-specific {@link BaseException} subclasses</li>
 * <li>Bean validation errors ({@link MethodArgumentNotValidException})</li>
 * <li>Spring Security authentication/authorization failures</li>
 * <li>Malformed request exceptions</li>
 * <li>Unexpected server errors (catch-all)</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ======================== Application Exceptions ========================

    /**
     * Handles all Velocira-specific business exceptions.
     *
     * @param ex      the application exception
     * @param request the HTTP request
     * @return a structured error response
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(
            BaseException ex, HttpServletRequest request) {
        log.warn("Business exception on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getHttpStatus().value()));
    }

    // ======================== Validation Exceptions ========================

    /**
     * Handles bean validation failures from {@code @Valid} annotations.
     *
     * @param ex      the validation exception
     * @param request the HTTP request
     * @return a structured error response with field-level details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetail> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorDetail.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        log.warn("Validation failed on [{}]: {} field error(s)",
                request.getRequestURI(), fieldErrors.size());

        ErrorDetail error = ErrorDetail.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .errors(fieldErrors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles bind exceptions from form data validation.
     *
     * @param ex      the bind exception
     * @param request the HTTP request
     * @return a structured error response with field-level details
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorDetail> handleBindException(
            BindException ex, HttpServletRequest request) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorDetail.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        log.warn("Bind exception on [{}]: {} field error(s)",
                request.getRequestURI(), fieldErrors.size());

        ErrorDetail error = ErrorDetail.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Invalid request parameters")
                .errors(fieldErrors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /** Handles method-level request parameter constraints such as paging bounds. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Invalid request parameter on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("One or more request parameters are invalid", HttpStatus.BAD_REQUEST.value()));
    }

    // ======================== Security Exceptions ========================

    /**
     * Handles authentication failures (bad credentials, expired tokens, etc.).
     *
     * @param ex      the authentication exception
     * @param request the HTTP request
     * @return a 401 error response
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed: " + ex.getMessage(),
                        HttpStatus.UNAUTHORIZED.value()));
    }

    /**
     * Handles bad credentials specifically.
     *
     * @param ex      the bad credentials exception
     * @param request the HTTP request
     * @return a 401 error response
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials on [{}]", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password",
                        HttpStatus.UNAUTHORIZED.value()));
    }

    /**
     * Handles access denied (insufficient permissions).
     *
     * @param ex      the access denied exception
     * @param request the HTTP request
     * @return a 403 error response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to access this resource",
                        HttpStatus.FORBIDDEN.value()));
    }

    // ======================== Request Format Exceptions ========================

    /**
     * Handles malformed JSON request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Malformed request body. Please check your JSON syntax.",
                        HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Handles unsupported HTTP methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported on [{}]: {}", request.getRequestURI(), ex.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                        HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    /**
     * Handles unsupported media types.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Unsupported media type on [{}]: {}", request.getRequestURI(), ex.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Content type '" + ex.getContentType() + "' is not supported",
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()));
    }

    /**
     * Handles missing request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter on [{}]: {}", request.getRequestURI(), ex.getParameterName());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Required parameter '" + ex.getParameterName() + "' is missing",
                        HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Handles type mismatch in method arguments.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch on [{}]: parameter '{}' = '{}'",
                request.getRequestURI(), ex.getName(), ex.getValue());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Parameter '" + ex.getName() + "' has an invalid value",
                        HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Handles 404 - resource not found.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested resource was not found",
                        HttpStatus.NOT_FOUND.value()));
    }

    // ======================== Catch-All ========================

    /**
     * Catches all unhandled exceptions as a safety net.
     *
     * <p>
     * <strong>Important:</strong> Never expose internal error details to clients.
     * Log the full stack trace server-side for debugging.
     * </p>
     *
     * @param ex      the unexpected exception
     * @param request the HTTP request
     * @return a generic 500 error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUnhandled(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on [{}]", request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
