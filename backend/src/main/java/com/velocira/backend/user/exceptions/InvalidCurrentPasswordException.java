package com.velocira.backend.user.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a password change attempt provides an incorrect current password.
 *
 * @author Velocira Team
 * @since 1.0
 */
public class InvalidCurrentPasswordException extends BaseException {

    public InvalidCurrentPasswordException() {
        super("The current password you entered is incorrect.", HttpStatus.BAD_REQUEST);
    }
}
