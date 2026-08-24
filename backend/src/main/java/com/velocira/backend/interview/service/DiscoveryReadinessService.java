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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic, auditable readiness layer. Readiness is based on confirmed
 * material decisions, not on an answer count. Validators identify the exact
 * unresolved facet and expose one narrow follow-up at a time.
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

    private record FacetCheck(
            String key,
            String label,
            String questionText,
            boolean satisfied) {
    }

    private final ObjectMapper objectMapper;
    private final DiscoveryQuestionCatalog questionCatalog;

    public Assessment assess(ProjectEntity project, List<InterviewAnswerEntity> answers) {
        List<InterviewAnswerEntity> currentAnswers = answers.stream()
                .filter(InterviewAnswerEntity::isCurrent)
                .toList();
        Map<String, InterviewAnswerEntity> currentByKey = currentAnswers.stream()
                .collect(Collectors.toMap(
                        InterviewAnswerEntity::getQuestionKey,
                        answer -> answer,
                        (earlier, later) -> later,
                        LinkedHashMap::new));
        Map<InterviewCategory, List<InterviewAnswerEntity>> currentByCategory = new EnumMap<>(InterviewCategory.class);
        currentAnswers.forEach(answer -> currentByCategory
                .computeIfAbsent(answer.getCategory(), ignored -> new ArrayList<>())
                .add(answer));

        List<Finding> findings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Set<InterviewCategory> requiredCategories = questionCatalog.requiredCategories(project, currentAnswers);
        boolean serviceBooking = questionCatalog.serviceBookingApplies(project, currentAnswers);
        String applicabilityContext = projectContext(project, currentAnswers);
        ObjectNode categoryStates = objectMapper.createObjectNode();
        ObjectNode coverage = objectMapper.createObjectNode();

        int requiredTotal = 0;
        int addressedRequired = 0;
        int completeRequired = 0;
        boolean materialCoverageComplete = true;

        for (DiscoveryQuestionCatalog.QuestionDefinition question : questionCatalog.ordered()) {
            InterviewAnswerEntity answer = currentByKey.get(question.key());
            boolean required = requiredCategories.contains(question.category());
            if (required) {
                requiredTotal++;
            }

            if (answer == null) {
                categoryStates.put(question.category().name(), required ? "MISSING" : "NOT_REQUIRED");
                if (required) {
                    materialCoverageComplete = false;
                    blockers.add("Answer the " + display(question.category()) + " decision.");
                    findings.add(new Finding(
                            "required-" + question.key(), question.category(), question.questionText(),
                            "This material decision has not been addressed.", RiskLevel.HIGH,
                            OpenQuestionStatus.OPEN, true));
                }
                continue;
            }

            categoryStates.put(question.category().name(), answer.getDisposition().name());
            if (!required) {
                continue;
            }
            addressedRequired++;

            if (answer.getDisposition() != InterviewAnswerDisposition.ANSWERED) {
                materialCoverageComplete = false;
                blockers.add("Resolve or explicitly accept the unknown " + display(question.category()) + " risk.");
                findings.add(new Finding(
                        "required-" + question.key(), question.category(), question.questionText(),
                        "The owner marked this material decision "
                                + answer.getDisposition().name().toLowerCase(Locale.ROOT)
                                + "; it remains visible and cannot be treated as settled.",
                        RiskLevel.HIGH, OpenQuestionStatus.ACKNOWLEDGED_UNKNOWN, true));
                continue;
            }

            if (isLowInformation(answer.getAnswerText()) || isExplicitlyUndecided(answer)) {
                materialCoverageComplete = false;
                categoryStates.put(question.category().name(), "INCOMPLETE");
                blockers.add("Clarify the tentative " + display(question.category()) + " answer.");
                findings.add(new Finding(
                        "incomplete-" + question.key() + "-facet-specificity",
                        question.category(), specificityFollowUp(question.category()),
                        "The answer is tentative, explicitly undecided, or too brief to support a requirement without inventing detail.",
                        RiskLevel.HIGH, OpenQuestionStatus.OPEN, true));
                continue;
            }

            findings.add(new Finding(
                    "required-" + question.key(), question.category(), question.questionText(),
                    "The owner supplied evidence for this material decision.", question.riskLevel(),
                    OpenQuestionStatus.RESOLVED, true));

            String combined = combinedAnswerValue(currentByCategory.get(question.category()));
            List<FacetCheck> checks = coverageChecks(
                    question.category(), combined, applicabilityContext, serviceBooking);
            List<FacetCheck> missing = checks.stream().filter(check -> !check.satisfied()).toList();
            ObjectNode categoryCoverage = coverage.putObject(question.category().name());
            ArrayNode satisfiedNode = categoryCoverage.putArray("satisfiedFacets");
            checks.stream().filter(FacetCheck::satisfied).map(FacetCheck::key).forEach(satisfiedNode::add);
            ArrayNode missingNode = categoryCoverage.putArray("missingFacets");
            missing.stream().map(FacetCheck::key).forEach(missingNode::add);
            categoryCoverage.put("complete", missing.isEmpty());

            if (missing.isEmpty()) {
                completeRequired++;
                categoryStates.put(question.category().name(), "COMPLETE");
                continue;
            }

            materialCoverageComplete = false;
            categoryStates.put(question.category().name(), "INCOMPLETE");
            String missingLabels = missing.stream().map(FacetCheck::label).collect(Collectors.joining(", "));
            blockers.add("Complete " + display(question.category()) + " coverage: " + missingLabels + ".");
            FacetCheck nextGap = missing.getFirst();
            findings.add(new Finding(
                    "incomplete-" + question.key() + "-facet-" + nextGap.key(),
                    question.category(), nextGap.questionText(),
                    "The current answer does not yet establish: " + missingLabels + ".",
                    RiskLevel.HIGH, OpenQuestionStatus.OPEN, true));
        }

        addContradictionFinding(currentByCategory, findings, blockers);

        boolean minimumComplete = addressedRequired == requiredTotal && materialCoverageComplete;
        boolean generationReady = minimumComplete && blockers.isEmpty();
        ObjectNode brief = toCanonicalBrief(currentByCategory, currentAnswers);
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("categoryStates", categoryStates);
        snapshot.set("coverage", coverage);
        snapshot.put("minimumComplete", minimumComplete);
        snapshot.put("generationReady", generationReady);
        snapshot.put("answeredRequiredCategories", completeRequired);
        snapshot.put("requiredCategoryCount", requiredTotal);
        snapshot.put("readinessBasis", "material-decision-coverage");
        ArrayNode blockersNode = snapshot.putArray("blockers");
        blockers.forEach(blockersNode::add);
        return new Assessment(brief, snapshot, minimumComplete, generationReady,
                completeRequired, requiredTotal, List.copyOf(blockers), List.copyOf(findings));
    }

    private List<FacetCheck> coverageChecks(
            InterviewCategory category,
            String text,
            String applicabilityContext,
            boolean serviceBooking) {
        String value = text.toLowerCase(Locale.ROOT);
        List<FacetCheck> checks = new ArrayList<>();
        switch (category) {
            case PROBLEM -> check(checks, "affected-outcome", "affected actor and outcome",
                    "Who experiences the problem, and which one observable outcome should improve?",
                    has(value, "customer", "client", "user", "professional", "provider", "operator", "team", "staff")
                            && has(value, "delay", "error", "time", "cost", "drop", "fail", "conflict", "trust", "rework", "outcome"));
            case USERS -> {
                check(checks, "named-role", "named actor role",
                        "Which named role performs the core action?",
                        has(value, "customer", "client", "user", "professional", "provider", "operator", "owner", "admin", "staff", "reviewer", "approver", "team"));
                check(checks, "decision-authority", "decision authority",
                        "Which named role has final authority over the core result?",
                        has(value, "authority", "approve", "confirm", "accept", "decide", "owner", "responsible"));
                if (serviceBooking) {
                    check(checks, "status-visibility", "booking-status visibility",
                            "Which role may see the current booking status?",
                            has(value, "status", "pending", "confirmed", "cancelled", "completed")
                                    && has(value, "see", "view", "visible", "access", "show"));
                }
            }
            case STAKEHOLDERS -> check(checks, "release-owner", "named release decision owner",
                    "Which named role may accept or block the release?",
                    has(value, "owner", "approver", "authority", "accept", "block", "sign off", "decide"));
            case SCOPE -> {
                check(checks, "first-release-outcome", "first-release outcome",
                        "Which single completed user outcome belongs in the first release?",
                        has(value, "first release", "initial release", "mvp", "launch")
                                && has(value, "complete", "confirmed", "accepted", "result", "outcome", "booking", "request", "handoff"));
                if (serviceBooking) {
                    check(checks, "service-discovery", "service discovery eligibility",
                            "What eligibility rule determines which professionals appear in service discovery results?",
                            has(value, "discover", "search", "filter", "result", "match")
                                    && has(value, "category", "service", "location", "area", "availability"));
                    check(checks, "professional-trust", "professional profile and trust information",
                            "Which profile fact determines whether a professional may appear as trusted?",
                            has(value, "profile", "professional", "provider")
                                    && has(value, "rating", "review", "verified", "trust", "skill", "credential"));
                    check(checks, "availability-booking", "availability and booking outcome",
                            "Which availability condition must hold before a booking may be confirmed?",
                            has(value, "availability", "available", "slot", "schedule")
                                    && has(value, "book", "confirm", "reserve"));
                    if (has(applicabilityContext, "price", "pricing", "cost", "rate", "payment")) {
                        check(checks, "pricing-display", "pricing display boundary",
                                "What price meaning must customers see before requesting a booking?",
                                has(value, "price", "pricing", "rate", "cost")
                                        && has(value, "display", "show", "estimate", "quoted", "visible"));
                    }
                }
            }
            case EXCLUSIONS -> {
                check(checks, "explicit-boundary", "explicit first-release exclusion",
                        "Which nearby capability is explicitly outside the first release?",
                        has(value, "exclude", "outside", "out of scope", "wait", "defer", "not include", "without"));
                if (serviceBooking && has(applicabilityContext, "payment", "subscription", "analytics", "ai", "admin")) {
                    check(checks, "commercial-automation-boundary", "commercial and automation exclusions",
                            "Which commercial or automated capability is explicitly outside the first release?",
                            has(value, "payment", "subscription", "analytics", "ai", "admin")
                                    && has(value, "exclude", "outside", "out of scope", "wait", "defer", "without", "no "));
                }
            }
            case WORKFLOWS -> addWorkflowChecks(checks, value, applicabilityContext, serviceBooking);
            case ENTITIES -> addEntityChecks(checks, value, applicabilityContext, serviceBooking);
            case INTEGRATIONS -> {
                check(checks, "dependency-boundary", "external dependency boundary",
                        "Which external dependency is essential, or is the first release explicitly self-contained?",
                        has(value, "api", "service", "calendar", "email", "sms", "webhook", "provider", "none", "self-contained", "no external"));
                check(checks, "dependency-failure", "dependency failure behavior",
                        "What should the user observe if the essential external dependency fails?",
                        has(value, "fail", "unavailable", "timeout", "retry", "queue", "degraded", "manual", "error"));
            }
            case QUALITY_GOALS -> {
                check(checks, "quality-failure", "named quality failure",
                        "Which one quality failure is unacceptable at launch?",
                        has(value, "fail", "conflict", "unauthorized", "slow", "unavailable", "lost", "incorrect", "privacy", "error"));
                check(checks, "quality-target", "measurable quality target",
                        "What numeric threshold makes that quality outcome acceptable?",
                        containsNumber(value) || has(value, "zero", "none", "all", "every"));
            }
            case CONSTRAINTS -> addConstraintChecks(checks, value);
            case RISKS -> addRiskChecks(checks, value, serviceBooking);
            case BUSINESS_RULES -> addBusinessRuleChecks(checks, value, applicabilityContext, serviceBooking);
            case METRICS -> addMetricChecks(checks, value, serviceBooking);
        }
        return List.copyOf(checks);
    }

    private void addWorkflowChecks(List<FacetCheck> checks, String value, String applicabilityContext, boolean serviceBooking) {
        check(checks, "trigger", "workflow trigger", "What exact event starts the core workflow?",
                has(value, "when", "after", "before", "once", "starts", "submits", "requests", "selects", "creates", "receives"));
        check(checks, "actors", "workflow actor", "Which named role performs the next workflow action?",
                has(value, "customer", "client", "user", "professional", "provider", "operator", "owner", "staff", "system", "team"));
        check(checks, "states", "workflow states", "Which state follows the initial workflow action?",
                has(value, "state", "status", "pending", "requested", "confirmed", "accepted", "completed", "cancelled", "rejected", "expired"));
        check(checks, "success-outcome", "success outcome", "Which observable state proves the workflow succeeded?",
                has(value, "success", "completed", "confirmed", "accepted", "delivered", "closed", "outcome", "result"));
        check(checks, "exception", "exception behavior", "What should happen after the most likely workflow exception?",
                has(value, "fail", "conflict", "retry", "recover", "expire", "cancel", "no-show", "error", "unavailable", "reject", "alternative"));
        if (!serviceBooking) {
            return;
        }
        check(checks, "confirmation-transition", "booking confirmation transition",
                "What exact event moves a booking into the confirmed state?",
                has(value, "confirm", "confirmed") && has(value, "professional", "provider", "operator", "system", "authority", "accept"));
        if (has(applicabilityContext, "resched", "cancel", "no-show", "change booking")) {
            check(checks, "reschedule", "rescheduling behavior", "What should happen when an authorized actor requests a reschedule?",
                    has(value, "resched", "change slot", "change time"));
            check(checks, "cancellation", "cancellation behavior", "What state follows an authorized booking cancellation?",
                    has(value, "cancel", "cancelled"));
            check(checks, "no-show", "no-show behavior", "What state or action follows a recorded no-show?",
                    has(value, "no-show", "no show", "missed appointment"));
        }
        if (has(applicabilityContext, "confirm", "notification", "reminder", "email", "sms")) {
            check(checks, "notification-failure", "confirmation and notification failure",
                    "What should users see when a booking notification cannot be delivered?",
                    has(value, "notif", "reminder", "email", "sms", "message")
                            && has(value, "fail", "retry", "undelivered", "delivery", "status", "manual"));
        }
    }

    private void addEntityChecks(List<FacetCheck> checks, String value, String applicabilityContext, boolean serviceBooking) {
        check(checks, "entity-names", "actual entity names", "Which named record is authoritative for the core workflow?",
                has(value, "record", "booking", "profile", "availability", "customer", "professional", "document", "request", "account", "transaction", "entity"));
        check(checks, "ownership", "record ownership", "Which role or system owns the authoritative record?",
                has(value, "own", "authoritative", "source of truth", "responsible", "system of record"));
        check(checks, "access", "record access rule", "Which named role may view the authoritative record?",
                has(value, "access", "view", "read", "visible", "permission", "may see", "may change"));
        check(checks, "lifecycle", "record lifecycle", "What event ends the authoritative record's active lifecycle?",
                has(value, "create", "update", "retain", "delete", "expire", "archive", "history", "correct", "audit", "end"));
        if (!serviceBooking) {
            return;
        }
        check(checks, "availability-owner", "availability ownership", "Which role or system owns professional availability?",
                has(value, "availability", "schedule", "slot") && has(value, "own", "authoritative", "source of truth", "professional", "provider"));
        check(checks, "time-zone", "availability time-zone basis", "Which time zone is authoritative for availability and booking timestamps?",
                has(value, "time zone", "timezone", "utc", "local time"));
        check(checks, "duration-buffer", "service duration and buffer", "Which duration-and-buffer rule determines the occupied booking interval?",
                has(value, "duration", "length") && has(value, "buffer", "gap", "setup", "travel"));
        if (has(applicabilityContext, "rating", "review", "trust", "profile", "skill", "credential")) {
            check(checks, "trust-record", "professional trust and rating records", "Which professional trust fact is stored as an auditable record?",
                    has(value, "rating", "review", "verified", "trust", "skill", "credential")
                            && has(value, "profile", "professional", "provider", "record"));
        }
        if (has(applicabilityContext, "address", "location", "privacy", "retention", "audit")) {
            check(checks, "privacy-retention", "privacy and retention boundary",
                    "When must sensitive booking data stop being visible to a professional?",
                    has(value, "address", "location", "access instruction", "sensitive", "private", "privacy")
                            && has(value, "retain", "delete", "expire", "end", "after", "until"));
            check(checks, "audit-events", "auditable booking events", "Which booking change must create an immutable audit event?",
                    has(value, "audit", "history", "event", "log", "trace")
                            && has(value, "change", "status", "booking", "availability", "access"));
        }
    }

    private void addRiskChecks(List<FacetCheck> checks, String value, boolean serviceBooking) {
        check(checks, "failure-event", "failure event", "Which exact event constitutes the material failure?",
                has(value, "fail", "conflict", "double-book", "unauthorized", "lost", "incorrect", "outage", "error", "missed"));
        check(checks, "impact", "failure impact", "Which concrete harm follows that failure?",
                has(value, "impact", "harm", "loss", "delay", "trust", "expose", "cost", "customer", "operation", "privacy"));
        check(checks, "detection", "detection mechanism", "Which signal detects the failure?",
                has(value, "detect", "monitor", "alert", "audit", "log", "metric", "alarm", "check"));
        check(checks, "owner", "response owner", "Which named role owns the failure response?",
                has(value, "owner", "operator", "team", "responsible", "support", "professional", "provider", "escalat"));
        check(checks, "recovery", "recovery action", "Which action restores a safe state after the failure?",
                has(value, "recover", "retry", "resolve", "restore", "reassign", "contain", "rollback", "release", "alternative"));
        if (serviceBooking) {
            check(checks, "concurrency-conflict", "confirmed-booking concurrency risk",
                    "What atomic check prevents two requests from confirming the same availability interval?",
                    has(value, "double-book", "same slot", "overlap", "conflict", "concurr", "atomic", "lock", "unique")
                            && has(value, "prevent", "reject", "check", "reserve", "confirm"));
        }
    }

    private void addConstraintChecks(List<FacetCheck> checks, String value) {
        check(checks, "boundary-type", "fixed boundary type", "Which one boundary is fixed: platform, date, budget, or scope?",
                has(value, "platform", "technology", "stack", "date", "deadline", "budget", "scope", "team", "web", "mobile", "cloud", "on-prem"));
        check(checks, "exact-boundary", "exact boundary value", "What exact value cannot move for that boundary?",
                containsNumber(value)
                        || has(value, "responsive web", "native ios", "native android", "on-prem", "on premises", "aws", "azure", "gcp", "java", "spring", "python", "fixed scope", "no external"));
    }

    private void addBusinessRuleChecks(List<FacetCheck> checks, String value, String applicabilityContext, boolean serviceBooking) {
        check(checks, "condition", "rule condition", "Under which exact condition does the rule apply?",
                has(value, "if", "when", "before", "after", "until", "unless", "once"));
        check(checks, "decision", "enforced decision", "What decision must the product enforce when the condition is true?",
                has(value, "must", "may", "cannot", "reject", "allow", "confirm", "require", "block", "release"));
        check(checks, "threshold", "deadline or threshold", "What exact deadline or threshold governs the decision?",
                containsNumber(value) || has(value, "immediately", "same day", "cutoff", "deadline", "threshold", "zero"));
        check(checks, "expiration", "expiration behavior", "What should happen when the rule's deadline expires?",
                has(value, "expire", "after silence", "otherwise", "release", "reassign", "timeout", "lapse", "cancel"));
        if (!serviceBooking) {
            return;
        }
        check(checks, "slot-hold", "slot-hold and expiration rule", "What exact event releases an unconfirmed slot hold?",
                has(value, "hold", "reserve") && has(value, "expire", "release", "timeout", "lapse", "cancel"));
        check(checks, "conflict-retry", "conflict and retry rule", "What response must a losing request receive after a booking conflict?",
                has(value, "conflict", "double-book", "same slot", "overlap")
                        && has(value, "retry", "reject", "alternative", "different slot", "fail"));
        check(checks, "idempotency", "idempotent confirmation rule", "Which idempotency key makes repeated confirmation requests return one booking?",
                has(value, "idempot", "request key", "deduplic", "same request", "one booking"));
        check(checks, "confirmation-authority", "confirmation authority rule", "Which actor or atomic system action has authority to confirm a booking?",
                has(value, "confirm") && has(value, "professional", "provider", "operator", "system", "authority", "atomic"));
        if (has(applicabilityContext, "price", "pricing", "payment")) {
            check(checks, "payment-boundary", "pricing semantics without payment processing",
                    "What does the displayed price mean when payment processing is outside scope?",
                    has(value, "price", "pricing", "rate", "quote", "estimate")
                            && has(value, "payment", "display", "informational", "not charge", "outside scope", "offline"));
        }
    }

    private void addMetricChecks(List<FacetCheck> checks, String value, boolean serviceBooking) {
        check(checks, "numerator", "metric numerator", "Which exact successful or failed events are counted in the metric numerator?",
                has(value, "number", "count", "successful", "confirmed", "conflict", "failed", "requests", "bookings", "events"));
        check(checks, "denominator", "metric denominator", "Which total population forms the metric denominator?",
                has(value, "denominator", "divided by", "out of", "per all", "all valid", "all confirmed",
                        "total requests", "total bookings", "total attempts", "total population", "ratio"));
        check(checks, "target", "metric target", "What numeric target must the metric meet?",
                containsNumber(value) || has(value, "zero", "none", "all", "every"));
        check(checks, "time-window", "measurement time window", "Over which time window is the target evaluated?",
                has(value, "minute", "hour", "day", "daily", "week", "weekly", "month", "monthly", "quarter", "after launch", "window"));
        if (serviceBooking) {
            check(checks, "booking-reliability", "booking reliability measure", "Which rate measures confirmed booking conflicts?",
                    has(value, "conflict", "double-book", "reliability")
                            && has(value, "rate", "%", "per", "total", "zero"));
            check(checks, "request-confirmation", "request-to-confirmation measure",
                    "What percentile or proportion of booking requests must be confirmed within the target time?",
                    has(value, "request", "booking") && has(value, "confirm", "confirmation")
                            && has(value, "within", "minute", "hour", "percent", "%", "percentile"));
        }
    }

    private void check(List<FacetCheck> checks, String key, String label, String question, boolean satisfied) {
        checks.add(new FacetCheck(key, label, question, satisfied));
    }

    private ObjectNode toCanonicalBrief(
            Map<InterviewCategory, List<InterviewAnswerEntity>> current,
            List<InterviewAnswerEntity> currentAnswers) {
        ObjectNode brief = objectMapper.createObjectNode();
        putAnswers(brief, "stakeholders", current.get(InterviewCategory.STAKEHOLDERS));
        putAnswers(brief, "problem", current.get(InterviewCategory.PROBLEM));
        putAnswers(brief, "users", current.get(InterviewCategory.USERS));
        putAnswers(brief, "actors", current.get(InterviewCategory.USERS));
        putAnswers(brief, "scope", current.get(InterviewCategory.SCOPE));
        putAnswers(brief, "exclusions", current.get(InterviewCategory.EXCLUSIONS));
        putAnswers(brief, "workflows", current.get(InterviewCategory.WORKFLOWS));
        putAnswers(brief, "businessRules", current.get(InterviewCategory.BUSINESS_RULES));
        putAnswers(brief, "entities", current.get(InterviewCategory.ENTITIES));
        putAnswers(brief, "integrations", current.get(InterviewCategory.INTEGRATIONS));
        putAnswers(brief, "qualityTargets", current.get(InterviewCategory.QUALITY_GOALS));
        putAnswers(brief, "constraints", current.get(InterviewCategory.CONSTRAINTS));
        putAnswers(brief, "risks", current.get(InterviewCategory.RISKS));
        putAnswers(brief, "metrics", current.get(InterviewCategory.METRICS));

        ObjectNode dataAndIntegrations = brief.putObject("dataAndIntegrations");
        putAnswers(dataAndIntegrations, "entities", current.get(InterviewCategory.ENTITIES));
        putAnswers(dataAndIntegrations, "integrations", current.get(InterviewCategory.INTEGRATIONS));
        ArrayNode goals = brief.putArray("goals");
        answerValues(current.get(InterviewCategory.PROBLEM)).forEach(goals::add);

        ArrayNode decisions = brief.putArray("decisions");
        currentAnswers.stream()
                .filter(answer -> answer.getDisposition() == InterviewAnswerDisposition.ANSWERED)
                .filter(answer -> answer.getAnswerText() != null && !answer.getAnswerText().isBlank())
                .forEach(answer -> {
                    ObjectNode decision = decisions.addObject();
                    if (answer.getId() != null) {
                        decision.put("answerId", answer.getId().toString());
                    }
                    decision.put("questionKey", answer.getQuestionKey());
                    decision.put("category", answer.getCategory().name());
                    decision.put("statement", answer.getAnswerText().trim());
                    decision.put("revision", answer.getRevisionNumber());
                    decision.put("source", answer.getSource());
                    if (answer.getEvidence() != null) {
                        decision.set("evidence", answer.getEvidence().deepCopy());
                    }
                });
        return brief;
    }

    private void putAnswers(ObjectNode target, String field, List<InterviewAnswerEntity> answers) {
        List<String> values = answerValues(answers);
        if (values.isEmpty()) {
            target.putNull(field);
        } else {
            target.put(field, String.join("\n\n", values));
        }
    }

    private List<String> answerValues(List<InterviewAnswerEntity> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.stream()
                .filter(answer -> answer.getDisposition() == InterviewAnswerDisposition.ANSWERED)
                .map(InterviewAnswerEntity::getAnswerText)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String combinedAnswerValue(List<InterviewAnswerEntity> answers) {
        return String.join(" ", answerValues(answers));
    }

    private void addContradictionFinding(
            Map<InterviewCategory, List<InterviewAnswerEntity>> current,
            List<Finding> findings,
            List<String> blockers) {
        String constraints = combinedAnswerValue(current.get(InterviewCategory.CONSTRAINTS)).toLowerCase(Locale.ROOT);
        String integrations = combinedAnswerValue(current.get(InterviewCategory.INTEGRATIONS)).toLowerCase(Locale.ROOT);
        boolean prohibitsExternal = constraints.matches(".*(no cloud|no external api|no third.party|offline only).*");
        boolean requiresExternal = integrations.matches(".*(cloud|api|webhook|third.party|external service).*");
        if (prohibitsExternal && requiresExternal) {
            blockers.add("Resolve the conflict between the stated constraints and required integrations.");
            findings.add(new Finding(
                    "contradiction-constraints-integrations", InterviewCategory.CONSTRAINTS,
                    "Which boundary governs the first release: the external integration or the deployment constraint?",
                    "The constraints prohibit an external/cloud dependency while the integration answer requires one.",
                    RiskLevel.HIGH, OpenQuestionStatus.OPEN, true));
        } else {
            findings.add(new Finding(
                    "contradiction-constraints-integrations", InterviewCategory.CONSTRAINTS,
                    "Which boundary governs the first release: the external integration or the deployment constraint?",
                    "No deterministic conflict was found between the current constraints and integration answers.",
                    RiskLevel.HIGH, OpenQuestionStatus.RESOLVED, true));
        }
    }

    private String specificityFollowUp(InterviewCategory category) {
        return switch (category) {
            case WORKFLOWS -> "What exact event starts the core workflow?";
            case ENTITIES -> "Which named record is authoritative for the core workflow?";
            case RISKS -> "Which exact event constitutes the material failure?";
            case CONSTRAINTS -> "Which one boundary is fixed: platform, date, budget, or scope?";
            case BUSINESS_RULES -> "Under which exact condition does the rule apply?";
            case METRICS -> "Which exact successful or failed events are counted in the metric numerator?";
            case USERS -> "Which named role has final authority over the core result?";
            case STAKEHOLDERS -> "Which named role may accept or block the release?";
            case SCOPE -> "Which single completed user outcome belongs in the first release?";
            case EXCLUSIONS -> "Which nearby capability is explicitly outside the first release?";
            case INTEGRATIONS -> "Which external dependency is essential, or is the first release explicitly self-contained?";
            case QUALITY_GOALS -> "Which one quality failure is unacceptable at launch?";
            case PROBLEM -> "Who experiences the problem, and which one observable outcome should improve?";
        };
    }

    private String projectContext(ProjectEntity project, List<InterviewAnswerEntity> answers) {
        StringBuilder context = new StringBuilder()
                .append(project.getName()).append(' ')
                .append(project.getDescription()).append(' ')
                .append(project.getIndustry() == null ? "" : project.getIndustry()).append(' ')
                .append(project.getTargetAudience() == null ? "" : project.getTargetAudience()).append(' ')
                .append(project.getTechStack() == null ? "" : project.getTechStack());
        answers.stream().map(InterviewAnswerEntity::getAnswerText)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> context.append(' ').append(value));
        return context.toString().toLowerCase(Locale.ROOT);
    }

    private boolean has(String value, String... signals) {
        for (String signal : signals) {
            if (value.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNumber(String value) {
        return Pattern.compile("(?<![a-z])\\d+(?:[.,]\\d+)?(?:%|\\s*(?:seconds?|minutes?|hours?|days?|weeks?|months?|usd|eur|gbp))?")
                .matcher(value)
                .find();
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
                || normalized.matches("^(tbd|unknown|not sure|unsure|maybe|not decided( yet)?|to be decided|n/?a)[.!]?$");
    }

    private boolean isExplicitlyUndecided(InterviewAnswerEntity answer) {
        if (answer.getEvidence() == null) {
            return false;
        }
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
