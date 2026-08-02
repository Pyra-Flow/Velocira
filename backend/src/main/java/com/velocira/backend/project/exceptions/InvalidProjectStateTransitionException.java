package com.velocira.backend.project.exceptions;

import com.velocira.backend.common.exception.BaseException;
import com.velocira.backend.project.model.ProjectStatus;
import org.springframework.http.HttpStatus;

/** Thrown when a requested lifecycle change is not permitted. */
public class InvalidProjectStateTransitionException extends BaseException {

    public InvalidProjectStateTransitionException(ProjectStatus from, ProjectStatus to) {
        super("Project cannot move from " + from + " to " + to + ".", HttpStatus.CONFLICT);
    }
}
