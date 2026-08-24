package com.velocira.backend.interview.service;

import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.RiskLevel;
import com.velocira.backend.project.model.ProjectEntity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-owned question wording and order for the deterministic baseline.
 * The later LLM planner may choose among these categories, but cannot make a
 * browser-provided question into a product rule.
 */
@Component
public class DiscoveryQuestionCatalog {

    private static final Map<String, String> DECISION_FOCUS_BY_KEY = Map.ofEntries(
            Map.entry("manual-work", "reducing manual work"),
            Map.entry("availability-conflicts", "preventing confirmed booking conflicts"),
            Map.entry("slow-confirmation", "shortening booking confirmation time"),
            Map.entry("missed-follow-up", "preventing missed follow-up"),
            Map.entry("intake-dropoff", "reducing client intake drop-off"),
            Map.entry("slow-qualification", "shortening intake review time"),
            Map.entry("missing-intake-context", "collecting complete client context"),
            Map.entry("handoff-delay", "reducing care hand-off delays"),
            Map.entry("missing-context", "preventing missing care context"),
            Map.entry("unclear-ownership", "making ownership unambiguous"),
            Map.entry("failed-collection", "reducing failed collections"),
            Map.entry("approval-delay", "shortening approval time"),
            Map.entry("reconciliation-work", "reducing reconciliation work"),
            Map.entry("delay", "reducing avoidable delay"),
            Map.entry("errors", "preventing costly errors"),
            Map.entry("visibility", "making status and ownership visible"),
            Map.entry("closed-booking", "one complete booking journey"),
            Map.entry("request-decision", "one complete request-to-decision journey"),
            Map.entry("closed-handoff", "one closed-loop care hand-off"),
            Map.entry("payment-lifecycle", "one complete payment lifecycle"),
            Map.entry("reviewed-task", "one reviewed AI-assisted task"),
            Map.entry("one-way-sync", "one authoritative one-way synchronization"),
            Map.entry("complete-thin-slice", "one complete end-to-end journey"),
            Map.entry("no-double-booking", "preventing confirmed double-bookings"),
            Map.entry("protect-location", "protecting addresses and access details"),
            Map.entry("reliable-notices", "dependable status notifications"),
            Map.entry("fast-mobile", "responsive mobile booking"),
            Map.entry("acknowledgment-target", "a bounded hand-off acknowledgment time"),
            Map.entry("access-integrity", "preventing incorrect patient-record access"),
            Map.entry("traceable-changes", "making every care-record correction traceable"),
            Map.entry("security-first", "preventing unauthorized access"),
            Map.entry("integrity-first", "preventing lost or inconsistent work"),
            Map.entry("reliability-first", "keeping the core journey available"),
            Map.entry("speed-first", "keeping the core action responsive"));

    public record QuestionDefinition(
            String key,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            RiskLevel riskLevel,
            boolean required,
            boolean allowsMultiple,
            List<ChoiceOption> options) {

        InterviewDtos.QuestionResponse toResponse() {
            return toResponse(
                    "Selected by the deterministic catalog order.",
                    "An unanswered " + category.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " requirement.",
                    List.of("project:title", "project:description"),
                    List.of("Project title and owner-supplied description"),
                    List.of(),
                    List.of(),
                    "deterministic-context-planner-v2",
                    "deterministic");
        }

        InterviewDtos.QuestionResponse toResponse(
                String selectionReason,
                String missingRequirement,
                List<String> sourceContext,
                List<String> confirmedContextUsed,
                List<String> assumptionsToValidate,
                List<InterviewDtos.CandidateScoreResponse> candidateScores,
                String planner,
                String model) {
            return new InterviewDtos.QuestionResponse(key, category, questionText, whyWeAsk, riskLevel,
                    allowsMultiple, options.stream().map(ChoiceOption::toResponse).toList(), selectionReason,
                    missingRequirement, List.copyOf(sourceContext), List.copyOf(confirmedContextUsed),
                    List.copyOf(assumptionsToValidate), List.copyOf(candidateScores), planner, model);
        }
    }

    /** A bounded, server-owned option that can be safely rendered as a check control. */
    public record ChoiceOption(String key, String label, String description) {
        InterviewDtos.ChoiceOptionResponse toResponse() {
            return new InterviewDtos.ChoiceOptionResponse(key, label, description);
        }
    }

    private final Map<InterviewCategory, QuestionDefinition> byCategory;
    private final Map<String, QuestionDefinition> byKey;
    private final List<QuestionDefinition> ordered;

    public DiscoveryQuestionCatalog() {
        ordered = List.of(
                question("problem", InterviewCategory.PROBLEM,
                        "What is the most important problem to solve first?",
                        "A clear first problem keeps the requirements focused on an outcome instead of a feature list.", RiskLevel.HIGH, true),
                question("users", InterviewCategory.USERS,
                        "Who needs this most, and what do they need to do?",
                        "The main users define the roles, permissions, and journeys the requirements must cover.", RiskLevel.HIGH, true),
                question("stakeholders", InterviewCategory.STAKEHOLDERS,
                        "Who has final approval when priorities conflict?",
                        "A named decision owner prevents approval and escalation rules from staying ambiguous.", RiskLevel.MEDIUM, false),
                question("scope", InterviewCategory.SCOPE,
                        "What is the smallest complete outcome the first release must deliver?",
                        "A complete first outcome keeps requirements testable and prevents accidental scope growth.", RiskLevel.HIGH, true),
                question("exclusions", InterviewCategory.EXCLUSIONS,
                        "What should the first release deliberately leave out?",
                        "A visible boundary protects the team from assumptions and scope creep.", RiskLevel.HIGH, true),
                question("workflows", InterviewCategory.WORKFLOWS,
                        "What should happen from the user’s first action to a successful result?",
                        "The core journey becomes the backbone for requirements, recovery paths, and tests.", RiskLevel.HIGH, true),
                question("entities", InterviewCategory.ENTITIES,
                        "Which records are essential, and who owns them?",
                        "The essential records reveal ownership, access, validation, and retention needs.", RiskLevel.HIGH, true),
                question("integrations", InterviewCategory.INTEGRATIONS,
                        "Which external service is essential to the core outcome?",
                        "An essential dependency adds data, security, reliability, and recovery decisions.", RiskLevel.HIGH, true),
                question("quality", InterviewCategory.QUALITY_GOALS,
                        "Which quality outcome matters most at launch?",
                        "Choosing one priority turns broad quality language into a measurable requirement.", RiskLevel.HIGH, true),
                question("constraints", InterviewCategory.CONSTRAINTS,
                        "Which project boundary cannot change?",
                        "A fixed boundary makes scope and delivery trade-offs explicit.", RiskLevel.HIGH, true),
                question("risks", InterviewCategory.RISKS,
                        "Which realistic failure would cause the most harm?",
                        "A concrete risk can produce prevention, detection, recovery, and escalation requirements.", RiskLevel.HIGH, true),
                question("business-rules", InterviewCategory.BUSINESS_RULES,
                        "Which decision must the product enforce consistently?",
                        "Approval, eligibility, and calculation rules must come from an accountable owner.", RiskLevel.HIGH, false),
                question("metrics", InterviewCategory.METRICS,
                        "What result would prove the first release worked?",
                        "A measurable result helps the team evaluate the release and prioritize what comes next.", RiskLevel.MEDIUM, false));

        Map<InterviewCategory, QuestionDefinition> categories = new EnumMap<>(InterviewCategory.class);
        Map<String, QuestionDefinition> keys = new java.util.HashMap<>();
        ordered.forEach(question -> {
            categories.put(question.category(), question);
            keys.put(question.key(), question);
        });
        byCategory = Map.copyOf(categories);
        byKey = Map.copyOf(keys);
    }

