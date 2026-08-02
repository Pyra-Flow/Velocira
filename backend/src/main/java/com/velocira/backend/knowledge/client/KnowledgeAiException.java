package com.velocira.backend.knowledge.client;

/** Sanitised internal-service failure; controller advice can expose only its safe message. */
public class KnowledgeAiException extends RuntimeException {
    public KnowledgeAiException(String message) { super(message); }
    public KnowledgeAiException(String message, Throwable cause) { super(message, cause); }
}
