package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when too many OTP resend requests are made within the rate limit
 * window.
 *
 * <p>
 * HTTP Status: 429 Too Many Requests
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class OtpRateLimitException extends BaseException {

    public OtpRateLimitException() {
        super("Too many OTP requests. Please wait before requesting a new code.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