    public List<QuestionDefinition> ordered() {
        return ordered;
    }

    public QuestionDefinition requireByKey(String key) {
        QuestionDefinition question = byKey.get(key);
        if (question == null) {
            throw new IllegalArgumentException("That interview question is not recognized.");
        }
        return question;
    }

    public java.util.Optional<QuestionDefinition> findByKey(String key) {
        return java.util.Optional.ofNullable(byKey.get(key));
    }

    public QuestionDefinition byCategory(InterviewCategory category) {
        return byCategory.get(category);
    }

    /**
     * Simple products stop after the seven core evidence areas. Complexity
     * signals deterministically add the deeper categories needed for safe,
     * useful downstream documents.
     */
    public Set<InterviewCategory> requiredCategories(
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        EnumSet<InterviewCategory> required = EnumSet.of(
                InterviewCategory.PROBLEM,
                InterviewCategory.USERS,
                InterviewCategory.SCOPE,
                InterviewCategory.WORKFLOWS,
                InterviewCategory.QUALITY_GOALS,
                InterviewCategory.CONSTRAINTS,
                InterviewCategory.METRICS);
        StringBuilder context = new StringBuilder()
                .append(project.getName()).append(' ')
                .append(project.getDescription()).append(' ')
                .append(project.getType()).append(' ')
                .append(project.getIndustry() == null ? "" : project.getIndustry()).append(' ')
                .append(project.getTargetAudience() == null ? "" : project.getTargetAudience()).append(' ')
                .append(project.getTechStack() == null ? "" : project.getTechStack());
        answers.stream().map(InterviewAnswerEntity::getAnswerText)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> context.append(' ').append(value));
        String signals = context.toString().toLowerCase(Locale.ROOT);

        boolean regulated = containsPositiveSignal(signals, "health", "medical", "patient", "finance", "bank", "payment",
                "insurance", "government", "compliance", "regulated", "gdpr", "hipaa", "pci");
        boolean multiRole = containsPositiveSignal(signals, "role", "admin", "approval", "manager", "staff", "partner",
                "marketplace", "multi-tenant", "enterprise");
        boolean dataHeavy = containsPositiveSignal(signals, "data", "record", "document", "analytics", "report", "file",
                "transaction", "patient", "inventory", "machine learning");
        boolean integrated = containsPositiveSignal(signals, "api", "integration", "webhook", "sso", "payment", "crm",
                "erp", "device", "import", "export", "third-party");
        boolean ruleHeavy = containsPositiveSignal(signals, "cutoff", "threshold", "eligibility", "refund",
                "override", "cancellation policy", "pricing rule", "spending limit");
        boolean highRisk = regulated || project.getType().name().equals("AI_SYSTEM")
                || project.getType().name().equals("IOT")
                || containsPositiveSignal(signals, "ai", "llm", "agent", "real-time", "critical", "offline");
        boolean serviceBooking = containsPositiveSignal(signals, "booking", "appointment", "reservation", "availability")
                && containsPositiveSignal(signals, "customer", "client", "professional", "provider", "service");

        // A service-booking workflow is materially cross-cutting even when its
        // short project description does not contain generic words such as
        // "data", "risk", or "integration". The category validators below
        // decide which concrete facets remain unresolved.
        if (serviceBooking) {
            required.addAll(EnumSet.allOf(InterviewCategory.class));
        }

