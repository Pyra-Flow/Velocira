package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when Google OAuth2 token validation fails.
 *
 * <p>
 * HTTP Status: 401 Unauthorized
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class GoogleAuthenticationException extends BaseException {

    public GoogleAuthenticationException() {
        super("Failed to authenticate with Google. Please try again.", HttpStatus.UNAUTHORIZED);
    }

    public GoogleAuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
