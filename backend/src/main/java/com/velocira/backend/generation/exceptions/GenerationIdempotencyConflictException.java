package com.velocira.backend.generation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** A reused idempotency key describes a different generation request. */
public class GenerationIdempotencyConflictException extends BaseException {

    public GenerationIdempotencyConflictException() {
        super("This idempotency key was already used for a different generation request.", HttpStatus.CONFLICT);
    }
}
