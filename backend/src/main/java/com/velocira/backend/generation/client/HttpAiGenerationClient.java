package com.velocira.backend.generation.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.generation.config.GenerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;

/**
 * HTTP implementation of the internal AI-service boundary. It deliberately
 * carries no provider credential: only the FastAPI service may hold one.
 */
@Slf4j
@Component
public class HttpAiGenerationClient implements AiGenerationClient {

    private final ObjectMapper objectMapper;
    private final GenerationProperties properties;
    private final HttpClient httpClient;

    public HttpAiGenerationClient(ObjectMapper objectMapper, GenerationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                // Uvicorn does not support Java's clear-text HTTP/2 upgrade.
                // Forcing HTTP/1.1 prevents the upgrade probe from consuming
                // the POST body before FastAPI validation.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getAi().getConnectTimeout())
                .build();
    }

    @Override
    public AiGenerationResponse generate(AiGenerationRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint("/v1/generate"))
                    .timeout(properties.getAi().getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Id", correlationId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
            addInternalToken(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failureForHttpStatus(response.statusCode(), response.body());
            }

            AiGenerationResponse body = objectMapper.readValue(response.body(), AiGenerationResponse.class);
            if (!body.success()) {
                AiGenerationResponse.Error error = body.error();
                throw new AiGenerationException(
                        errorCode(error == null ? null : error.code()),
                        error == null || error.message() == null ? "The generation service rejected the request." : error.message(),
                        error != null && error.retryable());
            }
            return body;
        } catch (HttpTimeoutException ex) {
            throw new AiGenerationException(AiFailureCode.TIMEOUT,
                    "The generation service did not respond in time.", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiGenerationException(AiFailureCode.UNAVAILABLE,
                    "The generation worker was interrupted.", true, ex);
        } catch (IOException ex) {
            throw new AiGenerationException(AiFailureCode.UNAVAILABLE,
                    "The generation service is unavailable.", true, ex);
        }
    }

    @Override
    public boolean isReady() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint("/ready"))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            addInternalToken(builder);
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ex) {
            log.debug("AI service readiness check failed: {}", ex.getMessage());
            return false;
        }
    }

    private URI endpoint(String path) {
        String base = properties.getAi().getBaseUrl().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private void addInternalToken(HttpRequest.Builder builder) {
        String token = properties.getAi().getSharedSecret();
        if (token != null && !token.isBlank()) {
            builder.header("X-Internal-Token", token);
        }
    }

    private String correlationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private AiGenerationException failureForHttpStatus(int status, String body) {
        boolean retryable = status == 408 || status == 429 || status >= 500;
        AiFailureCode code = status == 408 ? AiFailureCode.TIMEOUT
                : status >= 500 ? AiFailureCode.UNAVAILABLE
                : status == 422 ? AiFailureCode.INVALID_OUTPUT
                : AiFailureCode.INVALID_REQUEST;
        String message = retryable ? "The generation service is temporarily unavailable."
                : "The generation service could not process this request.";
        try {
            AiGenerationResponse response = objectMapper.readValue(body, AiGenerationResponse.class);
            if (response.error() != null) {
                code = errorCode(response.error().code());
                retryable = response.error().retryable();
                if (response.error().message() != null) {
                    message = response.error().message();
                }
            }
        } catch (JsonProcessingException ignored) {
            // Never expose a raw service body to the end user.
        }
        return new AiGenerationException(code, message, retryable);
    }

    private AiFailureCode errorCode(String value) {
        if (value == null) {
            return AiFailureCode.INTERNAL_ERROR;
        }
        return switch (value.trim().toUpperCase()) {
            case "PROVIDER_TIMEOUT" -> AiFailureCode.TIMEOUT;
            case "PROVIDER_UNAVAILABLE", "PROVIDER_RATE_LIMIT", "SERVICE_NOT_READY" -> AiFailureCode.UNAVAILABLE;
            case "PROVIDER_INVALID_OUTPUT" -> AiFailureCode.INVALID_OUTPUT;
            case "CONTENT_SAFETY_BLOCKED" -> AiFailureCode.SAFETY_REJECTED;
            case "INVALID_REQUEST", "UNAUTHORIZED_CALLER", "PROVIDER_AUTH" -> AiFailureCode.INVALID_REQUEST;
            case "INTERNAL_ERROR" -> AiFailureCode.INTERNAL_ERROR;
            default -> AiFailureCode.INTERNAL_ERROR;
        };
    }
}
