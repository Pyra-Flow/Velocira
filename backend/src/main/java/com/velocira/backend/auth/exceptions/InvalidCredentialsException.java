package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails due to invalid credentials.
 *
 * <p>
 * HTTP Status: 401 Unauthorized
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