        if (multiRole || regulated) {
            required.add(InterviewCategory.STAKEHOLDERS);
            required.add(InterviewCategory.BUSINESS_RULES);
        }
        if (ruleHeavy) {
            required.add(InterviewCategory.BUSINESS_RULES);
        }
        if (dataHeavy || multiRole || regulated) {
            required.add(InterviewCategory.ENTITIES);
        }
        if (integrated || project.getType().name().equals("API_BACKEND") || project.getType().name().equals("IOT")) {
            required.add(InterviewCategory.INTEGRATIONS);
        }
        if (highRisk) {
            required.add(InterviewCategory.EXCLUSIONS);
            required.add(InterviewCategory.RISKS);
        }
        return Set.copyOf(required);
    }

    /**
     * Exposes the domain applicability decision used by the catalog so the
     * readiness gate can require booking-specific evidence without coupling
     * production logic to a fixture or project name.
     */
    public boolean serviceBookingApplies(ProjectEntity project, List<InterviewAnswerEntity> answers) {
        return "booking product".equals(detectDomain(project, answers).label());
    }

    /**
     * Keeps the catalog bounded and reviewable while making the exact prompt
     * relevant to the project context. The AI planner still decides which
     * unanswered category is most valuable next; this layer ensures the
     * durable question and answer history use the same safe wording.
     */
    public QuestionDefinition tailorForProject(QuestionDefinition question, ProjectEntity project) {
        return tailorForContext(question, project, List.of());
    }

    /** Safe decision-specific fallback used when Gemini is disabled or unavailable. */
    public QuestionDefinition tailorForContext(
            QuestionDefinition question,
            ProjectEntity project,
            List<InterviewAnswerEntity> answers) {
        DomainProfile domain = detectDomain(project, answers);
        Integer teamSize = project.getTeamSize();
        String prompt = switch (question.category()) {
            case PROBLEM -> problemQuestion(domain);
            case USERS -> usersQuestion(domain);
            case STAKEHOLDERS -> "Who has final authority over " + domain.decisionFocus() + " decisions?";
            case SCOPE -> "What is the smallest complete " + domain.workflow() + " the first release must support?";
            case EXCLUSIONS -> "What should the first release deliberately leave out?";
            case WORKFLOWS -> workflowQuestion(domain);
            case ENTITIES -> entitiesQuestion(domain);
            case INTEGRATIONS -> integrationQuestion(domain);
            case QUALITY_GOALS -> qualityQuestion(domain);
            case CONSTRAINTS -> teamSize == null
                    ? "Which first-release boundary cannot change: date, budget, platform, or scope?"
                    : "With a team of " + teamSize + ", which first-release boundary cannot change?";
            case RISKS -> riskQuestion(domain);
            case BUSINESS_RULES -> rulesQuestion(domain);
            case METRICS -> "What measurable result would prove the first release worked?";
        };
        String why = switch (question.category()) {
            case PROBLEM -> "This separates the outcome worth funding from possible features and gives the SRS a measurable purpose.";
            case USERS -> "Authority and responsibility define roles, permissions, notifications, and exception ownership.";
            case STAKEHOLDERS -> "The answer establishes who resolves conflicts and accepts consequential behavior before launch.";
            case SCOPE -> "A complete thin slice produces testable acceptance criteria and a credible delivery plan.";
            case EXCLUSIONS -> "Naming the nearest tempting capability prevents it from silently entering design and estimates.";
            case WORKFLOWS -> "This supplies state changes, hand-offs, timeout behavior, recovery paths, and UX feedback.";
            case ENTITIES -> "The answer drives database ownership, permission checks, audit history, retention, and deletion behavior.";
            case INTEGRATIONS -> "This clarifies exchanged data, authentication, retries, degraded operation, and support ownership.";
            case QUALITY_GOALS -> "Choosing a failure and target converts broad quality language into a verifiable non-functional requirement.";
            case CONSTRAINTS -> "Knowing what cannot move makes scope and architecture trade-offs explicit rather than accidental.";
            case RISKS -> "A concrete harm scenario produces prevention, detection, recovery, and human-escalation requirements.";
            case BUSINESS_RULES -> "The product cannot safely invent who may decide, override, calculate, or approve a consequential action.";
            case METRICS -> "A baseline, target, and review window make post-launch success observable and actionable.";
        };
        return new QuestionDefinition(question.key(), question.category(), prompt, why, question.riskLevel(),
                question.required(), question.allowsMultiple(), contextualChoices(question.category(), domain));
    }

    private String priorDecisionFocus(
            InterviewCategory category,
            List<InterviewAnswerEntity> answers) {
        List<InterviewCategory> eligible = switch (category) {
            case USERS -> List.of(InterviewCategory.PROBLEM);
            case SCOPE -> List.of(InterviewCategory.PROBLEM, InterviewCategory.USERS);
            case WORKFLOWS -> List.of(InterviewCategory.SCOPE, InterviewCategory.PROBLEM);
            case ENTITIES -> List.of(InterviewCategory.SCOPE, InterviewCategory.USERS, InterviewCategory.PROBLEM);
            case INTEGRATIONS -> List.of(InterviewCategory.SCOPE, InterviewCategory.WORKFLOWS, InterviewCategory.PROBLEM);
            case QUALITY_GOALS -> List.of(InterviewCategory.PROBLEM, InterviewCategory.SCOPE);
            case RISKS -> List.of(InterviewCategory.QUALITY_GOALS, InterviewCategory.PROBLEM, InterviewCategory.SCOPE);
            case BUSINESS_RULES -> List.of(InterviewCategory.WORKFLOWS, InterviewCategory.USERS, InterviewCategory.PROBLEM);
            case STAKEHOLDERS -> List.of(InterviewCategory.RISKS, InterviewCategory.USERS, InterviewCategory.PROBLEM);
            case EXCLUSIONS -> List.of(InterviewCategory.SCOPE, InterviewCategory.PROBLEM);
            case METRICS -> List.of(InterviewCategory.QUALITY_GOALS, InterviewCategory.PROBLEM);
            case CONSTRAINTS -> List.of(InterviewCategory.SCOPE, InterviewCategory.PROBLEM);
            case PROBLEM -> List.of();
        };
        for (InterviewCategory sourceCategory : eligible) {
            for (int index = answers.size() - 1; index >= 0; index--) {
                InterviewAnswerEntity answer = answers.get(index);
                if (answer.getCategory() != sourceCategory || answer.getEvidence() == null) {
                    continue;
                }
                for (var selected : answer.getEvidence().path("selectedOptionKeys")) {
                    String focus = DECISION_FOCUS_BY_KEY.get(selected.asText());
                    if (focus != null) {
                        return focus;
                    }
                }
            }
        }
        return null;
    }

    public QuestionDefinition fromResponse(InterviewDtos.QuestionResponse response, ProjectEntity project) {
        QuestionDefinition catalog = tailorForProject(requireByKey(response.questionKey()), project);
        if (response.category() != catalog.category()) {
            throw new IllegalArgumentException("The planned interview category does not match its server-owned key.");
        }
        List<ChoiceOption> plannedOptions = response.options() == null || response.options().isEmpty()
                ? catalog.options()
                : response.options().stream()
                        .map(option -> new ChoiceOption(option.key(), option.label(), option.description()))
                        .toList();
        return new QuestionDefinition(catalog.key(), catalog.category(), response.questionText(), response.whyWeAsk(),
                catalog.riskLevel(), catalog.required(), catalog.allowsMultiple(), plannedOptions);
    }

    private DomainProfile detectDomain(ProjectEntity project, List<InterviewAnswerEntity> answers) {
        StringBuilder source = new StringBuilder()
                .append(project.getName()).append(' ')
                .append(project.getDescription()).append(' ')
                .append(project.getIndustry() == null ? "" : project.getIndustry()).append(' ')
                .append(project.getTargetAudience() == null ? "" : project.getTargetAudience()).append(' ');
        answers.stream().map(InterviewAnswerEntity::getAnswerText)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> source.append(value).append(' '));
        return detectDomainSource(source.toString().toLowerCase(Locale.ROOT));
    }

    private DomainProfile detectDomainSource(String source) {
        if (containsAny(source, "legal", "law firm", "lawyer", "attorney", "solicitor", "case intake",
                "client intake", "legal practice")) {
            return new DomainProfile("legal services product", "prospective clients and legal staff",
                    "client intake journey", "clients, intake responses, documents, and review decisions",
                    "confidentiality, accessibility, record integrity, and clear status", "client eligibility, confidentiality, review ownership, and safe document handling");
        }
        if (containsAny(source, "booking", "appointment", "schedule", "professional", "reservation")
                && !containsAny(source, "clinic", "health", "patient", "doctor", "medical", "hospital", "care", "ehr")) {
            return new DomainProfile("booking product", "customers, operators, and assigned staff",
                    "booking from request through completion", "customers, availability, bookings, and access details",
                    "conflict prevention, privacy, notification delivery, and mobile speed",
                    "authority, booking states, sensitive location data, and failure recovery");
        }
        if (containsAny(source, "clinic", "health", "patient", "doctor", "medical", "hospital", "care", "ehr")) {
            return new DomainProfile("healthcare product", "patients and care teams", "care journey", "patient records and appointments", "privacy, safety, and uptime", "clinical roles, sensitive data, and safe hand-offs");
        }
        if (containsAny(source, "payment", "bank", "finance", "fintech", "wallet", "invoice", "transaction", "loan")) {
            return new DomainProfile("fintech product", "customers and operations teams", "money movement flow", "accounts, transactions, and approvals", "security, auditability, and reliability", "financial controls, permissions, and traceability");
        }
        if (containsAny(source, "shop", "store", "commerce", "ecommerce", "marketplace", "order", "catalog", "inventory")) {
            return new DomainProfile("commerce product", "customers and fulfilment teams", "order lifecycle", "products, orders, and inventory", "checkout security, speed, and availability", "orders, payment boundaries, and fulfilment");
        }
        if (containsAny(source, "course", "student", "teacher", "school", "learning", "education", "training", "exam")) {
            return new DomainProfile("education product", "learners and educators", "learning journey", "courses, enrolments, and assessments", "accessibility, privacy, and availability", "learner progress, assessment rules, and accessible delivery");
        }
        if (containsSignal(source, "ai", "llm", "assistant", "agent", "model", "machine learning", "copilot", "rag")) {
            return new DomainProfile("AI product", "end users and human reviewers", "human-in-the-loop interaction", "inputs, outputs, feedback, and evaluations", "safety, privacy, and reliability", "human oversight, data boundaries, and safe fallback behaviour");
        }
        if (containsAny(source, "api", "webhook", "etl", "warehouse", "sync", "connector", "integration")) {
            return new DomainProfile("data integration product", "data owners and exception operators",
                    "record from source through validated destination", "source records, mappings, failures, and corrections",
                    "freshness, integrity, replay safety, and observability", "system authority, validation, retries, and reconciliation");
        }
        if (containsAny(source, "saas", "multi-tenant", "workspace", "subscription", "enterprise")) {
            return new DomainProfile("multi-workspace SaaS product", "workspace members, approvers, and administrators",
                    "team work item from submission through approval", "workspaces, memberships, work items, and audit events",
                    "tenant isolation, reliability, and responsive core actions", "workspace permissions, approval ownership, and isolation");
        }
        if (containsAny(source, "internal", "employee", "staff", "erp", "admin", "operations", "back office")) {
            return new DomainProfile("internal operations tool", "staff and approvers", "operational hand-off", "work items, approvals, and reports", "permissions, auditability, and reliability", "roles, approvals, and existing operational systems");
        }
        return new DomainProfile("software product", "the people who start, complete, approve, or monitor the core work",
                "core user outcome from start through completion", "core records, ownership, and status history",
                "security, integrity, reliability, accessibility, and response time",
                "roles, state changes, data ownership, recovery, and delivery boundaries");
    }

    private String problemQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "What is the biggest obstacle in the current client intake process?";
            case "booking product" -> "What is the biggest problem in the current booking process?";
            case "healthcare product" -> "Which care-coordination problem causes the most harmful delay or uncertainty?";
            case "fintech product" -> "Which money-movement problem is most costly today?";
            case "AI product" -> "Which user task should AI improve first?";
            case "data integration product" -> "Which broken data hand-off creates the most rework or unreliable decisions?";
            case "multi-workspace SaaS product" -> "Which team workflow loses the most time or control today?";
            default -> "Which part of the current process creates the most avoidable delay, error, or frustration?";
        };
    }

    private String usersQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which named role decides the next step after a client submits an intake?";
            case "booking product" -> "Which named role has authority to confirm a booking?";
            case "healthcare product" -> "Which named role accepts responsibility for a care hand-off?";
            case "fintech product" -> "Which named role has authority to approve a payment?";
            case "AI product" -> "Which named role accepts an AI-assisted result?";
            case "data integration product" -> "Who owns source data and resolves rejected records?";
            case "multi-workspace SaaS product" -> "Which named role has final approval authority in each workspace?";
            default -> "Which named role has final authority over the core result?";
        };
    }

    private String workflowQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "What should happen from a client starting an intake to receiving a clear next step?";
            case "booking product" -> "What should happen from a customer requesting a slot to a confirmed outcome?";
            case "healthcare product" -> "What should happen from hand-off creation to confirmed receipt?";
            case "fintech product" -> "What should happen from payment initiation to final status?";
            case "AI product" -> "What should happen from user input to an accepted AI-assisted result?";
            case "data integration product" -> "What should happen from source change to an accepted destination record?";
            case "multi-workspace SaaS product" -> "What should happen from submission to approval and completion?";
            default -> "What should happen from the first user action to a completed result?";
        };
    }

    private String entitiesQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which client-intake record is authoritative?";
            case "booking product" -> "Which booking record is authoritative?";
            case "healthcare product" -> "Which care hand-off record is authoritative?";
            case "fintech product" -> "Which payment record is authoritative?";
            case "AI product" -> "Which AI-assisted work record is authoritative?";
            case "data integration product" -> "Which system is authoritative for each shared record?";
            case "multi-workspace SaaS product" -> "Which workspace record is authoritative?";
            default -> "Which named record is authoritative for the core workflow?";
        };
    }

    private String integrationQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which existing calendar, document, identity, or case service is essential to the first release?";
            case "booking product" -> "If the first release uses reminders or calendar updates, which actions would depend on an external service, and what should users see or do when delivery fails?";
            case "healthcare product" -> "Which clinical or identity system is essential to the care hand-off?";
            case "fintech product" -> "Which payment or identity provider is essential to final status?";
            case "AI product" -> "Which model or retrieval service is essential to the core task?";
            case "data integration product" -> "Which system is authoritative for the highest-value data exchange?";
            default -> "Which external service is essential to the core outcome?";
        };
    }

    private String qualityQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which quality failure would damage client trust most at launch?";
            case "booking product" -> "Which quality failure would damage booking trust most at launch?";
            case "healthcare product" -> "Which quality failure poses the greatest safety or privacy risk at launch?";
            case "fintech product" -> "Which quality failure poses the greatest financial risk at launch?";
            case "AI product" -> "Which quality failure would make an AI-assisted result unacceptable?";
            case "data integration product" -> "Which data quality failure would cause the most downstream harm?";
            default -> "Which quality failure would damage trust most at launch?";
        };
    }

    private String riskQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which failure could most harm a prospective client or the practice?";
            case "booking product" -> "Which booking failure would create the most customer or operational harm?";
            case "healthcare product" -> "Which realistic failure could cause the most harm to care or privacy?";
            case "fintech product" -> "Which realistic failure could cause the most financial loss or loss of trust?";
            case "AI product" -> "Which AI failure could cause the most harm?";
            case "data integration product" -> "Which silent data failure could cause the worst downstream decision?";
            default -> "Which realistic failure could most harm users or invalidate the release?";
        };
    }

    private String rulesQuestion(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> "Which eligibility, conflict, consent, or review decision must be enforced consistently?";
            case "booking product" -> "Which booking decision must follow an explicit rule?";
            case "healthcare product" -> "Which hand-off decision must follow an explicit safety or access rule?";
            case "fintech product" -> "Which approval or limit must be enforced before money can move?";
            case "AI product" -> "Which AI input or output must require a block or human review?";
            case "data integration product" -> "Which rule decides whether a record is accepted or sent for review?";
            default -> "Which approval, eligibility, calculation, or access decision must be enforced consistently?";
        };
    }

    private List<ChoiceOption> contextualChoices(InterviewCategory category, DomainProfile domain) {
        List<ChoiceOption> result = new java.util.ArrayList<>(switch (category) {
            case PROBLEM -> problemChoices(domain);
            case USERS -> userChoices(domain);
            case STAKEHOLDERS -> List.of(
                    option("product-owner", "Product owner has final authority", "Centralizes scope decisions while named specialists sign off on defined risks."),
                    option("operational-owner", "Named operational owner decides routine policy while a risk owner may block unsafe release", "Separates day-to-day accountability from the authority to stop a consequential release."),
                    option("joint-approval", "Business and risk owners approve jointly", "Adds protection for consequential releases but can lengthen decision time."));
            case SCOPE -> scopeChoices(domain);
            case EXCLUSIONS -> List.of(
                    option("advanced-automation", "Advanced automation waits", "Keeps consequential decisions human-controlled in the first release."),
                    option("historical-migration", "Historical migration waits", "Reduces data-cleaning risk by starting with new or essential records."),
                    option("nonessential-integrations", "Non-essential integrations wait", "Protects the core journey from external dependency and support risk."),
                    option("native-apps", "Native mobile applications wait", "Uses a responsive web experience before funding separate platform builds."));
            case WORKFLOWS -> workflowChoices(domain);
            case ENTITIES -> entityChoices(domain);
            case INTEGRATIONS -> List.of(
                    option("required-live", "Required for the live journey", "The core action waits or fails clearly when the external service is unavailable."),
                    option("queued-degraded", "Queue work during an outage", "Users may continue with a visible pending status and controlled retry."),
                    option("manual-fallback", "Provide a manual fallback", "Preserves essential work but requires later reconciliation."),
                    option("defer-integration", "Defer it from the first release", "Keeps the first product self-contained until the core workflow is proven."));
            case QUALITY_GOALS -> qualityChoices(domain);
            case CONSTRAINTS -> List.of(
                    option("date-fixed", "Launch date is fixed", "Scope must shrink before quality or critical controls are compromised."),
                    option("budget-fixed", "Budget and team are fixed", "The release must favor a smaller thin slice and managed services."),
                    option("platform-fixed", "Keep the confirmed platform fixed and move scope or schedule before replacing it", "Requires the exact platform or technology constraint to be named."),
                    option("scope-fixed", "Required scope is fixed", "Time, staffing, or phased delivery must absorb the uncertainty."));
            case RISKS -> riskChoices(domain);
            case BUSINESS_RULES -> ruleChoices(domain);
            case METRICS -> metricChoices(domain);
        });
        result.add(option("not-decided", "Not decided yet",
                "Keep this as an explicit open decision instead of turning a suggestion into a project fact."));
        return List.copyOf(result);
    }

    private List<ChoiceOption> problemChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("intake-dropoff", "Reduce intake drop-off", "Prioritizes a shorter, clearer client experience with visible progress and recovery."),
                    option("slow-qualification", "Shorten review time", "Prioritizes complete intake information, clear ownership, and a timely next step."),
                    option("missing-intake-context", "Prevent missing client context", "Prioritizes relevant questions, document completeness, and safe correction before review."));
            case "booking product" -> List.of(
                    option("availability-conflicts", "Prevent availability conflicts", "Prioritizes accurate availability and conflict prevention before convenience features."),
                    option("slow-confirmation", "Shorten confirmation time", "Prioritizes response ownership, deadlines, and automatic status updates."),
                    option("missed-follow-up", "Prevent missed follow-up", "Prioritizes reliable reminders, delivery status, and recovery when messages fail."));
            case "healthcare product" -> List.of(
                    option("handoff-delay", "Reduce hand-off delays", "Prioritizes acknowledgment, escalation, and visibility of unaccepted work."),
                    option("missing-context", "Prevent missing care context", "Prioritizes required information, validation, and correction ownership."),
                    option("unclear-ownership", "Make ownership unambiguous", "Prioritizes role responsibility, status, and escalation rules."));
            case "fintech product" -> List.of(
                    option("failed-collection", "Reduce failed collection", "Prioritizes provider status, retry behavior, and customer recovery."),
                    option("approval-delay", "Shorten approval time", "Prioritizes authority, limits, expiry, and escalation."),
                    option("reconciliation-work", "Reduce reconciliation work", "Prioritizes authoritative statuses, audit history, and exception queues."));
            default -> List.of(
                    option("delay", "Reduce avoidable delay", "Prioritizes hand-offs, status visibility, and response deadlines."),
                    option("errors", "Prevent costly errors", "Prioritizes validation, ownership, and recoverable failure handling."),
                    option("visibility", "Make work visible", "Prioritizes trustworthy status, responsibility, and exception reporting."));
        };
    }

    private List<ChoiceOption> scopeChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("request-decision", "Complete intake-to-decision journey", "Covers intake, document upload, review, a clear decision, and client status."),
                    option("client-intake-first", "Client intake first", "Prioritizes a clear submission experience while staff handle review manually."),
                    option("staff-review-first", "Staff review first", "Proves review, assignment, and decision controls before broader client self-service."));
            case "booking product" -> List.of(
                    option("closed-booking", "Complete booking journey", "Covers request, confirmation, assignment, changes, completion, and visible recovery."),
                    option("operations-first", "Owner scheduling first", "Proves availability and assignment controls before broader customer self-service."),
                    option("customer-first", "Customers request and track bookings while owners resolve exceptional conflicts", "Keeps the customer journey self-service while a named owner handles unusual scheduling conflicts manually."));
            case "healthcare product" -> List.of(
                    option("closed-handoff", "Closed-loop care hand-off", "Covers creation, validation, acknowledgment, escalation, correction, and traceability."),
                    option("clinical-team-first", "Clinical-team workflow first", "Proves safety and accountability before any patient-facing experience."),
                    option("coordination-first", "Coordination visibility first", "Prioritizes queues and ownership while complex clinical edits remain manual."));
            case "multi-workspace SaaS product" -> List.of(
                    option("request-decision", "Request-to-decision journey", "Covers submission, policy validation, approval, rejection, history, and completion."),
                    option("controls-first", "Workspace controls first", "Proves membership, permissions, and policy enforcement before broad workflow features."),
                    option("requester-first", "Requester experience first", "Prioritizes submission and status while complex administration remains manual."));
            default -> List.of(
                    option("complete-thin-slice", "One complete end-to-end journey", "Delivers a usable outcome including errors and recovery before adding breadth."),
                    option("operator-first", "Internal operation first", "Validates process and controls before exposing a customer-facing experience."),
                    option("self-service-first", "User self-service first", "Prioritizes the external experience while keeping complex exceptions manual."));
        };
    }

    private List<ChoiceOption> userChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("client-submits", "Client owns intake and corrections", "Lets prospective clients submit and correct their own information without seeing internal review notes."),
                    option("coordinator-reviews", "Intake coordinator reviews completeness", "Creates a clear owner for missing information, routing, and response time."),
                    option("lawyer-decides", "Lawyer decides the next step", "Keeps eligibility and representation decisions with an accountable professional."),
                    option("admin-visibility", "Administrator sees status only", "Supports operations without granting unnecessary access to confidential intake details."));
            case "booking product" -> List.of(
                    option("owner-controls", "Owner controls confirmation and overrides", "Keeps schedule authority with the owner and requires a response deadline for pending requests."),
                    option("customer-cutoff", "Customer controls changes before a cutoff", "Enables self-service while requiring an explicit cutoff and clear handling afterward."),
                    option("professional-assignment", "Assigned professionals accept or decline work without changing customer terms", "Gives the assigned professional availability control while price and customer terms remain owner-controlled."),
                    option("state-based-authority", "Authority changes with booking status", "Requires explicit permissions for pending, confirmed, active, and completed states."));
            case "healthcare product" -> List.of(
                    option("sender-accountable", "Sending clinician owns complete hand-off data", "Makes the originator accountable for required context and corrections before acceptance."),
                    option("receiver-acknowledges", "Receiving clinician accepts responsibility", "Creates a clear transfer point with a deadline and escalation for silence."),
                    option("coordinator-escalates", "Coordinator resolves routing exceptions", "Keeps clinical decisions with clinicians while giving operational failures an owner."),
                    option("emergency-override", "Named clinician may use an emergency override", "Supports urgent care only with a reason, expiry, and complete audit history."));
            case "multi-workspace SaaS product" -> List.of(
                    option("requester-drafts", "Requester owns drafts and corrections", "Keeps submission efficient while preventing self-approval of consequential work."),
                    option("manager-threshold", "Manager approves within a limit", "Speeds routine decisions but requires a documented threshold and escalation."),
                    option("specialist-approval", "Specialist approves exceptional work", "Adds control for high-risk cases with routing rules and response deadlines."),
                    option("admin-no-self-approval", "Administrator configures but cannot self-approve", "Separates workspace administration from consequential business decisions."),
                    option("auditor-read-only", "Auditor receives read-only history", "Supports oversight without authority to alter the underlying work."));
            default -> List.of(
                    option("operator-decides", "Operator owns routine decisions", "Keeps daily work fast while reserving exceptions for an accountable approver."),
                    option("approver-controls", "Approver confirms consequential actions", "Adds control and auditability but requires response deadlines and escalation."),
                    option("state-based-authority", "Authority changes by workflow state", "Supports realistic hand-offs but requires explicit permission for every transition."));
        };
    }

    private List<ChoiceOption> workflowChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("request-correction", "Request missing information", "Keeps the intake editable and tells the client exactly what is still needed."),
                    option("route-reviewer", "Route to the right reviewer", "Uses a visible owner and response target instead of a generic shared queue."),
                    option("clear-next-step", "Send a clear next step", "Confirms whether the matter proceeds, needs more information, or cannot be accepted."));
            case "booking product" -> List.of(
                    option("owner-resolves", "Owner resolves conflicts", "Keeps assignment authority clear but requires a response deadline and escalation."),
                    option("offer-alternatives", "Offer alternative slots automatically", "Reduces waiting while requiring trustworthy availability and conflict prevention."),
                    option("expire-request", "Expire and notify everyone", "Prevents indefinite pending bookings but needs a clear expiry and recovery path."));
            case "healthcare product" -> List.of(
                    option("escalate-unacknowledged", "Escalate an unacknowledged hand-off", "Assigns a deadline, backup recipient, and visible owner so urgent work cannot disappear."),
                    option("reject-for-correction", "Return incomplete information for correction", "Protects record quality while preserving urgency and correction ownership."),
                    option("emergency-path", "Use a governed urgent path", "Allows faster handling only with named authority and complete audit history."));
            case "multi-workspace SaaS product" -> List.of(
                    option("delegate-backup", "Delegate to a backup approver", "Keeps work moving but requires delegation scope, expiry, and audit history."),
                    option("escalate-deadline", "Escalate after a response deadline", "Preserves primary authority while preventing requests from waiting indefinitely."),
                    option("return-requester", "Return the item to its requester", "Avoids silent state changes and makes the required correction explicit."));
            default -> List.of(
                    option("human-queue", "Send exceptions to a human queue", "Keeps edge cases visible and recoverable without pretending they are automated."),
                    option("retry-escalate", "Retry automatically, then escalate", "Handles temporary failures quickly while preventing silent endless retries."),
                    option("stop-explain", "Stop and explain the next action", "Avoids uncertain state changes and tells the responsible person how to recover."));
        };
    }

    private List<ChoiceOption> entityChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("minimum-intake", "Keep only necessary intake information", "Limits collection to information needed for conflict, eligibility, and next-step review."),
                    option("review-scoped-access", "Access follows the review role", "Restricts confidential information to the people actively responsible for the intake."),
                    option("document-integrity", "Preserve document versions and status", "Makes uploads, replacements, review state, and errors understandable and traceable."),
                    option("retention-owner", "Retention follows an accountable policy", "Keeps deletion and retention decisions out of ad hoc user behavior."));
            case "healthcare product" -> List.of(
                    option("minimum-handoff", "Minimum necessary hand-off record", "Limits the record to information needed for safe coordination and explicit validation."),
                    option("role-and-state-access", "Access depends on role and hand-off state", "Removes broad visibility after responsibility changes while preserving traceability."),
                    option("correction-history", "Corrections append to immutable history", "Preserves what changed, why, and who authorized it."),
                    option("retention-owner", "Retention follows an accountable policy owner", "Keeps deletion and retention outside ad hoc user behavior."));
            case "multi-workspace SaaS product" -> List.of(
                    option("workspace-owned", "Every work item belongs to one workspace", "Creates a hard tenant boundary for queries, permissions, exports, and deletion."),
                    option("membership-scoped", "Access follows active membership", "Removes visibility when membership ends while preserving governed audit history."),
                    option("policy-versioned", "Decisions retain the policy version used", "Makes later review explainable when approval rules change."),
                    option("audit-immutable", "Decision history cannot be overwritten", "Preserves who acted, when, under which authority, and why."));
            default -> List.of(
                    option("least-privilege", "Role- and state-based access ends when workflow responsibility ends", "Requires an auditable access-removal event at the state transition."),
                    option("owner-controlled", "Record owner controls sharing", "Gives the accountable user control but needs administrative recovery rules."),
                    option("policy-controlled", "Organization policy controls access", "Provides consistent permissions and auditability across users and teams."),
                    option("immutable-history", "Keep an immutable change history", "Supports disputes and audits but increases retention and privacy considerations."));
        };
    }

    private List<ChoiceOption> qualityChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("security-first", "Protect confidential client information", "Prioritizes least privilege, secure defaults, and auditable access failures."),
                    option("integrity-first", "Never lose an intake or document", "Prioritizes upload integrity, idempotent submission, and visible recovery."),
                    option("status-clarity", "Make every intake status clear", "Prioritizes plain-language feedback, ownership, and an understandable next step."),
                    option("accessible-intake", "Keep intake accessible on mobile", "Requires readable, keyboard-friendly, responsive completion on realistic devices."));
            case "booking product" -> List.of(
                    option("no-double-booking", "Prevent confirmed double-bookings", "Prioritizes atomic availability checks and conflict tests before convenience features."),
                    option("protect-location", "Protect address and access details", "Prioritizes state-based visibility, auditability, and prompt access removal."),
                    option("reliable-notices", "Make status notifications dependable", "Prioritizes delivery tracking, retry limits, and visible manual recovery."),
                    option("fast-mobile", "Keep mobile booking responsive", "Requires a measured completion-time target on ordinary mobile connections."));
            case "healthcare product" -> List.of(
                    option("acknowledgment-target", "Bound acknowledgment time", "Requires a measurable deadline, escalation, and visibility of unaccepted hand-offs."),
                    option("access-integrity", "Prevent incorrect patient-record access", "Prioritizes least privilege, permission tests, and auditable denial events."),
                    option("traceable-changes", "Make every correction traceable", "Prioritizes immutable history, correction reasons, and accountable ownership."));
            case "multi-workspace SaaS product" -> List.of(
                    option("tenant-isolation", "Prevent cross-workspace access", "Prioritizes tenant-bound queries, permission tests, and auditable denial events."),
                    option("decision-integrity", "Never lose or duplicate an approval", "Prioritizes idempotent transitions and visible recovery from partial failure."),
                    option("approval-availability", "Keep submission and approval available", "Prioritizes graceful degradation and a recovery target for the core journey."),
                    option("core-screen-speed", "Keep core screens responsive", "Requires a measured response-time target at realistic workspace size and load."));
            default -> List.of(
                    option("security-first", "Prevent unauthorized access", "Prioritizes permission tests, secure defaults, and auditable access failures."),
                    option("integrity-first", "Prevent lost or inconsistent work", "Prioritizes validation, idempotency, backups, and visible recovery."),
                    option("reliability-first", "Keep the core journey available", "Prioritizes monitoring, graceful degradation, and recovery targets."),
                    option("speed-first", "Keep the core action responsive", "Prioritizes a measured response-time target on realistic devices and load."));
        };
    }

    private List<ChoiceOption> riskChoices(DomainProfile domain) {
        if (domain.label().equals("legal services product")) {
            return List.of(
                    option("confidentiality-breach", "Confidential intake information is exposed", "Prioritizes least privilege, secure handling, and a tested incident owner."),
                    option("lost-submission", "A submitted intake or document is lost", "Prioritizes durable submission, visible status, and recoverable upload handling."),
                    option("wrong-intake-decision", "A client receives the wrong next step", "Prioritizes accountable review, clear decision evidence, and correction."),
                    option("missed-response", "A completed intake receives no response", "Prioritizes ownership, response targets, escalation, and client-visible status."));
        }
        if (domain.label().equals("healthcare product")) {
            return List.of(
                    option("missed-escalation", "Unacknowledged urgent hand-off", "Prioritizes deadlines, backup recipients, and evidence that escalation occurred."),
                    option("wrong-patient-access", "Wrong-patient or wrong-role access", "Prioritizes identity checks, least privilege, denial testing, and audit events."),
                    option("silent-correction", "Untraceable care-context correction", "Prioritizes immutable history and ownership of corrected information."),
                    option("outage-continuity", "Care coordination during an outage", "Requires a safe continuity path and later reconciliation."));
        }
        return List.of(
                option("prevent", "Prevent the failure by design", "Use validation, permissions, and safe defaults before harm can occur."),
                option("detect-recover", "Named operations owner detects alerts, contains impact, and runs tested recovery", "Requires an observable detection signal and a specific recovery result."),
                option("human-review", "Require human review", "Route consequential or ambiguous cases to an accountable person."),
                option("limit-impact", "Limit the blast radius", "Isolate users, records, or transactions so one failure cannot spread."));
    }

    private List<ChoiceOption> ruleChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("conflict-before-review", "Check conflicts before detailed review", "Protects confidentiality by limiting access before the practice confirms it can proceed."),
                    option("consent-before-upload", "Capture consent before document upload", "Makes the client understand how sensitive information will be used before transmission."),
                    option("eligibility-human-owned", "A named reviewer owns eligibility", "Keeps consequential intake decisions accountable instead of inventing automated judgment."),
                    option("override-audited", "Exceptions require a reason", "Allows unusual cases without erasing who changed the normal rule and why."));
            case "booking product" -> List.of(
                    option("change-cutoff", "Changes follow a clear cutoff", "Defines when customers may self-serve and when an owner decides the exception."),
                    option("availability-before-confirm", "Availability is rechecked before confirmation", "Prevents stale schedules from creating a confirmed conflict during simultaneous requests."),
                    option("assignment-acceptance", "Professional acceptance expires at a confirmed deadline and then reassigns", "Keeps bookings from waiting indefinitely and makes reassignment after silence an explicit state transition."),
                    option("override-reason", "Owner overrides require a reason", "Allows exceptional handling while preserving who changed the normal rule and why."));
            case "healthcare product" -> List.of(
                    option("acknowledgment-deadline", "Acknowledgment has a governed deadline", "Defines when responsibility transfers and when silence escalates."),
                    option("required-context", "Required context before routine acceptance", "Prevents incomplete hand-offs while preserving a governed urgent path."),
                    option("correction-approval", "Consequential corrections require a reason", "Maintains accountability without blocking clerical fixes indefinitely."),
                    option("emergency-break-glass", "Emergency access is time-limited", "Permits urgent access only with named authority, expiry, and audit review."));
            case "multi-workspace SaaS product" -> List.of(
                    option("no-self-approval", "Requester cannot approve their own work", "Enforces separation of duties and defines reassignment when no approver is available."),
                    option("approval-threshold", "Approval authority follows a threshold", "Routes consequential requests upward while keeping routine work fast."),
                    option("delegation-expiry", "Delegation has scope and expiry", "Prevents permanent authority drift and preserves who delegated each decision."),
                    option("override-audited", "Overrides require reason and audit history", "Allows exceptional handling without erasing the normal policy decision."));
            default -> List.of(
                    option("strict-rule", "Always enforce the rule", "Consistency takes priority; exceptions require a separate governed process."),
                    option("role-override", "Allow a named role to override", "Supports unusual cases but requires a reason and immutable audit event."),
                    option("threshold-review", "Review only above a threshold", "Keeps routine work fast while escalating consequential cases."),
                    option("manual-first", "Keep the decision manual initially", "Avoids invented automation until real examples define a safe rule."));
        };
    }

    private List<ChoiceOption> metricChoices(DomainProfile domain) {
        return switch (domain.label()) {
            case "legal services product" -> List.of(
                    option("intake-completion-rate", "Completed intake rate", "Measures whether prospective clients can finish the guided process without avoidable drop-off."),
                    option("intake-review-time", "Time to a clear next step", "Measures whether ownership and complete information make review faster."),
                    option("correction-rate", "Intakes returned for correction", "Shows whether the questions and upload guidance collect usable information."),
                    option("privacy-guardrail", "Privacy incident guardrail", "Prevents faster intake from being called successful if confidentiality risk increases."));
            case "booking product" -> List.of(
                    option("booking-conflict-rate", "Confirmed booking-conflict rate", "Measures whether the release eliminates the scheduling error it was funded to prevent."),
                    option("confirmation-time", "Request-to-confirmation time", "Measures whether ownership and deadlines make bookings faster for customers."),
                    option("recovery-volume", "Manual recovery volume", "Reveals notification, conflict, and assignment failures hidden by completion counts."),
                    option("repeat-booking", "Successful repeat booking", "Tests whether customers and owners trust the new process enough to reuse it."));
            case "healthcare product" -> List.of(
                    option("acknowledgment-time", "Time to acknowledged hand-off", "Measures whether responsibility transfers promptly instead of remaining pending."),
                    option("overdue-rate", "Overdue unacknowledged hand-offs", "Measures the coordination failure the workflow must expose and escalate."),
                    option("correction-rate", "Hand-offs returned for correction", "Shows whether required context improves information quality."),
                    option("safety-guardrail", "Safety and privacy incident guardrail", "Prevents faster work from being called successful if harm increases."));
            case "multi-workspace SaaS product" -> List.of(
                    option("approval-cycle-time", "Request-to-decision time", "Measures whether clearer ownership and escalation reduce approval delay."),
                    option("overdue-request-rate", "Overdue request rate", "Shows whether work remains stuck despite visible status."),
                    option("policy-exception-rate", "Policy exception volume", "Reveals whether configured rules fit real work or create manual burden."),
                    option("successful-workspaces", "Workspaces repeatedly completing the journey", "Measures adoption through outcomes rather than account creation."));
            default -> List.of(
                    option("completion-time", "Time to successful completion", "Measures whether the core job becomes meaningfully faster."),
                    option("failure-rate", "Failure or rework rate", "Measures whether the release prevents the errors it was designed to reduce."),
                    option("successful-adoption", "Successful repeated use", "Measures whether intended users finish the workflow and return."),
                    option("support-burden", "Support and exception volume", "Measures operational complexity that user activity alone can hide."));
        };
    }

    private boolean containsAny(String source, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(source::contains);
    }

    private boolean containsSignal(String source, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(term -> Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])").matcher(source).find());
    }

    private boolean containsPositiveSignal(String source, String... terms) {
        for (String term : terms) {
            var matcher = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])")
                    .matcher(source);
            while (matcher.find()) {
                String before = source.substring(Math.max(0, matcher.start() - 55), matcher.start());
                String after = source.substring(matcher.end(), Math.min(source.length(), matcher.end() + 70));
                boolean negatedBefore = Pattern.compile(
                        ".*\\b(no|without|exclude|excludes|excluding|defer|deferred)\\b(?:\\W+\\w+){0,5}\\W*$")
                        .matcher(before).matches();
                boolean deferredAfter = Pattern.compile(
                        "^(?:\\W+\\w+){0,7}\\W+(wait|waits|deferred|excluded|optional|out of scope|not required|later release|future release|added later|planned later)\\b")
                        .matcher(after).find();
                if (!negatedBefore && !deferredAfter) {
                    return true;
                }
            }
        }
        return false;
    }

    private record DomainProfile(
            String label,
            String primaryPeople,
            String workflow,
            String records,
            String qualityFocus,
            String whyFocus) {

        String decisionFocus() {
            return switch (label) {
                case "legal services product" -> "client eligibility or confidentiality";
                case "healthcare product" -> "safety or privacy";
                case "fintech product" -> "financial control";
                case "AI product" -> "AI safety or human-oversight";
                case "data integration product" -> "data ownership or correction";
                default -> "scope or operational";
            };
        }

        String exclusionExamples() {
            return switch (label) {
                case "booking product" -> "online payment, route optimization, or native mobile apps";
                case "healthcare product" -> "diagnosis, treatment recommendations, or broad record-system replacement";
                case "fintech product" -> "credit decisions, cross-border settlement, or automated dispute judgment";
                case "AI product" -> "autonomous action, training on customer data, or unsupported content types";
                case "data integration product" -> "historical migration, real-time sync, or bidirectional updates";
                default -> "advanced automation, historical migration, or non-essential integrations";
            };
        }

        String successMeasure() {
            return switch (label) {
                case "booking product" -> "fewer booking conflicts or faster confirmed bookings";
                case "healthcare product" -> "faster acknowledged hand-offs without safety or privacy incidents";
                case "fintech product" -> "more successful payments with less reconciliation effort";
                case "AI product" -> "more accepted outputs without increasing unsafe or incorrect results";
                case "data integration product" -> "fresher accepted data with fewer manual corrections";
                default -> "faster successful completion with fewer errors";
            };
        }
    }

    private QuestionDefinition question(
            String key,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            RiskLevel riskLevel,
            boolean required) {
        return new QuestionDefinition(key, category, questionText, whyWeAsk, riskLevel, required,
                Set.of(InterviewCategory.USERS, InterviewCategory.STAKEHOLDERS, InterviewCategory.EXCLUSIONS,
                        InterviewCategory.ENTITIES, InterviewCategory.INTEGRATIONS, InterviewCategory.RISKS,
                        InterviewCategory.BUSINESS_RULES).contains(category),
                choicesFor(category));
    }

    private List<ChoiceOption> choicesFor(InterviewCategory category) {
        return switch (category) {
            case PROBLEM -> List.of(
                    option("manual-work", "Reduce manual work", "Replace repetitive, error-prone steps."),
                    option("visibility", "Improve visibility", "Give people a clearer view of work, status, or data."),
                    option("service", "Improve customer or user service", "Make a key task easier, faster, or more reliable."),
                    option("accuracy", "Improve accuracy or compliance", "Reduce mistakes and strengthen controls."));
            case USERS -> List.of(
                    option("customers", "Customers or end users", "People receiving the service or product."),
                    option("employees", "Internal staff", "People who operate the day-to-day process."),
                    option("managers", "Managers or approvers", "People who monitor, decide, or approve work."),
                    option("admins", "Administrators", "People who configure access, data, or settings."),
                    option("partners", "External partners", "Suppliers, vendors, or other outside collaborators."));
            case STAKEHOLDERS -> List.of(
                    option("sponsor", "Executive sponsor", "Owns the outcome or funding."),
                    option("product-owner", "Product owner", "Sets priorities and accepts the delivered work."),
                    option("operations", "Operations lead", "Owns the process changing in practice."),
                    option("security", "Security or compliance", "Reviews risk, privacy, or regulatory obligations."),
                    option("support", "Support or service team", "Handles questions and problems after launch."));
            case SCOPE -> List.of(
                    option("records", "Create and manage records", "Capture, update, and organize core information."),
                    option("dashboard", "Dashboard and status tracking", "Show progress, queues, or operational visibility."),
                    option("approvals", "Review and approvals", "Route work for checks or decisions."),
                    option("notifications", "Notifications and reminders", "Keep the right people informed at the right time."),
                    option("reporting", "Search and reporting", "Find information and understand outcomes."));
            case EXCLUSIONS -> List.of(
                    option("native-mobile", "Native mobile apps", "A separate iOS or Android application."),
                    option("payments", "Payments or billing", "Processing money, subscriptions, or invoices."),
                    option("migration", "Historic data migration", "Moving and cleaning old data in this release."),
                    option("integrations", "Complex third-party integrations", "Non-essential external-system connections."),
                    option("advanced-ai", "Advanced AI automation", "Autonomous or sophisticated AI capabilities."));
            case WORKFLOWS -> List.of(
                    option("submit", "Submit a request or item", "A user starts the main process."),
                    option("review", "Review and approve", "A responsible person checks or decides."),
                    option("manage", "Manage a record", "A user creates, updates, or closes a core record."),
                    option("monitor", "Monitor status", "A user views queues, progress, or exceptions."),
                    option("resolve", "Resolve an issue", "A user handles an exception or completes a follow-up."));
            case ENTITIES -> List.of(
                    option("profiles", "People or organization profiles", "Users, customers, teams, or accounts."),
                    option("requests", "Requests or work items", "Tasks, cases, tickets, or submissions."),
                    option("transactions", "Transactions or events", "Orders, appointments, actions, or activity records."),
                    option("documents", "Documents or files", "Attachments, evidence, or generated documents."),
                    option("settings", "Settings and permissions", "Roles, access rules, configuration, or preferences."));
            case INTEGRATIONS -> List.of(
                    option("none", "No integration for the first release", "Keep the first release self-contained."),
                    option("identity", "Identity or single sign-on", "Use an existing account or login provider."),
                    option("email", "Email or messaging", "Send notifications or receive updates."),
                    option("files", "File storage", "Store or retrieve documents and attachments."),
                    option("business-system", "Existing business system", "Connect to an ERP, CRM, payment, or specialist platform."));
            case QUALITY_GOALS -> List.of(
                    option("security", "Security", "Protect accounts, access, and sensitive information."),
                    option("privacy", "Privacy", "Collect and use personal data responsibly."),
                    option("speed", "Speed", "Keep key screens and actions responsive."),
                    option("accessibility", "Accessibility", "Make the experience usable for people with disabilities."),
                    option("reliability", "Reliability", "Keep the service available and recoverable."));
            case CONSTRAINTS -> List.of(
                    option("deadline", "Fixed deadline", "A launch date or delivery milestone cannot move."),
                    option("budget", "Fixed budget", "Spend or team capacity is limited."),
                    option("technology", "Required technology", "A specified stack, platform, or hosting environment."),
                    option("compliance", "Compliance requirement", "A legal, security, or industry obligation."),
                    option("team", "Limited team capacity", "Available skills or people constrain delivery."));
            case RISKS -> List.of(
                    option("adoption", "Low user adoption", "People may not change their current habits."),
                    option("data", "Data quality or privacy", "Data may be incomplete, inaccurate, or sensitive."),
                    option("integration", "Integration dependency", "Another system or team could delay the work."),
                    option("delivery", "Delivery capacity", "Time, budget, or technical complexity could block launch."),
                    option("security", "Security incident", "Unauthorized access or misuse could harm users."));
            case BUSINESS_RULES -> List.of(
                    option("eligibility", "Eligibility checks", "Decide who can start or complete an action."),
                    option("approval", "Approval rules", "Require the right role to review or decide."),
                    option("validation", "Validation rules", "Require complete, accurate, or correctly formatted data."),
                    option("calculation", "Calculations", "Derive totals, scores, prices, or deadlines."),
                    option("retention", "Retention and access rules", "Define who can see data and how long it remains."));
            case METRICS -> List.of(
                    option("adoption", "User adoption", "Active users or completed onboarding."),
                    option("time", "Time saved", "Less time to complete the key job."),
                    option("quality", "Quality improvement", "Fewer errors, rework items, or complaints."),
                    option("conversion", "Completion or conversion", "More people finish the intended journey."),
                    option("satisfaction", "User satisfaction", "Better feedback, support outcomes, or loyalty."));
        };
    }

    private ChoiceOption option(String key, String label, String description) {
        return new ChoiceOption(key, label, description);
    }
}
