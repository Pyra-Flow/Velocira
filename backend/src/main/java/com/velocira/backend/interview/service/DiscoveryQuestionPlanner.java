package com.velocira.backend.interview.service;

import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.InterviewSessionEntity;
import com.velocira.backend.interview.model.OpenQuestionEntity;
import com.velocira.backend.knowledge.model.KnowledgeSourceStatus;
import com.velocira.backend.knowledge.repository.KnowledgeSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Chooses and safely tailors one high-value unanswered discovery question. */
@Service
@RequiredArgsConstructor
public class DiscoveryQuestionPlanner {

    private final DiscoveryQuestionCatalog questionCatalog;
    private final HttpDiscoveryPlannerClient plannerClient;
    private final KnowledgeSourceRepository knowledgeSourceRepository;

    public InterviewDtos.QuestionResponse nextQuestion(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions) {
        if (session.getReadinessSnapshot().path("generationReady").asBoolean(false)) {
            return null;
        }
        Set<String> answeredKeys = answers.stream()
                .map(InterviewAnswerEntity::getQuestionKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<InterviewCategory> answeredCategories = answers.stream()
                .map(InterviewAnswerEntity::getCategory)
                .collect(java.util.stream.Collectors.toSet());
        Set<InterviewCategory> followUpCategories = new java.util.HashSet<>(openQuestions.stream()
                .filter(OpenQuestionEntity::isMaterial)
                .filter(question -> !question.getStatus().name().equals("RESOLVED"))
                .filter(question -> answeredCategories.contains(question.getCategory()))
                .map(OpenQuestionEntity::getCategory)
                .collect(java.util.stream.Collectors.toSet()));
        answers.stream().filter(answer -> answer.getDisposition().name().equals("ANSWERED"))
                .filter(answer -> isLowInformation(answer.getAnswerText()) || isExplicitlyUndecided(answer))
                .map(InterviewAnswerEntity::getCategory)
                .forEach(followUpCategories::add);
        List<DiscoveryQuestionCatalog.QuestionDefinition> candidates = questionCatalog.ordered().stream()
                .filter(question -> !answeredKeys.contains(question.key())
                        || followUpCategories.contains(question.category()))
                .filter(question -> !answeredCategories.contains(question.category())
                        || followUpCategories.contains(question.category()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        Set<InterviewCategory> required = questionCatalog.requiredCategories(session.getProject(), answers);
        List<HttpDiscoveryPlannerClient.EvidenceContext> evidence = approvedEvidence(session);
        return plannerClient.planQuestion(session.getProject(), answers, openQuestions, evidence, candidates, required)
                .map(this::toResponse)
                .orElseGet(() -> deterministicPlan(session, answers, openQuestions, evidence, candidates, required));
    }

    private InterviewDtos.QuestionResponse deterministicPlan(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            List<HttpDiscoveryPlannerClient.EvidenceContext> evidence,
            List<DiscoveryQuestionCatalog.QuestionDefinition> candidates,
            Set<InterviewCategory> required) {
        DiscoveryQuestionCatalog.QuestionDefinition selected = candidates.stream()
                .max(Comparator.comparingInt(question -> priority(
                        question, session, answers, openQuestions, required, candidates)))
                .orElse(candidates.getFirst());
        DiscoveryQuestionCatalog.QuestionDefinition tailored = questionCatalog.tailorForContext(
                selected, session.getProject(), answers);
        OpenQuestionEntity followUp = openQuestions.stream()
                .filter(OpenQuestionEntity::isMaterial)
                .filter(item -> item.getCategory() == selected.category())
                .filter(item -> !item.getStatus().name().equals("RESOLVED"))
                .filter(item -> answers.stream().anyMatch(answer -> answer.getCategory() == selected.category()))
                .findFirst().orElse(null);
        boolean incompleteAnswer = answers.stream().anyMatch(answer -> answer.getCategory() == selected.category()
                && answer.getDisposition().name().equals("ANSWERED")
                && (isLowInformation(answer.getAnswerText()) || isExplicitlyUndecided(answer)));
        if (followUp != null || incompleteAnswer) {
            String followUpText = followUp != null && followUp.getQuestionKey().startsWith("contradiction-")
                    ? "The current integration and deployment answers point in different directions; which boundary should govern the first release, and what must change to satisfy it?"
                    : incompleteFollowUp(selected.category());
            tailored = new DiscoveryQuestionCatalog.QuestionDefinition(
                    tailored.key(), tailored.category(), followUpText,
                    "This follow-up resolves a visible gap without treating the earlier incomplete answer as a settled fact.",
                    tailored.riskLevel(), tailored.required(), tailored.allowsMultiple(), tailored.options());
        }
        List<String> sources = sourceContext(answers, openQuestions, evidence);
        List<InterviewDtos.CandidateScoreResponse> scores = candidates.stream()
                .map(candidate -> new InterviewDtos.CandidateScoreResponse(
                        candidate.key(), candidate.category(),
                        priority(candidate, session, answers, openQuestions, required, candidates),
                        priorityReasons(candidate, session, answers, openQuestions, required)))
                .sorted(Comparator.comparingInt(InterviewDtos.CandidateScoreResponse::score).reversed())
                .toList();
        return tailored.toResponse(
                selectionReason(tailored.category(), session, openQuestions),
                missingRequirement(tailored.category()),
                sources,
                confirmedContext(session, answers, evidence),
                assumptionsToValidate(tailored.category(), session, answers),
                scores,
                "deterministic-discovery-strategist-v3",
                "deterministic");
    }

    private int priority(
            DiscoveryQuestionCatalog.QuestionDefinition question,
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            Set<InterviewCategory> required,
            List<DiscoveryQuestionCatalog.QuestionDefinition> candidates) {
        int score = 1_000 - candidates.indexOf(question);
        if (question.category() == InterviewCategory.PROBLEM
                && answers.stream().noneMatch(answer -> answer.getCategory() == InterviewCategory.PROBLEM)) {
            score += 5_000;
        }
        if (required.contains(question.category())) {
            score += 2_000;
        }
        if (question.riskLevel().name().equals("HIGH")) {
            score += 300;
        }
        if (openQuestions.stream().anyMatch(item -> item.isMaterial()
                && item.getCategory() == question.category()
                && !item.getStatus().name().equals("RESOLVED"))
                && answers.stream().anyMatch(answer -> answer.getCategory() == question.category())) {
            score += 6_000;
        }
        if (answers.stream().anyMatch(answer -> answer.getCategory() == question.category()
                && answer.getDisposition().name().equals("ANSWERED")
                && (isLowInformation(answer.getAnswerText()) || isExplicitlyUndecided(answer)))) {
            score += 3_000;
        }
        String context = contextText(session, answers);
        Set<InterviewCategory> answeredCategories = answers.stream()
                .filter(answer -> answer.getDisposition().name().equals("ANSWERED"))
                .map(InterviewAnswerEntity::getCategory)
                .collect(java.util.stream.Collectors.toSet());
        if (question.category() == InterviewCategory.USERS
                && answeredCategories.contains(InterviewCategory.PROBLEM)) {
            score += 700;
        }
        if (question.category() == InterviewCategory.SCOPE
                && answeredCategories.containsAll(Set.of(InterviewCategory.PROBLEM, InterviewCategory.USERS))) {
            score += 600;
        }
        if (question.category() == InterviewCategory.WORKFLOWS
                && !answeredCategories.contains(InterviewCategory.USERS)) {
            score -= 900;
        }
        if (question.category() == InterviewCategory.WORKFLOWS
                && !answeredCategories.contains(InterviewCategory.SCOPE)) {
            score -= 400;
        }
        if (question.category() == InterviewCategory.WORKFLOWS
                && answeredCategories.containsAll(Set.of(
                        InterviewCategory.PROBLEM, InterviewCategory.USERS, InterviewCategory.SCOPE))) {
            score += 700;
        }
        if (question.category() == InterviewCategory.METRICS
                && answeredCategories.contains(InterviewCategory.QUALITY_GOALS)
                && answeredCategories.containsAll(required.stream().filter(Set.of(
                        InterviewCategory.STAKEHOLDERS, InterviewCategory.EXCLUSIONS, InterviewCategory.ENTITIES,
                        InterviewCategory.INTEGRATIONS, InterviewCategory.RISKS, InterviewCategory.BUSINESS_RULES)::contains)
                        .collect(java.util.stream.Collectors.toSet()))) {
            score += 700;
        }
        score += switch (question.category()) {
            case STAKEHOLDERS -> matches(context, "approval", "owner", "enterprise", "team", "role") * 180;
            case ENTITIES -> matches(context, "data", "record", "document", "patient", "transaction", "analytics") * 180;
            case INTEGRATIONS -> matches(context, "api", "integration", "webhook", "payment", "crm", "erp", "device") * 180;
            case RISKS -> matches(context, "health", "finance", "payment", "regulated", "ai", "security", "privacy") * 180;
            case BUSINESS_RULES -> matches(context, "approval", "eligibility", "payment", "subscription", "compliance",
                    "role", "cutoff", "threshold", "override", "cancellation") * 180;
            default -> 0;
        };
        return score;
    }

    private InterviewDtos.QuestionResponse toResponse(HttpDiscoveryPlannerClient.PlannedQuestion planned) {
        return new InterviewDtos.QuestionResponse(
                planned.key(), planned.category(), planned.questionText(), planned.whyWeAsk(), planned.riskLevel(),
                planned.allowsMultiple(), planned.options().stream()
                        .map(DiscoveryQuestionCatalog.ChoiceOption::toResponse).toList(),
                planned.selectionReason(), planned.missingRequirement(), planned.sourceContext(),
                planned.confirmedContextUsed(), planned.assumptionsToValidate(), planned.candidateScores(),
                planned.planner(), planned.model());
    }

    private List<String> priorityReasons(
            DiscoveryQuestionCatalog.QuestionDefinition question,
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            Set<InterviewCategory> required) {
        List<String> reasons = new ArrayList<>();
        if (question.category() == InterviewCategory.PROBLEM && answers.isEmpty()) {
            reasons.add("anchors all downstream decisions before solution detail");
        }
        if (required.contains(question.category())) {
            reasons.add("required by the current project complexity profile");
        }
        if (openQuestions.stream().anyMatch(item -> item.isMaterial()
                && item.getCategory() == question.category()
                && !item.getStatus().name().equals("RESOLVED"))) {
            reasons.add("matches a visible unresolved gap");
        }
        if (reasons.isEmpty()) {
            reasons.add("next unanswered server-approved discovery area");
        }
        return List.copyOf(reasons);
    }

    private String incompleteFollowUp(InterviewCategory category) {
        return switch (category) {
            case PROBLEM -> "Your earlier answer did not identify one concrete outcome; which delay, error, or harmful result must improve first, and how would you recognize the improvement?";
            case USERS -> "Your earlier answer did not establish authority; who starts, completes, approves, or overrides the core action, and who only needs visibility?";
            case SCOPE -> "Your earlier answer did not define a complete first-release slice; which single journey must work end to end, and which nearby capability should wait?";
            case WORKFLOWS -> "Your earlier answer left the exception path open; after the main action fails or waits too long, who acts next and what should each person see?";
            case ENTITIES -> "Your earlier answer did not settle record ownership; which system or role owns corrections, access decisions, and deletion for the core record?";
            case INTEGRATIONS -> "Your earlier answer did not identify an authoritative dependency; which external system, if any, owns the status used by the core workflow?";
            case QUALITY_GOALS -> "Your earlier answer did not provide a testable target; which failure is least acceptable at launch, and what measurable threshold should govern it?";
            case CONSTRAINTS -> "Your earlier answer did not identify a fixed boundary; which of launch date, budget, platform, or scope is non-negotiable, and which may move?";
            case RISKS -> "Your earlier answer did not identify a concrete harm; which credible failure matters most, and who must detect and recover from it?";
            case BUSINESS_RULES -> "Your earlier answer did not establish an enforceable decision; which approval, limit, or override rule must be consistent, and who owns exceptions?";
            case METRICS -> "Your earlier answer did not provide a measurable result; which baseline, target, and review period should prove the release worked?";
            case STAKEHOLDERS -> "Your earlier answer did not identify final authority; who accepts the release and who may block it for operational, security, or compliance risk?";
            case EXCLUSIONS -> "Your earlier answer did not establish a release boundary; which nearby capability must explicitly wait even if users request it?";
        };
    }

    private List<HttpDiscoveryPlannerClient.EvidenceContext> approvedEvidence(InterviewSessionEntity session) {
        return knowledgeSourceRepository.findCurrentByProjectOwnerAndStatus(
                        session.getProject().getId(), session.getOwner().getId(),
                        KnowledgeSourceStatus.APPROVED, Instant.now()).stream()
                .limit(6)
                .map(source -> new HttpDiscoveryPlannerClient.EvidenceContext(
                        source.getId(), source.getTitle(), boundedExcerpt(source.getExtractedText())))
                .toList();
    }

    private String boundedExcerpt(String text) {
        String normalized = text == null ? "" : text.strip();
        return normalized.length() <= 3_000 ? normalized : normalized.substring(0, 3_000);
    }

    private List<String> sourceContext(
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            List<HttpDiscoveryPlannerClient.EvidenceContext> evidence) {
        LinkedHashSet<String> sources = new LinkedHashSet<>(List.of("project:title", "project:description", "project:type"));
        answers.stream().skip(Math.max(0, answers.size() - 4L))
                .forEach(answer -> sources.add("answer:" + answer.getQuestionKey()));
        openQuestions.stream().filter(OpenQuestionEntity::isMaterial).limit(3)
                .forEach(question -> sources.add("open-question:" + question.getQuestionKey()));
        evidence.stream().limit(3).forEach(item -> sources.add("evidence:" + item.sourceId()));
        return List.copyOf(sources);
    }

    private String selectionReason(
            InterviewCategory category,
            InterviewSessionEntity session,
            List<OpenQuestionEntity> openQuestions) {
        boolean visibleGap = openQuestions.stream().anyMatch(item -> item.isMaterial() && item.getCategory() == category);
        String categoryName = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return visibleGap
                ? "Selected " + categoryName + " because it is a visible unresolved gap with the highest current information value."
                : "Selected " + categoryName + " because resolving it unlocks the most useful downstream requirements at this point.";
    }

    private List<String> confirmedContext(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<HttpDiscoveryPlannerClient.EvidenceContext> evidence) {
        List<String> confirmed = new ArrayList<>();
        confirmed.add("Project title: " + session.getProject().getName());
        confirmed.add("Owner-supplied project description");
        confirmed.add("Project type: " + session.getProject().getType().name());
        if (session.getProject().getIndustry() != null && !session.getProject().getIndustry().isBlank()) {
            confirmed.add("Industry: " + session.getProject().getIndustry());
        }
        if (session.getProject().getTargetAudience() != null && !session.getProject().getTargetAudience().isBlank()) {
            confirmed.add("Target audience: " + session.getProject().getTargetAudience());
        }
        answers.stream().skip(Math.max(0, answers.size() - 4L))
                .forEach(answer -> {
                    List<String> selected = selectedOptionKeys(answer);
                    String detail = selected.isEmpty() ? ""
                            : " (selected decision: " + String.join(", ", selected) + ")";
                    confirmed.add("Owner answer: "
                            + answer.getCategory().name().toLowerCase(Locale.ROOT).replace('_', ' ') + detail);
                });
        if (!evidence.isEmpty()) {
            confirmed.add("Owner-approved project evidence");
        }
        return confirmed.stream().distinct().limit(12).toList();
    }

    private List<String> assumptionsToValidate(
            InterviewCategory category,
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers) {
        String context = contextText(session, answers);
        List<String> assumptions = new ArrayList<>();
        if (category == InterviewCategory.INTEGRATIONS
                && matches(context, "api", "integration", "sms", "email", "webhook", "provider") == 0) {
            assumptions.add("Whether an external service is essential to the first release remains unconfirmed.");
        }
        if (Set.of(InterviewCategory.USERS, InterviewCategory.STAKEHOLDERS,
                InterviewCategory.BUSINESS_RULES).contains(category)) {
            assumptions.add("Suggested authority patterns are options, not confirmed role assignments.");
        }
        if (category == InterviewCategory.QUALITY_GOALS) {
            assumptions.add("Example failure modes and targets remain choices until the owner confirms them.");
        }
        return List.copyOf(assumptions);
    }

    private String missingRequirement(InterviewCategory category) {
        return switch (category) {
            case PROBLEM -> "A precise problem statement and urgency signal for scope and prioritization.";
            case USERS -> "Named actors, goals, and permission boundaries for requirements and UX flows.";
            case STAKEHOLDERS -> "Decision ownership, approvals, and operational accountability.";
            case SCOPE -> "A testable first-release boundary for plans, architecture, and acceptance.";
            case EXCLUSIONS -> "Explicit release exclusions that prevent accidental scope expansion.";
            case WORKFLOWS -> "The happy path, exception paths, hand-offs, and completion state.";
            case ENTITIES -> "Data records, ownership, lifecycle, validation, and access rules.";
            case INTEGRATIONS -> "External dependencies, exchanged data, authentication, and failure behaviour.";
            case QUALITY_GOALS -> "Measurable security, performance, reliability, privacy, and accessibility targets.";
            case CONSTRAINTS -> "Delivery, platform, budget, compliance, and team constraints.";
            case RISKS -> "Material product, user, security, data, and delivery risks with mitigation needs.";
            case BUSINESS_RULES -> "Authoritative approvals, calculations, eligibility, and enforcement rules.";
            case METRICS -> "Observable success measures and post-launch decision signals.";
        };
    }

    private String contextText(InterviewSessionEntity session, List<InterviewAnswerEntity> answers) {
        List<String> parts = new ArrayList<>();
        parts.add(session.getProject().getName());
        parts.add(session.getProject().getDescription());
        parts.add(session.getProject().getType().name());
        parts.add(session.getProject().getIndustry());
        parts.add(session.getProject().getTargetAudience());
        answers.forEach(answer -> parts.add(answer.getAnswerText()));
        return parts.stream().filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" ")).toLowerCase(Locale.ROOT);
    }

    private int matches(String context, String... signals) {
        return (int) java.util.Arrays.stream(signals).filter(signal -> Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(signal) + "(?![a-z0-9])").matcher(context).find()).count();
    }

    private List<String> selectedOptionKeys(InterviewAnswerEntity answer) {
        if (answer.getEvidence() == null || !answer.getEvidence().path("selectedOptionKeys").isArray()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        answer.getEvidence().path("selectedOptionKeys").forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                keys.add(item.asText());
            }
        });
        return List.copyOf(keys);
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
        return selectedOptionKeys(answer).contains("not-decided");
    }
}
