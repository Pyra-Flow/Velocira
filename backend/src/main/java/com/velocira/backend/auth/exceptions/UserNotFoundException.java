package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user is not found by email or ID.
 *
 * <p>
 * HTTP Status: 404 Not Found
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class UserNotFoundException extends BaseException {

    public UserNotFoundException(String identifier) {
        super("User not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
