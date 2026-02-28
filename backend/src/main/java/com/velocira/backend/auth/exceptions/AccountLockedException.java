package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user account has been locked by an administrator.
 *
 * <p>
 * HTTP Status: 403 Forbidden
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
        super("Your account has been suspended. Please contact support for assistance.", HttpStatus.FORBIDDEN);
    }
}
