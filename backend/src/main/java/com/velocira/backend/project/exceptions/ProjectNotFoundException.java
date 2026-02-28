package com.velocira.backend.project.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested project is not found.
 *
 * @author Velocira Team
 * @since 1.0
 */
public class ProjectNotFoundException extends BaseException {

    public ProjectNotFoundException(String identifier) {
        super("Project not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
