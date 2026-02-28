package com.velocira.backend.document.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a document of the same type already exists in a project.
 *
 * @author Velocira Team
 * @since 1.0
 */
public class DocumentAlreadyExistsException extends BaseException {

    public DocumentAlreadyExistsException(String projectName, String documentType) {
        super("Document of type '" + documentType + "' already exists in project '" + projectName + "'",
                HttpStatus.CONFLICT);
    }
}
