package com.velocira.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all Velocira application exceptions.
 *
 * <p>
 * All module-specific exceptions should extend this class to ensure
 * consistent error handling and HTTP status code propagation through
 * the global exception handler.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Getter
public abstract class BaseException extends RuntimeException {

    /** The HTTP status code associated with this exception. */
    private final HttpStatus httpStatus;

    /**
     * Constructs a new base exception.
     *
     * @param message    the human-readable error message
     * @param httpStatus the HTTP status to return
     */
    protected BaseException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * Constructs a new base exception with a cause.
     *
     * @param message    the human-readable error message
     * @param httpStatus the HTTP status to return
     * @param cause      the underlying cause
     */
    protected BaseException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
