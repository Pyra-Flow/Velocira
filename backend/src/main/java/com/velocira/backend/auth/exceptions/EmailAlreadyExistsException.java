package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts to register with an email that is already in use.
 *
 * <p>
 * HTTP Status: 409 Conflict
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class EmailAlreadyExistsException extends BaseException {

    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists", HttpStatus.CONFLICT);
    }
}
