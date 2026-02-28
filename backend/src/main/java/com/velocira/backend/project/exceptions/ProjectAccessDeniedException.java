package com.velocira.backend.project.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts to access a project that belongs to another user.
 *
 * @author Velocira Team
 * @since 1.0
 */
public class ProjectAccessDeniedException extends BaseException {

    public ProjectAccessDeniedException() {
        super("You do not have permission to access this project.", HttpStatus.FORBIDDEN);
    }

    public ProjectAccessDeniedException(String projectId) {
        super("Access denied to project: " + projectId, HttpStatus.FORBIDDEN);
    }
}
