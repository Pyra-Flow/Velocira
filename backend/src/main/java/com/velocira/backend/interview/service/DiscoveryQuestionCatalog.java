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
            boolean required) {

        InterviewDtos.QuestionResponse toResponse() {
            return new InterviewDtos.QuestionResponse(key, category, questionText, whyWeAsk, riskLevel);
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
        return new QuestionDefinition(question.key(), question.category(), prompt, why, question.riskLevel(), question.required());
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
        return new QuestionDefinition(key, category, questionText, whyWeAsk, riskLevel, required);
    }
}
