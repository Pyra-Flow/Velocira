package com.velocira.backend.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.generation.config.GenerationProperties;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** HTTP adapter for the governed FastAPI RAG endpoints. */
@Component
public class HttpKnowledgeAiClient implements KnowledgeAiClient {
    private final ObjectMapper objectMapper;
    private final GenerationProperties properties;
    private final HttpClient httpClient;

    public HttpKnowledgeAiClient(ObjectMapper objectMapper, GenerationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getAi().getConnectTimeout())
                .build();
    }

    @Override
    public void index(IndexChunk chunk) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode body = payload.putObject("chunk");
        body.put("project_id", chunk.projectId().toString());
        body.put("owner_id", chunk.ownerId().toString());
        body.put("source_id", chunk.sourceId().toString());
        body.put("chunk_id", chunk.chunkId().toString());
        body.put("source_title", chunk.sourceTitle());
        body.put("content", chunk.content());
        call("/v1/retrieval/index", payload, 200);
    }

    @Override
    public void delete(UUID projectId, UUID ownerId, UUID chunkId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("project_id", projectId.toString());
        payload.put("owner_id", ownerId.toString());
        payload.put("chunk_id", chunkId.toString());
        call("/v1/retrieval/delete", payload, 204);
    }

    @Override
    public List<RetrievedEvidence> retrieve(UUID projectId, UUID ownerId, String query, int limit) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("project_id", projectId.toString());
        payload.put("owner_id", ownerId.toString());
        payload.put("query", query);
        payload.put("limit", limit);
        JsonNode hits = call("/v1/retrieval/search", payload, 200).path("hits");
        List<RetrievedEvidence> result = new ArrayList<>();
        if (!hits.isArray()) throw new KnowledgeAiException("The evidence retrieval service returned an invalid response.");
        for (JsonNode hit : hits) {
            try {
                result.add(new RetrievedEvidence(
                        UUID.fromString(hit.path("source_id").asText()), UUID.fromString(hit.path("chunk_id").asText()),
                        hit.path("source_title").asText(), hit.path("content").asText(), hit.path("score").asDouble()));
            } catch (IllegalArgumentException ex) {
                throw new KnowledgeAiException("The evidence retrieval service returned an invalid citation.", ex);
            }
        }
        return result;
    }

    @Override
    public SrsGenerationResult generate(SrsGenerationRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode project = payload.putObject("project");
        project.put("id", request.projectId().toString());
        project.put("name", request.projectName());
        project.put("description", request.projectDescription());
        project.put("type", request.projectType());
        payload.set("confirmed_brief", request.confirmedBrief());
        ObjectNode profile = payload.putObject("profile");
        profile.put("key", request.profileKey());
        profile.put("name", request.profileName());
        profile.set("controls", request.controls());
        payload.put("generation_mode", request.generationMode());
        ArrayNode evidence = payload.putArray("evidence");
        request.evidence().forEach(hit -> {
            ObjectNode item = evidence.addObject();
            item.put("source_id", hit.sourceId().toString());
            item.put("chunk_id", hit.chunkId().toString());
            item.put("source_title", hit.sourceTitle());
            item.put("content", hit.content());
            item.put("score", hit.score());
        });
        JsonNode response = call("/v1/srs/generate", payload, 200);
        if (response.path("artifact").isMissingNode() || response.path("validation").isMissingNode()) {
            throw new KnowledgeAiException("The SRS generation service returned an invalid response.");
        }
        return new SrsGenerationResult(response.path("provider").asText(), response.path("model").asText(),
                response.path("prompt_version").asText(), response.path("artifact"), response.path("validation"));
    }

    private JsonNode call(String path, JsonNode payload, int expectedStatus) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(path))
                    .timeout(properties.getAi().getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Id", correlationId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (properties.getAi().getSharedSecret() != null && !properties.getAi().getSharedSecret().isBlank()) {
                request.header("X-Internal-Token", properties.getAi().getSharedSecret());
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus) {
                throw new KnowledgeAiException(safeMessage(path, response.statusCode()));
            }
            return expectedStatus == 204 ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KnowledgeAiException(serviceLabel(path) + " was interrupted. No change was saved.", ex);
        } catch (HttpTimeoutException ex) {
            throw new KnowledgeAiException(serviceLabel(path) + " exceeded its configured processing time. No change was saved.", ex);
        } catch (IOException ex) {
            throw new KnowledgeAiException(serviceLabel(path) + " is unavailable. No change was saved.", ex);
        }
    }

    private URI endpoint(String path) {
        return URI.create(properties.getAi().getBaseUrl().replaceAll("/+$", "") + path);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String safeMessage(String path, int status) {
        if (status == 422 && path.startsWith("/v1/srs/")) {
            return "The generated SRS did not pass the governed quality checks. No change was saved.";
        }
        if (status == 422) return "The evidence was insufficient or failed validation.";
        if (status == 401 || status == 403) return "The internal evidence service is not securely configured.";
        return serviceLabel(path) + " is temporarily unavailable. No change was saved.";
    }

    private String serviceLabel(String path) {
        return path.startsWith("/v1/srs/") ? "The SRS generation service" : "The evidence service";
    }
}
