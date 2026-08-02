package com.velocira.backend.interview.model;

/** Visibility state for a material question; unknown remains visible rather than silently resolved. */
public enum OpenQuestionStatus {
    OPEN,
    ACKNOWLEDGED_UNKNOWN,
    RESOLVED
}
