package com.velocira.backend.generation.client;

/** Narrow boundary between Spring orchestration and the internal AI service. */
public interface AiGenerationClient {

    AiGenerationResponse generate(AiGenerationRequest request);

    boolean isReady();
}
