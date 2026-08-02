package com.velocira.backend.generation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** A request exceeded a generation rate, concurrency, or spend guardrail. */
public class GenerationGuardrailException extends BaseException {

    public GenerationGuardrailException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
