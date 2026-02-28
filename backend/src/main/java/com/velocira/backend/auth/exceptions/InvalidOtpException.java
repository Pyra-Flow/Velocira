package com.velocira.backend.auth.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an OTP code is invalid, expired, or already used.
 *
 * <p>
 * HTTP Status: 400 Bad Request
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class InvalidOtpException extends BaseException {

    public InvalidOtpException() {
        super("Invalid or expired OTP code. Please request a new one.", HttpStatus.BAD_REQUEST);
    }

    public InvalidOtpException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
