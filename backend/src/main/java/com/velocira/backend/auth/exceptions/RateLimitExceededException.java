package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an API rate limit is exceeded.
 *
 * <p>
 * HTTP Status: 429 Too Many Requests
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException() {
        super("Too many requests. Please slow down and try again later.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
