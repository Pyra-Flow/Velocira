package com.velocira.backend.interview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.OpenQuestionEntity;
import com.velocira.backend.interview.model.RiskLevel;
import com.velocira.backend.project.model.ProjectEntity;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Internal adapter for bounded, context-aware discovery-question planning. */
@Slf4j
@Component
public class HttpDiscoveryPlannerClient {

    private static final Pattern OPTION_KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,79}$");
    private static final List<Pattern> GENERIC_QUESTIONS = List.of(
            Pattern.compile("^who (are|will be|will use)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(what|which) (is )?(the )?(primary|main|core)( operational)? (problem|bottleneck|challenge|pain point)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what problem (does|should|must)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what (are )?the (quality goals|constraints|risks|integrations)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what quality goals matter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what constraints must we respect", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^walk me through the most important user journey", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^how will you know the project is successful", Pattern.CASE_INSENSITIVE));

    public record EvidenceContext(
            @JsonProperty("source_id") UUID sourceId,
            String title,
            String excerpt) {
    }

    public record PlannedQuestion(
            String key,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            RiskLevel riskLevel,
            boolean allowsMultiple,
            List<DiscoveryQuestionCatalog.ChoiceOption> options,
            String selectionReason,
            String missingRequirement,
            List<String> sourceContext,
            List<String> confirmedContextUsed,
            List<String> assumptionsToValidate,
            List<InterviewDtos.CandidateScoreResponse> candidateScores,
            String planner,
            String model) {
    }

    private final ObjectMapper objectMapper;
    private final GenerationProperties properties;
    private final HttpClient httpClient;

    public HttpDiscoveryPlannerClient(ObjectMapper objectMapper, GenerationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getAi().getConnectTimeout())
                .build();
    }

    public Optional<PlannedQuestion> planQuestion(
            ProjectEntity project,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            List<EvidenceContext> evidence,
            List<DiscoveryQuestionCatalog.QuestionDefinition> candidates,
            Set<InterviewCategory> requiredCategories) {
        if (!properties.getAi().isDiscoveryPlannerEnabled() || candidates.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<String> sourceAnchors = sourceAnchors(project, answers, openQuestions, evidence);
            PlannerRequest payload = new PlannerRequest(
                    new ProjectContext(project.getId(), project.getName(), project.getDescription(), project.getType().name(),
                            project.getIndustry(), project.getTargetAudience(), project.getTechStack(), project.getTeamSize()),
                    answers.stream().map(answer -> new AnswerContext(
                            answer.getQuestionKey(), answer.getCategory().name(), answer.getDisposition().name(),
                            answer.getQuestionText(), answer.getAnswerText(), selectedOptionKeys(answer))).toList(),
                    openQuestions.stream().filter(OpenQuestionEntity::isMaterial).map(question -> new OpenQuestionContext(
                            question.getQuestionKey(), question.getCategory().name(), question.getQuestionText(),
                            question.getReason(), question.getRiskLevel().name(), question.isMaterial())).toList(),
                    evidence,
                    candidates.stream().map(candidate -> new CandidateContext(
                            candidate.key(), candidate.category().name(), candidate.questionText(), candidate.whyWeAsk(),
                            candidate.riskLevel().name(), requiredCategories.contains(candidate.category()),
                            candidate.allowsMultiple(), List.of())).toList(),
                    sourceAnchors,
                    openQuestions.stream().filter(OpenQuestionEntity::isMaterial)
                            .map(OpenQuestionEntity::getQuestionKey).toList());
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint("/v1/discovery/plan"))
                    .timeout(properties.getAi().getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Id", correlationId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            String token = properties.getAi().getSharedSecret();
            if (token != null && !token.isBlank()) {
                request.header("X-Internal-Token", token);
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("Discovery planner returned HTTP {}; using deterministic context planner", response.statusCode());
                return Optional.empty();
            }
            PlannerResponse body = objectMapper.readValue(response.body(), PlannerResponse.class);
            return validate(body, candidates, sourceAnchors, project, answers);
        } catch (IOException ex) {
            log.debug("Discovery planner is unavailable; using deterministic context planner: {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<PlannedQuestion> validate(
            PlannerResponse body,
            List<DiscoveryQuestionCatalog.QuestionDefinition> candidates,
            List<String> sourceAnchors,
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        if (body == null || body.nextQuestion() == null) {
            return Optional.empty();
        }
        RemoteQuestion remote = body.nextQuestion();
        Map<String, DiscoveryQuestionCatalog.QuestionDefinition> byKey = candidates.stream()
                .collect(Collectors.toMap(DiscoveryQuestionCatalog.QuestionDefinition::key, Function.identity()));
        DiscoveryQuestionCatalog.QuestionDefinition candidate = byKey.get(remote.key());
        if (candidate == null || !candidate.category().name().equals(remote.category())) {
            log.debug("Discovery planner attempted to leave the server-owned candidate set");
            return Optional.empty();
        }
        String questionText = bounded(remote.questionText(), 20, 600);
        String whyWeAsk = bounded(remote.whyWeAsk(), 10, 600);
        if (questionText == null || whyWeAsk == null || !questionText.endsWith("?")
                || questionText.chars().filter(character -> character == '?').count() != 1
                || questionText.split("\\s+").length > 55
                || looksLikePromptLeak(questionText + " " + whyWeAsk)
                || isGenericOrAwkward(questionText)
                || !hasCategoryCoverage(candidate.category(), questionText)
                || containsProjectTitle(questionText, project)
                || isSemanticDuplicate(questionText, answers)
                || assertsUnsupportedCompliance(questionText + " " + whyWeAsk, project, answers)) {
            return Optional.empty();
        }

        List<InterviewDtos.CandidateScoreResponse> scores = validatedScores(body.candidateScores(), candidates);
        if (scores.isEmpty()) {
            log.debug("Discovery planner omitted reviewable candidate scores");
            return Optional.empty();
        } else {
            int highestScore = scores.stream().mapToInt(InterviewDtos.CandidateScoreResponse::score).max().orElse(0);
            boolean selectedHighest = scores.stream().anyMatch(score -> score.score() == highestScore
                    && score.key().equals(candidate.key()));
            if (!selectedHighest) {
                log.debug("Discovery planner ignored the highest-information-value candidate");
                return Optional.empty();
            }
        }

        List<DiscoveryQuestionCatalog.ChoiceOption> options = validatedOptions(remote.options(), project, answers);
        if (options.size() < 4 || options.stream().noneMatch(option -> option.key().equals("not-decided"))) {
            log.debug("Discovery planner returned shallow or incomplete decision options");
            return Optional.empty();
        }
        Set<String> allowedSources = Set.copyOf(sourceAnchors);
        List<String> sources = remoteSourceContext(body).stream()
                .filter(allowedSources::contains)
                .distinct()
                .limit(12)
                .toList();
        if (sources.isEmpty()) {
            sources = sourceAnchors.stream().limit(8).toList();
        }
        return Optional.of(new PlannedQuestion(
                candidate.key(), candidate.category(), questionText, whyWeAsk, candidate.riskLevel(),
                candidate.allowsMultiple(), List.copyOf(options),
                safeAuditText(body.selectionReason(), "Selected as the highest-value unanswered documentation gap."),
                safeAuditText(body.missingRequirement(), "A material requirement needed by downstream documents."),
                List.copyOf(sources), safeList(body.confirmedContextUsed(), 12),
                safeList(body.assumptionsToValidate(), 12), scores,
                safeAuditText(body.planner(), "gemini-context-planner-v3"),
                safeAuditText(body.model(), "configured-discovery-model")));
    }

    private List<DiscoveryQuestionCatalog.ChoiceOption> validatedOptions(
            List<ChoiceContext> remote,
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        if (remote == null) {
            return List.of();
        }
        List<DiscoveryQuestionCatalog.ChoiceOption> options = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (ChoiceContext option : remote.stream().limit(6).toList()) {
            if (option == null || option.key() == null || !OPTION_KEY.matcher(option.key()).matches()
                    || !keys.add(option.key())) {
                continue;
            }
            String label = bounded(option.label(), 1, 120);
            String description = bounded(option.description(), 1, 300);
            boolean shallow = label != null && Set.of("yes", "no", "maybe", "users", "customers", "admins")
                    .contains(label.toLowerCase());
            boolean duplicateUncertainty = label != null && !option.key().equals("not-decided")
                    && Pattern.compile("\\b(not decided|not yet decided|undecided|unknown|unsure|maybe)\\b",
                            Pattern.CASE_INSENSITIVE).matcher(label).find();
            if (label != null && description != null && description.split("\\s+").length >= 5
                    && !shallow && !duplicateUncertainty
                    && !hasUnsupportedNumericClaim(label + " " + description, project, answers)
                    && !looksLikePromptLeak(label + " " + description)) {
                options.add(new DiscoveryQuestionCatalog.ChoiceOption(option.key(), label, description));
            }
        }
        return List.copyOf(options);
    }

    private boolean hasUnsupportedNumericClaim(
            String value,
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        StringBuilder context = new StringBuilder()
                .append(project.getDescription()).append(' ')
                .append(project.getTargetAudience()).append(' ')
                .append(project.getTeamSize() == null ? "" : project.getTeamSize() + " people");
        answers.stream().map(InterviewAnswerEntity::getAnswerText).filter(java.util.Objects::nonNull)
                .forEach(answer -> context.append(' ').append(answer));
        var matcher = Pattern.compile(
                "\\b\\d+(?:\\.\\d+)?\\s*(?:%|percent|seconds?|minutes?|hours?|days?|weeks?|months?|years?|users?|requests?|records?)(?![a-z0-9])",
                Pattern.CASE_INSENSITIVE).matcher(value);
        String confirmed = context.toString().toLowerCase();
        while (matcher.find()) {
            if (!confirmed.contains(matcher.group().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> remoteSourceContext(PlannerResponse body) {
        return body.sourceContext() == null ? List.of() : body.sourceContext();
    }

    private List<String> safeList(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> bounded(value, 5, 400) != null)
                .filter(value -> !looksLikePromptLeak(value))
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<InterviewDtos.CandidateScoreResponse> validatedScores(
            List<CandidateScoreContext> scores,
            List<DiscoveryQuestionCatalog.QuestionDefinition> candidates) {
        if (scores == null) {
            return List.of();
        }
        Map<String, DiscoveryQuestionCatalog.QuestionDefinition> allowed = candidates.stream()
                .collect(Collectors.toMap(DiscoveryQuestionCatalog.QuestionDefinition::key, Function.identity()));
        return scores.stream().filter(score -> score != null && allowed.containsKey(score.key()))
                .filter(score -> allowed.get(score.key()).category().name().equals(score.category()))
                .limit(candidates.size())
                .map(score -> new InterviewDtos.CandidateScoreResponse(
                        score.key(), allowed.get(score.key()).category(), score.score(), safeList(score.reasons(), 8)))
                .toList();
    }

    private boolean isGenericOrAwkward(String question) {
        String lowered = question.toLowerCase();
        return lowered.contains("roles within") || GENERIC_QUESTIONS.stream()
                .anyMatch(pattern -> pattern.matcher(question).find());
    }

    private boolean containsProjectTitle(String question, ProjectEntity project) {
        String title = project.getName() == null ? "" : project.getName().trim().toLowerCase();
        return title.length() >= 3 && question.toLowerCase().contains(title);
    }

    private boolean hasCategoryCoverage(InterviewCategory category, String question) {
        String value = question.toLowerCase();
        return switch (category) {
            case SCOPE -> containsAny(value, "first release", "initial release", "mvp", "launch")
                    && containsAny(value, "wait", "defer", "exclude", "out of scope", "boundary", "before adding");
            case WORKFLOWS -> containsAny(value, "fail", "conflict", "reject", "decline", "expire", "timeout",
                    "non-response", "unanswered", "incomplete")
                    && containsAny(value, "recover", "resolve", "retry", "escalat", "hold", "alternative", "next");
            case QUALITY_GOALS -> Pattern.compile(
                    "(\\bwhat\\b[^?]{0,90}\\b(measurable\\s+)?(target|threshold|maximum|minimum)\\b"
                            + "|\\bhow\\s+(fast|quickly|often|many|reliably)\\b"
                            + "|\\bhow\\b[^?]{0,50}\\bmeasur|\\bwithin\\s+how\\b|\\bp95\\b)")
                    .matcher(value).find();
            case METRICS -> containsAny(value, "baseline", "current", "today", "existing", "%")
                    && containsAny(value, "target", "goal", "reduce", "increase", "below", "above")
                    && containsAny(value, "review period", "window", "weeks", "months", "after launch",
                            "by when", "timeframe");
            case CONSTRAINTS -> containsAny(value, "fixed", "non-negotiable", "strictly", "cannot move", "govern")
                    && containsAny(value, "move", "flexible", "may shift", "may shrink", "what must change");
            default -> true;
        };
    }

    private boolean containsAny(String value, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(value::contains);
    }

    private boolean isSemanticDuplicate(String question, List<InterviewAnswerEntity> answers) {
        Set<String> current = semanticTerms(question);
        for (InterviewAnswerEntity answer : answers) {
            Set<String> prior = semanticTerms(answer.getQuestionText());
            Set<String> union = new java.util.HashSet<>(current);
            union.addAll(prior);
            Set<String> overlap = new java.util.HashSet<>(current);
            overlap.retainAll(prior);
            if (!union.isEmpty() && (double) overlap.size() / union.size() >= 0.62) {
                return true;
            }
        }
        return false;
    }

    private Set<String> semanticTerms(String value) {
        if (value == null) {
            return Set.of();
        }
        Set<String> stop = Set.of("the", "and", "for", "that", "with", "what", "which", "who", "when",
                "how", "should", "from", "your", "this", "will", "are", "does", "into");
        return Pattern.compile("[a-z0-9]+").matcher(value.toLowerCase()).results()
                .map(java.util.regex.MatchResult::group)
                .filter(token -> token.length() > 2 && !stop.contains(token))
                .collect(Collectors.toSet());
    }

    private boolean assertsUnsupportedCompliance(
            String value,
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        StringBuilder confirmed = new StringBuilder()
                .append(project.getDescription()).append(' ')
                .append(project.getIndustry()).append(' ');
        answers.stream().map(InterviewAnswerEntity::getAnswerText).filter(java.util.Objects::nonNull)
                .forEach(answer -> confirmed.append(answer).append(' '));
        String output = value.toLowerCase();
        String context = confirmed.toString().toLowerCase();
        if (output.contains("you mentioned")) {
            return true;
        }
        if (java.util.stream.Stream.of("as you said", "as confirmed", "the project requires", "your requirement for")
                .anyMatch(output::contains)) {
            return true;
        }
        boolean unsupportedCompliance = java.util.stream.Stream.of("hipaa", "gdpr", "pci dss", "soc 2", "ferpa")
                .anyMatch(term -> output.contains(term) && !context.contains(term)
                        && !output.matches(".*(if|whether|might|could).*" + Pattern.quote(term) + ".*"));
        boolean unsupportedImplementation = java.util.stream.Stream.of(
                        "stripe", "twilio", "salesforce", "hubspot", "sap", "aws", "azure", "google cloud",
                        "postgresql", "mongodb", "kafka", "oauth", "single sign-on")
                .anyMatch(term -> output.contains(term) && !context.contains(term)
                        && !output.matches(".*(if|whether|might|could|example|such as).*"
                                + Pattern.quote(term) + ".*"));
        return unsupportedCompliance || unsupportedImplementation;
    }

    private List<String> selectedOptionKeys(InterviewAnswerEntity answer) {
        JsonNode selected = answer.getEvidence().path("selectedOptionKeys");
        if (!selected.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        selected.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        });
        return List.copyOf(result);
    }

    private List<String> sourceAnchors(
            ProjectEntity project,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions,
            List<EvidenceContext> evidence) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>(List.of(
                "project:title", "project:description", "project:type"));
        if (project.getIndustry() != null && !project.getIndustry().isBlank()) {
            anchors.add("project:industry");
        }
        if (project.getTargetAudience() != null && !project.getTargetAudience().isBlank()) {
            anchors.add("project:target-audience");
        }
        if (project.getTechStack() != null && !project.getTechStack().isBlank()) {
            anchors.add("project:tech-stack");
        }
        if (project.getTeamSize() != null) {
            anchors.add("project:team-size");
        }
        answers.forEach(answer -> anchors.add("answer:" + answer.getQuestionKey()));
        openQuestions.stream().filter(OpenQuestionEntity::isMaterial)
                .forEach(question -> anchors.add("open-question:" + question.getQuestionKey()));
        evidence.forEach(item -> anchors.add("evidence:" + item.sourceId()));
        return List.copyOf(anchors);
    }

    private String bounded(String value, int minimum, int maximum) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() >= minimum && trimmed.length() <= maximum ? trimmed : null;
    }

    private String safeAuditText(String value, String fallback) {
        String bounded = bounded(value, 1, 600);
        return bounded == null || looksLikePromptLeak(bounded) ? fallback : bounded;
    }

    private boolean looksLikePromptLeak(String value) {
        String lowered = value.toLowerCase();
        return lowered.contains("system prompt") || lowered.contains("internal prompt")
                || lowered.contains("ignore previous") || lowered.contains("gemini_api_key");
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
            @JsonProperty("open_questions") List<OpenQuestionContext> openQuestions,
            List<EvidenceContext> evidence,
            @JsonProperty("candidate_questions") List<CandidateContext> candidateQuestions,
            @JsonProperty("source_anchors") List<String> sourceAnchors,
            @JsonProperty("visible_open_question_keys") List<String> visibleOpenQuestionKeys) {
    }

    private record ProjectContext(
            UUID id,
            String name,
            String description,
            String type,
            String industry,
            @JsonProperty("target_audience") String targetAudience,
            @JsonProperty("tech_stack") String techStack,
            @JsonProperty("team_size") Integer teamSize) {
    }

    private record AnswerContext(
            @JsonProperty("question_key") String questionKey,
            String category,
            String disposition,
            @JsonProperty("question_text") String questionText,
            @JsonProperty("answer_text") String answerText,
            @JsonProperty("selected_option_keys") List<String> selectedOptionKeys) {
    }

    private record OpenQuestionContext(
            String key,
            String category,
            @JsonProperty("question_text") String questionText,
            String reason,
            @JsonProperty("risk_level") String riskLevel,
            boolean material) {
    }

    private record CandidateContext(
            String key,
            String category,
            @JsonProperty("base_question") String baseQuestion,
            @JsonProperty("why_we_ask") String whyWeAsk,
            @JsonProperty("risk_level") String riskLevel,
            boolean required,
            @JsonProperty("allows_multiple") boolean allowsMultiple,
            List<ChoiceContext> options) {
    }

    private record ChoiceContext(String key, String label, String description) {
    }

    private record PlannerResponse(
            String planner,
            String model,
            @JsonProperty("next_question") RemoteQuestion nextQuestion,
            @JsonProperty("selection_reason") String selectionReason,
            @JsonProperty("missing_requirement") String missingRequirement,
            @JsonProperty("source_context") List<String> sourceContext,
            @JsonProperty("confirmed_context_used") List<String> confirmedContextUsed,
            @JsonProperty("assumptions_to_validate") List<String> assumptionsToValidate,
            @JsonProperty("candidate_scores") List<CandidateScoreContext> candidateScores) {
    }

    private record CandidateScoreContext(
            String key,
            String category,
            int score,
            List<String> reasons) {
    }

    private record RemoteQuestion(
            String key,
            String category,
            @JsonProperty("question_text") String questionText,
            @JsonProperty("why_we_ask") String whyWeAsk,
            @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("allows_multiple") boolean allowsMultiple,
            List<ChoiceContext> options) {
    }
}
