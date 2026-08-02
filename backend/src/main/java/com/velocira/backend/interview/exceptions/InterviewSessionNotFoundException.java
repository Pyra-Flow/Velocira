package com.velocira.backend.interview.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InterviewSessionNotFoundException extends BaseException {
    public InterviewSessionNotFoundException() {
        super("Start the discovery interview before requesting its summary.", HttpStatus.NOT_FOUND);
    }
}
