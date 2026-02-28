package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user tries to log in but their email has not been verified.
 *
 * <p>
 * HTTP Status: 403 Forbidden
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class EmailNotVerifiedException extends BaseException {

    public EmailNotVerifiedException() {
        super("Email address has not been verified. Please check your inbox for the verification code.",
                HttpStatus.FORBIDDEN);
    }
}
