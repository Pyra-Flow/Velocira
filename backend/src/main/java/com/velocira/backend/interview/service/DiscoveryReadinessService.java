package com.velocira.backend.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.OpenQuestionStatus;
import com.velocira.backend.interview.model.RiskLevel;
import com.velocira.backend.project.model.ProjectEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, auditable readiness layer. It never manufactures a business
 * rule: it only records supplied evidence, known gaps, and narrow text-level
 * contradictions that a reviewer can see and resolve.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryReadinessService {

    public record Finding(
            String key,
            InterviewCategory category,
            String questionText,
            String reason,
            RiskLevel riskLevel,
            OpenQuestionStatus status,
            boolean material) {
    }

    public record Assessment(
            ObjectNode canonicalBrief,
            ObjectNode snapshot,
            boolean minimumComplete,
            boolean generationReady,
            int answeredRequiredCategories,
            int requiredCategoryCount,
            List<String> blockers,
            List<Finding> findings) {
    }

    private final ObjectMapper objectMapper;
    private final DiscoveryQuestionCatalog questionCatalog;

    public Assessment assess(ProjectEntity project, List<InterviewAnswerEntity> answers) {
        Map<InterviewCategory, InterviewAnswerEntity> current = new EnumMap<>(InterviewCategory.class);
        answers.stream()
                .filter(InterviewAnswerEntity::isCurrent)
                .forEach(answer -> current.put(answer.getCategory(), answer));

        List<Finding> findings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        int answeredRequired = 0;
        int addressedRequired = 0;
        int requiredTotal = 0;

        ObjectNode categoryStates = objectMapper.createObjectNode();
        var requiredCategories = questionCatalog.requiredCategories(project, answers);
        for (DiscoveryQuestionCatalog.QuestionDefinition question : questionCatalog.ordered()) {
            InterviewAnswerEntity answer = current.get(question.category());
            String state = answer == null ? "MISSING" : answer.getDisposition().name();
            categoryStates.put(question.category().name(), state);
            if (!requiredCategories.contains(question.category())) {
                continue;
            }

            requiredTotal++;
            if (answer != null && answer.getDisposition() == InterviewAnswerDisposition.ANSWERED) {
                addressedRequired++;
                if (isLowInformation(answer.getAnswerText()) || isExplicitlyUndecided(answer)) {
                    blockers.add("Clarify the tentative " + display(question.category()) + " answer.");
                    findings.add(new Finding(
                            "incomplete-" + question.key(), question.category(), question.questionText(),
                            "The answer is tentative, explicitly undecided, or too brief to support a requirement without inventing detail.",
                            RiskLevel.HIGH, OpenQuestionStatus.OPEN, true));
                } else {
                    answeredRequired++;
                    findings.add(new Finding(
                            "required-" + question.key(), question.category(), question.questionText(),
                            "This required category has a user-provided answer.", question.riskLevel(),
                            OpenQuestionStatus.RESOLVED, true));
                }
            } else if (answer == null) {
                blockers.add("Answer the " + display(question.category()) + " question.");
                findings.add(new Finding(
                        "required-" + question.key(), question.category(), question.questionText(),
                        "This required category has not been addressed.", RiskLevel.HIGH,
                        OpenQuestionStatus.OPEN, true));
            } else {
                addressedRequired++;
                blockers.add("Resolve or explicitly accept the unknown " + display(question.category()) + " risk.");
                findings.add(new Finding(
                        "required-" + question.key(), question.category(), question.questionText(),
                        "The owner marked this required category " + answer.getDisposition().name().toLowerCase(Locale.ROOT)
                                + "; it remains visible and blocks generation until resolved.",
                        RiskLevel.HIGH, OpenQuestionStatus.ACKNOWLEDGED_UNKNOWN, true));
            }
        }

        addContradictionFinding(current, findings, blockers);

        // Unknown and skipped answers count as addressed for brief review, but
        // remain generation blockers via the visible high-risk finding above.
        boolean minimumComplete = addressedRequired == requiredTotal;
        boolean generationReady = minimumComplete && blockers.isEmpty();
        ObjectNode brief = toCanonicalBrief(current);
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("categoryStates", categoryStates);
        snapshot.put("minimumComplete", minimumComplete);
        snapshot.put("generationReady", generationReady);
        snapshot.put("answeredRequiredCategories", answeredRequired);
        snapshot.put("requiredCategoryCount", requiredTotal);
        ArrayNode blockersNode = snapshot.putArray("blockers");
        blockers.forEach(blockersNode::add);
        return new Assessment(brief, snapshot, minimumComplete, generationReady,
                answeredRequired, requiredTotal, List.copyOf(blockers), List.copyOf(findings));
    }

    private ObjectNode toCanonicalBrief(Map<InterviewCategory, InterviewAnswerEntity> current) {
        ObjectNode brief = objectMapper.createObjectNode();
        putAnswer(brief, "stakeholders", current.get(InterviewCategory.STAKEHOLDERS));
        putAnswer(brief, "problem", current.get(InterviewCategory.PROBLEM));
        putAnswer(brief, "users", current.get(InterviewCategory.USERS));
        putAnswer(brief, "actors", current.get(InterviewCategory.USERS));
        putAnswer(brief, "scope", current.get(InterviewCategory.SCOPE));
        putAnswer(brief, "exclusions", current.get(InterviewCategory.EXCLUSIONS));
        putAnswer(brief, "workflows", current.get(InterviewCategory.WORKFLOWS));
        putAnswer(brief, "businessRules", current.get(InterviewCategory.BUSINESS_RULES));
        putAnswer(brief, "entities", current.get(InterviewCategory.ENTITIES));
        putAnswer(brief, "integrations", current.get(InterviewCategory.INTEGRATIONS));
        putAnswer(brief, "qualityTargets", current.get(InterviewCategory.QUALITY_GOALS));
        putAnswer(brief, "constraints", current.get(InterviewCategory.CONSTRAINTS));
        putAnswer(brief, "risks", current.get(InterviewCategory.RISKS));
        putAnswer(brief, "metrics", current.get(InterviewCategory.METRICS));

        ObjectNode dataAndIntegrations = brief.putObject("dataAndIntegrations");
        putAnswer(dataAndIntegrations, "entities", current.get(InterviewCategory.ENTITIES));
        putAnswer(dataAndIntegrations, "integrations", current.get(InterviewCategory.INTEGRATIONS));
        ArrayNode goals = brief.putArray("goals");
        answerValue(current.get(InterviewCategory.PROBLEM)).ifPresent(goals::add);
        return brief;
    }

    private void putAnswer(ObjectNode target, String field, InterviewAnswerEntity answer) {
        answerValue(answer).ifPresentOrElse(value -> target.put(field, value), () -> target.putNull(field));
    }

    private java.util.Optional<String> answerValue(InterviewAnswerEntity answer) {
        if (answer == null || answer.getDisposition() != InterviewAnswerDisposition.ANSWERED
                || answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(answer.getAnswerText().trim());
    }

    private void addContradictionFinding(
            Map<InterviewCategory, InterviewAnswerEntity> current,
            List<Finding> findings,
            List<String> blockers) {
        String constraints = answerValue(current.get(InterviewCategory.CONSTRAINTS)).orElse("").toLowerCase(Locale.ROOT);
        String integrations = answerValue(current.get(InterviewCategory.INTEGRATIONS)).orElse("").toLowerCase(Locale.ROOT);
        boolean prohibitsExternal = constraints.matches(".*(no cloud|no external api|no third.party|offline only).*" );
        boolean requiresExternal = integrations.matches(".*(cloud|api|webhook|third.party|external service).*" );
        if (prohibitsExternal && requiresExternal) {
            blockers.add("Resolve the conflict between the stated constraints and required integrations.");
            findings.add(new Finding(
                    "contradiction-constraints-integrations", InterviewCategory.CONSTRAINTS,
                    "Do the deployment constraints permit the integrations you listed?",
                    "The constraints prohibit an external/cloud dependency while the integration answer requires one.",
                    RiskLevel.HIGH, OpenQuestionStatus.OPEN, true));
        } else {
            findings.add(new Finding(
                    "contradiction-constraints-integrations", InterviewCategory.CONSTRAINTS,
                    "Do the deployment constraints permit the integrations you listed?",
                    "No deterministic conflict was found between the current constraints and integration answers.",
                    RiskLevel.HIGH, OpenQuestionStatus.RESOLVED, true));
        }
    }

    private String display(InterviewCategory category) {
        return category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean isLowInformation(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() < 12
                || normalized.matches("^(tbd|unknown|not sure|unsure|maybe|not decided( yet)?|to be decided|n/?a)[.!]?$" );
    }

    private boolean isExplicitlyUndecided(InterviewAnswerEntity answer) {
        JsonNode selected = answer.getEvidence().path("selectedOptionKeys");
        if (!selected.isArray()) {
            return false;
        }
        for (JsonNode key : selected) {
            if (key.isTextual() && key.asText().equals("not-decided")) {
                return true;
            }
        }
        return false;
    }
}
