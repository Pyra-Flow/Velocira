package com.velocira.backend.generation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** A generation request cannot safely be accepted in its current form. */
public class GenerationRequestInvalidException extends BaseException {

    public GenerationRequestInvalidException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
