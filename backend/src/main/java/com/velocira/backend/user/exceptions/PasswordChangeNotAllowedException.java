package com.velocira.backend.user.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a password change is attempted on an OAuth-only account
 * (which has no local password to change).
 *
 * @author Velocira Team
 * @since 1.0
 */
public class PasswordChangeNotAllowedException extends BaseException {

    public PasswordChangeNotAllowedException() {
        super("Password change is not available for accounts registered via social login.",
                HttpStatus.BAD_REQUEST);
    }
}
