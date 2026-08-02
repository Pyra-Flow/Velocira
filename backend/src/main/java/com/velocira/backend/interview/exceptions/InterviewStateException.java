package com.velocira.backend.interview.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InterviewStateException extends BaseException {
    public InterviewStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
