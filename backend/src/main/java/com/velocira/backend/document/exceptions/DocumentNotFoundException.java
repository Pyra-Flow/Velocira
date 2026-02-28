package com.velocira.backend.document.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested document is not found.
 *
 * @author Velocira Team
 * @since 1.0
 */
public class DocumentNotFoundException extends BaseException {

    public DocumentNotFoundException(String identifier) {
        super("Document not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
