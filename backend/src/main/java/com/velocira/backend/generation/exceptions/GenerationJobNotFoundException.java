package com.velocira.backend.generation.exceptions;

import com.velocira.backend.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/** A requested job does not exist in the caller's project workspace. */
public class GenerationJobNotFoundException extends BaseException {

    public GenerationJobNotFoundException(String id) {
        super("Generation job not found: " + id, HttpStatus.NOT_FOUND);
    }
}
