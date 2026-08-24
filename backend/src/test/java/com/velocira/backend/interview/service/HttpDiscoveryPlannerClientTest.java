package com.velocira.backend.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpDiscoveryPlannerClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsTheFullAiServiceSchemaAndItsValidatedDeterministicOptions() throws IOException {
        String response = """
                {
                  "planner": "deterministic-discovery-strategist-v3",
                  "model": "deterministic",
                  "next_question": {
                    "key": "problem",
                    "category": "PROBLEM",
                    "question_text": "What is the primary operational problem preventing prospective clients from completing legal intake and staff from reviewing submissions?",
                    "why_we_ask": "This separates the outcome worth funding from possible features and gives the SRS a measurable purpose.",
                    "risk_level": "HIGH",
                    "allows_multiple": false,
                    "options": [
                      {"key":"availability-conflicts","label":"Prevent availability conflicts","description":"Protects confidentiality by limiting access until the practice confirms it can proceed."},
                      {"key":"slow-confirmation","label":"Shorten confirmation time","description":"For the described secure client intake portal, prioritizes response ownership, deadlines, and automatic status updates."},
                      {"key":"missed-follow-up","label":"Prevent missed follow-up","description":"For the described secure client intake portal, prioritizes reliable reminders and recovery when messages fail."},
                      {"key":"not-decided","label":"Not decided yet","description":"Requires owner confirmation."}
                    ]
                  },
                  "selection_reason": "Selected problem because it is the highest-value unanswered gap.",
                  "missing_requirement": "A prioritized problem and observable improvement signal.",
                  "source_context": ["project:title","project:description","project:type"],
                  "confirmed_context_used": ["Project description supplied by the owner"],
                  "assumptions_to_validate": [],
                  "candidate_scores": [
                    {"key":"problem","category":"PROBLEM","score":7940,"reasons":["anchors downstream decisions"]}
                  ],
                  "suggestions": [],
                  "assumptions": []
                }
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/discovery/plan", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        GenerationProperties properties = new GenerationProperties();
        properties.getAi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getAi().setDiscoveryPlannerEnabled(true);
        properties.getAi().setDiscoveryPlannerTimeout(Duration.ofSeconds(5));
        HttpDiscoveryPlannerClient client = new HttpDiscoveryPlannerClient(new ObjectMapper(), properties);
        ProjectEntity project = ProjectEntity.builder()
                .name("Legal Intake Portal")
                .description("A secure client intake portal for a small legal practice where prospective clients check eligibility, upload case documents, request an appointment, and staff review submissions before accepting a case.")
                .type(ProjectType.WEB_APP)
                .build();
        project.setId(UUID.randomUUID());
        DiscoveryQuestionCatalog.QuestionDefinition candidate = new DiscoveryQuestionCatalog()
                .requireByKey("problem");

        var planned = client.planQuestion(
                project, List.of(), List.of(), List.of(), List.of(candidate), Set.of(InterviewCategory.PROBLEM));

        assertThat(planned).isPresent();
        assertThat(planned.orElseThrow().options()).hasSize(4);
        assertThat(planned.orElseThrow().planner()).isEqualTo("deterministic-discovery-strategist-v3");
    }
}
