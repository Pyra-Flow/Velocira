package com.velocira.backend.generation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** A requested operation is incompatible with the durable job lifecycle. */
public class GenerationJobStateException extends BaseException {

    public GenerationJobStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
