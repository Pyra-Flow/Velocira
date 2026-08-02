package com.velocira.backend.knowledge.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** Safe, owner-facing state failures for governed evidence and SRS work. */
public class KnowledgeStateException extends BaseException {
    public KnowledgeStateException(String message, HttpStatus status) { super(message, status); }
}
