package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the maximum number of OTP validation attempts is exceeded.
 *
 * <p>
 * HTTP Status: 429 Too Many Requests
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class OtpMaxAttemptsException extends BaseException {

    public OtpMaxAttemptsException() {
        super("Maximum OTP validation attempts exceeded. Please request a new code.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
