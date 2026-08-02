package com.velocira.backend.documentation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DocumentationPackageException extends BaseException {
    public DocumentationPackageException(String message, HttpStatus status) {
        super(message, status);
    }
}
