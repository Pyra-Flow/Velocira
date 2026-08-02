package com.velocira.backend.interview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.OpenQuestionEntity;
import com.velocira.backend.project.model.ProjectEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow internal adapter for the AI question planner. The remote response may
 * select only a server-owned catalog key; it never contributes requirement or
 * business-rule wording directly to the brief.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpDiscoveryPlannerClient {

    private final ObjectMapper objectMapper;
    private final GenerationProperties properties;

    public Optional<String> selectQuestionKey(
            ProjectEntity project,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions) {
        if (!properties.getAi().isDiscoveryPlannerEnabled()) {
            return Optional.empty();
        }
        try {
            PlannerRequest payload = new PlannerRequest(
                    new ProjectContext(project.getId(), project.getName(), project.getDescription(), project.getType().name()),
                    answers.stream().map(answer -> new AnswerContext(
                            answer.getCategory().name(), answer.getDisposition().name(), answer.getAnswerText())).toList(),
                    openQuestions.stream().filter(OpenQuestionEntity::isMaterial).map(OpenQuestionEntity::getQuestionKey).toList());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint("/v1/discovery/plan"))
                    .timeout(properties.getAi().getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Id", correlationId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            String token = properties.getAi().getSharedSecret();
            if (token != null && !token.isBlank()) {
                request.header("X-Internal-Token", token);
            }
            HttpResponse<String> response = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(properties.getAi().getConnectTimeout())
                    .build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("Discovery planner returned HTTP {}; using deterministic question order", response.statusCode());
                return Optional.empty();
            }
            PlannerResponse body = objectMapper.readValue(response.body(), PlannerResponse.class);
            return body.nextQuestion() == null || body.nextQuestion().key() == null
                    ? Optional.empty()
                    : Optional.of(body.nextQuestion().key());
        } catch (IOException ex) {
            log.debug("Discovery planner is unavailable; using deterministic question order: {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private URI endpoint(String path) {
        return URI.create(properties.getAi().getBaseUrl().replaceAll("/+$", "") + path);
    }

    private String correlationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private record PlannerRequest(
            ProjectContext project,
            List<AnswerContext> answers,
            @JsonProperty("visible_open_question_keys") List<String> visibleOpenQuestionKeys) {
    }

    private record ProjectContext(UUID id, String name, String description, String type) {
    }

    private record AnswerContext(String category, String disposition, @JsonProperty("answer_text") String answerText) {
    }

    private record PlannerResponse(@JsonProperty("next_question") PlannedQuestion nextQuestion) {
    }

    private record PlannedQuestion(String key) {
    }
}
