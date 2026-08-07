package com.velocira.backend.interview.service;

import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.RiskLevel;
import com.velocira.backend.project.model.ProjectEntity;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned question wording and order for the deterministic baseline.
 * The later LLM planner may choose among these categories, but cannot make a
 * browser-provided question into a product rule.
 */
@Component
public class DiscoveryQuestionCatalog {

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
            return new InterviewDtos.QuestionResponse(key, category, questionText, whyWeAsk, riskLevel,
                    allowsMultiple, options.stream().map(ChoiceOption::toResponse).toList());
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
                        "What problem are you trying to solve, and why does it matter now?",
                        "This anchors the goal and prevents features from being mistaken for the problem.", RiskLevel.HIGH, true),
                question("users", InterviewCategory.USERS,
                        "Who will use this, and what does each main user need to accomplish?",
                        "User groups define actors, permissions, and the workflows we need to document.", RiskLevel.HIGH, true),
                question("stakeholders", InterviewCategory.STAKEHOLDERS,
                        "Who can make decisions, approve work, or be affected by the system?",
                        "Stakeholders reveal approvals, ownership, and conflicting priorities early.", RiskLevel.MEDIUM, false),
                question("scope", InterviewCategory.SCOPE,
                        "What must the first release do? Describe the smallest successful outcome.",
                        "A clear scope keeps requirements testable and limits accidental expansion.", RiskLevel.HIGH, true),
                question("exclusions", InterviewCategory.EXCLUSIONS,
                        "What is explicitly out of scope for this release?",
                        "Explicit exclusions protect the team from assumptions and scope creep.", RiskLevel.HIGH, true),
                question("workflows", InterviewCategory.WORKFLOWS,
                        "Walk me through the most important user journey from start to finish.",
                        "Core workflows become the backbone for requirements, tests, and UX flows.", RiskLevel.HIGH, true),
                question("entities", InterviewCategory.ENTITIES,
                        "What important information will the system store or manage?",
                        "Key entities reveal data ownership, records, and validation needs.", RiskLevel.HIGH, true),
                question("integrations", InterviewCategory.INTEGRATIONS,
                        "Which existing systems, APIs, files, devices, or services must it connect to?",
                        "Integrations add security, reliability, and delivery risks that must be explicit.", RiskLevel.HIGH, true),
                question("quality", InterviewCategory.QUALITY_GOALS,
                        "What quality goals matter most—for example speed, security, accessibility, uptime, or privacy?",
                        "Quality targets make non-functional requirements measurable instead of implied.", RiskLevel.HIGH, true),
                question("constraints", InterviewCategory.CONSTRAINTS,
                        "What constraints must we respect: budget, deadline, technology, compliance, team capacity, or platform?",
                        "Constraints shape feasible options and expose delivery trade-offs.", RiskLevel.HIGH, true),
                question("risks", InterviewCategory.RISKS,
                        "What could make this project fail, harm users, or block delivery?",
                        "Named risks can be mitigated; hidden risks cannot be assessed.", RiskLevel.HIGH, true),
                question("business-rules", InterviewCategory.BUSINESS_RULES,
                        "Are there policies, calculations, eligibility rules, or approvals the system must enforce?",
                        "Business rules must come from you; the interview will not invent them.", RiskLevel.HIGH, false),
                question("metrics", InterviewCategory.METRICS,
                        "How will you know the project is successful after launch?",
                        "Success metrics make the brief measurable and help prioritize later decisions.", RiskLevel.MEDIUM, false));

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
     * Keeps the catalog bounded and reviewable while making the exact prompt
     * relevant to the project context. The AI planner still decides which
     * unanswered category is most valuable next; this layer ensures the
     * durable question and answer history use the same safe wording.
     */
    public QuestionDefinition tailorForProject(QuestionDefinition question, ProjectEntity project) {
        DomainProfile domain = detectDomain(project);
        if (domain == null) {
            return question;
        }

        String prompt = switch (question.category()) {
            case PROBLEM -> "For this " + domain.label() + ", what problem should improve for " + domain.primaryPeople()
                    + ", and why is it important now?";
            case USERS -> "Which " + domain.primaryPeople() + " will use this " + domain.label()
                    + ", and what does each need to accomplish?";
            case WORKFLOWS -> "Walk through the most important " + domain.workflow()
                    + " from start to finish. Where can it fail, wait, or need approval?";
            case ENTITIES -> "Which " + domain.records()
                    + " must the system store or manage, and who is allowed to change them?";
            case INTEGRATIONS -> "Which existing systems, APIs, files, or devices must this " + domain.label()
                    + " connect to?";
            case QUALITY_GOALS -> "Which quality goals are non-negotiable for this " + domain.label()
                    + "—for example " + domain.qualityFocus() + "?";
            case CONSTRAINTS -> "What deadline, budget, technology, compliance, or delivery constraints apply to this "
                    + domain.label() + "?";
            case RISKS -> "What could harm " + domain.primaryPeople()
                    + ", create a serious operational failure, or block delivery for this " + domain.label() + "?";
            case BUSINESS_RULES -> "Which policies, calculations, eligibility checks, or approvals must this "
                    + domain.label() + " enforce?";
            default -> question.questionText();
        };
        String why = question.whyWeAsk() + " For this project, it also clarifies " + domain.whyFocus() + ".";
        return new QuestionDefinition(question.key(), question.category(), prompt, why, question.riskLevel(),
                question.required(), question.allowsMultiple(), question.options());
    }

    private DomainProfile detectDomain(ProjectEntity project) {
        String source = (project.getName() + " " + project.getDescription() + " "
                + (project.getIndustry() == null ? "" : project.getIndustry())).toLowerCase();
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
        if (containsAny(source, "ai", "llm", "assistant", "agent", "model", "machine learning", "copilot", "rag")) {
            return new DomainProfile("AI product", "end users and human reviewers", "human-in-the-loop interaction", "inputs, outputs, feedback, and evaluations", "safety, privacy, and reliability", "human oversight, data boundaries, and safe fallback behaviour");
        }
        if (containsAny(source, "internal", "employee", "staff", "erp", "admin", "operations", "back office")) {
            return new DomainProfile("internal operations tool", "staff and approvers", "operational hand-off", "work items, approvals, and reports", "permissions, auditability, and reliability", "roles, approvals, and existing operational systems");
        }
        return null;
    }

    private boolean containsAny(String source, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(source::contains);
    }

    private record DomainProfile(
            String label,
            String primaryPeople,
            String workflow,
            String records,
            String qualityFocus,
            String whyFocus) {
    }

    private QuestionDefinition question(
            String key,
            InterviewCategory category,
            String questionText,
            String whyWeAsk,
            RiskLevel riskLevel,
            boolean required) {
        return new QuestionDefinition(key, category, questionText, whyWeAsk, riskLevel, required,
                category != InterviewCategory.PROBLEM && category != InterviewCategory.WORKFLOWS,
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
