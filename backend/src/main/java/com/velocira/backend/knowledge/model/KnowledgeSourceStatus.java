package com.velocira.backend.knowledge.model;

/** A source cannot be retrieved until the owner has explicitly approved it. */
public enum KnowledgeSourceStatus {
    PENDING_REVIEW,
    APPROVED,
    QUARANTINED,
    REJECTED,
    EXPIRED,
    DELETED
}
