package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a refresh token is invalid, expired, or revoked.
 *
 * <p>
 * HTTP Status: 401 Unauthorized
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class InvalidTokenException extends BaseException {

    public InvalidTokenException() {
        super("Invalid or expired token. Please log in again.", HttpStatus.UNAUTHORIZED);
    }

    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
