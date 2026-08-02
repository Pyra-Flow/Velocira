package com.velocira.backend.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/** Internal-only retrieval/SRS boundary. Provider credentials remain in FastAPI. */
public interface KnowledgeAiClient {
    void index(IndexChunk chunk);
    void delete(UUID projectId, UUID ownerId, UUID chunkId);
    List<RetrievedEvidence> retrieve(UUID projectId, UUID ownerId, String query, int limit);
    SrsGenerationResult generate(SrsGenerationRequest request);

    record IndexChunk(UUID projectId, UUID ownerId, UUID sourceId, UUID chunkId,
                      String sourceTitle, String content) { }
    record RetrievedEvidence(UUID sourceId, UUID chunkId, String sourceTitle, String content, double score) { }
    record SrsGenerationRequest(UUID projectId, String projectName, String projectDescription, String projectType,
                                JsonNode confirmedBrief, String profileKey, String profileName, JsonNode controls,
                                List<RetrievedEvidence> evidence) { }
    record SrsGenerationResult(String provider, String model, String promptVersion,
                               JsonNode artifact, JsonNode validation) { }
}
