package com.velocira.backend.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.InterviewSessionStatus;
import com.velocira.backend.interview.model.OpenQuestionStatus;
import com.velocira.backend.interview.model.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Compact typed API contracts for the discovery workspace. */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    public record AnswerRequest(
            @NotBlank @Size(max = 120) String questionKey,
            @NotNull InterviewAnswerDisposition disposition,
            @Size(max = 12_000) String answerText) {
    }

    public record QuestionResponse(
            String questionKey,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            RiskLevel riskLevel) {
    }

    public record AnswerResponse(
            UUID id,
            String questionKey,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            InterviewAnswerDisposition disposition,
            String answerText,
            int revisionNumber,
            boolean current,
            Instant createdAt) {
    }

    public record AssumptionResponse(
            UUID id,
            InterviewCategory category,
            String statement,
            String rationale,
            RiskLevel impact,
            String status,
            boolean material) {
    }

    public record OpenQuestionResponse(
            UUID id,
            String questionKey,
            InterviewCategory category,
            String questionText,
            String reason,
            RiskLevel riskLevel,
            OpenQuestionStatus status,
            boolean material) {
    }

    public record DecisionResponse(
            UUID id,
            InterviewCategory category,
            String statement,
            String rationale,
            String status) {
    }

    public record ReadinessResponse(
            boolean minimumComplete,
            boolean generationReady,
            int answeredRequiredCategories,
            int requiredCategoryCount,
            List<String> blockers,
            JsonNode snapshot) {
    }

    public record BriefResponse(
            int version,
            JsonNode content,
            Instant confirmedAt) {
    }

    public record SessionResponse(
            UUID id,
            UUID projectId,
            InterviewSessionStatus status,
            QuestionResponse nextQuestion,
            List<AnswerResponse> answers,
            List<AssumptionResponse> assumptions,
            List<OpenQuestionResponse> openQuestions,
            List<DecisionResponse> decisions,
            BriefResponse brief,
            ReadinessResponse readiness,
            Instant reopenedAt,
            Instant updatedAt) {
    }

    public record HistoryResponse(List<AnswerResponse> answers) {
    }
}
