package com.velocira.backend.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.exceptions.InterviewSessionNotFoundException;
import com.velocira.backend.interview.exceptions.InterviewStateException;
import com.velocira.backend.interview.model.AssumptionEntity;
import com.velocira.backend.interview.model.AssumptionStatus;
import com.velocira.backend.interview.model.DecisionEntity;
import com.velocira.backend.interview.model.DecisionStatus;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.model.InterviewSessionEntity;
import com.velocira.backend.interview.model.InterviewSessionStatus;
import com.velocira.backend.interview.model.OpenQuestionEntity;
import com.velocira.backend.interview.model.OpenQuestionStatus;
import com.velocira.backend.interview.model.RiskLevel;
import com.velocira.backend.interview.repository.AssumptionRepository;
import com.velocira.backend.interview.repository.DecisionRepository;
import com.velocira.backend.interview.repository.InterviewAnswerRepository;
import com.velocira.backend.interview.repository.InterviewSessionRepository;
import com.velocira.backend.interview.repository.OpenQuestionRepository;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owner-authorized workflow for adaptive discovery evidence and canonical briefs. */
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final ProjectRepository projectRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AssumptionRepository assumptionRepository;
    private final OpenQuestionRepository openQuestionRepository;
    private final DecisionRepository decisionRepository;
    private final DiscoveryQuestionCatalog questionCatalog;
    private final DiscoveryQuestionPlanner questionPlanner;
    private final DiscoveryReadinessService readinessService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional
    public InterviewDtos.SessionResponse start(UUID projectId, UUID ownerId) {
        // Lock the parent before checking for a session. Without this, two browser
        // requests can both see no session and race the one-session-per-project constraint.
        ProjectEntity project = ownedProjectForUpdate(projectId, ownerId);
        ensureInterviewAllowed(project);
        InterviewSessionEntity session = sessionRepository.findByProjectIdAndOwnerId(projectId, ownerId).orElse(null);
        if (session == null) {
            session = InterviewSessionEntity.builder()
                    .project(project)
                    .owner(project.getOwner())
                    .status(InterviewSessionStatus.IN_PROGRESS)
                    .canonicalBrief(objectMapper.createObjectNode())
                    .readinessSnapshot(objectMapper.createObjectNode())
                    .build();
            session = sessionRepository.save(session);
            if (project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.FAILED) {
                project.setStatus(ProjectStatus.DISCOVERY);
                project.setProgress(0);
                projectRepository.save(project);
            }
            rebuildDerivedState(session);
            auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.INTERVIEW_STARTED,
                    "Started discovery interview for project: " + project.getName());
        } else if (retireAutoFilledBriefAnswers(session, project)) {
            rebuildDerivedState(session);
            auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.INTERVIEW_REOPENED,
                    "Restored the owner questions for project: " + project.getName());
        }
        ensureCurrentQuestionPlan(session);
        return response(session);
    }

    @Transactional
    public InterviewDtos.SessionResponse summary(UUID projectId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        InterviewSessionEntity session = requiredSession(projectId, ownerId);
        ensureCurrentQuestionPlan(session);
        return response(session);
    }

    @Transactional(readOnly = true)
    public InterviewDtos.HistoryResponse history(UUID projectId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        InterviewSessionEntity session = requiredSession(projectId, ownerId);
        return new InterviewDtos.HistoryResponse(answerRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toAnswerResponse)
                .toList());
    }

    @Transactional
    public InterviewDtos.SessionResponse submitAnswer(
            UUID projectId,
            UUID ownerId,
            InterviewDtos.AnswerRequest request) {
        InterviewSessionEntity session = lockedSession(projectId, ownerId);
        ProjectEntity project = session.getProject();
        ensureInterviewAllowed(project);
        InterviewDtos.QuestionResponse plannedQuestion = currentQuestionPlan(session);
        DiscoveryQuestionCatalog.QuestionDefinition question = plannedQuestion != null
                && plannedQuestion.questionKey().equals(request.questionKey().trim())
                ? questionCatalog.fromResponse(plannedQuestion, project)
                : questionCatalog.tailorForProject(questionCatalog.requireByKey(request.questionKey().trim()), project);
        validateAnswer(question, request);
        reopenForEdit(session, project);

        InterviewAnswerEntity existing = answerRepository
                .findBySessionIdAndQuestionKeyAndCurrentTrue(session.getId(), question.key()).orElse(null);
        int revision = existing == null ? 1 : existing.getRevisionNumber() + 1;
        if (existing != null) {
            existing.setCurrent(false);
            answerRepository.save(existing);
            answerRepository.flush();
        }

        InterviewAnswerEntity answer = InterviewAnswerEntity.builder()
                .session(session)
                .category(question.category())
                .questionKey(question.key())
                .questionText(question.questionText())
                .whyWeAsk(question.whyWeAsk())
                .disposition(request.disposition())
                .answerText(normalizeAnswer(question, request))
                .revisionNumber(revision)
                .current(true)
                .source("USER")
                .evidence(answerEvidence(question, request, plannedQuestion))
                .build();
        answer = answerRepository.save(answer);
        refreshDerivedEvidence(session, answer);
        rebuildDerivedState(session);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.INTERVIEW_ANSWER_RECORDED,
                "Recorded discovery answer for " + question.category() + " in project: " + project.getName());
        return response(session);
    }

    @Transactional
    public InterviewDtos.SessionResponse reviseAnswer(
            UUID projectId,
            UUID answerId,
            UUID ownerId,
            InterviewDtos.AnswerRequest request) {
        InterviewSessionEntity session = lockedSession(projectId, ownerId);
        InterviewAnswerEntity original = answerRepository.findByIdAndSessionId(answerId, session.getId())
                .orElseThrow(() -> new InterviewStateException("That answer does not belong to this interview."));
        if (!original.isCurrent()) {
            throw new InterviewStateException("Only the current revision of an answer can be edited.");
        }
        if (!original.getQuestionKey().equals(request.questionKey().trim())) {
            throw new InterviewStateException("An answer can only be revised with its original interview question.");
        }
        return submitAnswer(projectId, ownerId, request);
    }

    @Transactional
    public InterviewDtos.SessionResponse confirm(UUID projectId, UUID ownerId) {
        InterviewSessionEntity session = lockedSession(projectId, ownerId);
        ProjectEntity project = session.getProject();
        ensureInterviewAllowed(project);
        DiscoveryReadinessService.Assessment assessment = rebuildDerivedState(session);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.INTERVIEW_BRIEF_CONFIRMED,
                "Rechecked discovery brief for project: " + project.getName()
                        + (assessment.generationReady() ? " (generation ready)" : " (visible gaps remain)"));
        return response(session);
    }

    @Transactional
    public InterviewDtos.SessionResponse reopen(UUID projectId, UUID ownerId) {
        InterviewSessionEntity session = lockedSession(projectId, ownerId);
        ProjectEntity project = session.getProject();
        ensureInterviewAllowed(project);
        DiscoveryReadinessService.Assessment assessment = rebuildDerivedState(session);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.INTERVIEW_REOPENED,
                "Reviewed discovery brief for project: " + project.getName()
                        + (assessment.generationReady() ? " (answers remain generation ready)" : " (follow-up required)"));
        return response(session);
    }

    /** Generation calls this boundary so an unconfirmed or risky brief cannot enter the worker queue. */
    @Transactional(readOnly = true)
    public void assertGenerationReady(UUID projectId, UUID ownerId) {
        InterviewSessionEntity session = sessionRepository.findByProjectIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new InterviewStateException(
                        "Complete the required discovery questions before requesting generation."));
        List<InterviewAnswerEntity> answers = answerRepository
                .findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(session.getId());
        DiscoveryReadinessService.Assessment assessment = readinessService.assess(session.getProject(), answers);
        if (!assessment.generationReady()) {
            String detail = assessment.blockers().isEmpty()
                    ? "Complete the required discovery questions before requesting generation."
                    : String.join(" ", assessment.blockers());
            throw new InterviewStateException("Generation is blocked: " + detail);
        }
    }

    private DiscoveryReadinessService.Assessment rebuildDerivedState(InterviewSessionEntity session) {
        List<InterviewAnswerEntity> answers = answerRepository.findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(session.getId());
        DiscoveryReadinessService.Assessment assessment = readinessService.assess(session.getProject(), answers);
        syncOpenQuestions(session, answers, assessment.findings());
        ObjectNode brief = assessment.canonicalBrief().deepCopy();
        brief.put("projectId", session.getProject().getId().toString());
        brief.put("projectName", session.getProject().getName());
        brief.put("projectType", session.getProject().getType().name());
        session.setCanonicalBrief(brief);
        session.setReadinessSnapshot(assessment.snapshot().deepCopy());
        session.setBriefVersion(session.getBriefVersion() + 1);
        ProjectEntity project = session.getProject();
        if (assessment.generationReady()) {
            // There is no separate confirmation screen: the user has already
            // confirmed every required fact by answering the interview. Make
            // the transition durable so a refresh cannot re-lock generation.
            session.setStatus(InterviewSessionStatus.CONFIRMED);
            if (session.getConfirmedAt() == null) {
                session.setConfirmedAt(Instant.now());
            }
            if (project.getStatus() != ProjectStatus.ARCHIVED && project.getStatus() != ProjectStatus.GENERATING) {
                project.setStatus(ProjectStatus.READY_FOR_GENERATION);
                project.setProgress(100);
                projectRepository.save(project);
            }
        } else {
            session.setStatus(assessment.minimumComplete()
                    ? InterviewSessionStatus.READY_FOR_CONFIRMATION
                    : InterviewSessionStatus.IN_PROGRESS);
            session.setConfirmedAt(null);
            if (project.getStatus() == ProjectStatus.READY_FOR_GENERATION) {
                project.setStatus(ProjectStatus.DISCOVERY);
                project.setProgress(0);
                projectRepository.save(project);
            }
        }
        refreshCurrentQuestionPlan(session, answers);
        sessionRepository.save(session);
        return assessment;
    }

    private void refreshDerivedEvidence(InterviewSessionEntity session, InterviewAnswerEntity answer) {
        List<AssumptionEntity> activeAssumptions = assumptionRepository
                .findBySessionIdAndCategoryAndStatus(session.getId(), answer.getCategory(), AssumptionStatus.OPEN);
        activeAssumptions.forEach(assumption -> assumption.setStatus(AssumptionStatus.RESOLVED));
        assumptionRepository.saveAll(activeAssumptions);

        List<DecisionEntity> activeDecisions = decisionRepository
                .findBySessionIdAndCategoryAndStatus(session.getId(), answer.getCategory(), DecisionStatus.ACTIVE);
        activeDecisions.forEach(decision -> decision.setStatus(DecisionStatus.SUPERSEDED));
        decisionRepository.saveAll(activeDecisions);

        if (answer.getDisposition() == InterviewAnswerDisposition.ANSWERED) {
            decisionRepository.save(DecisionEntity.builder()
                    .session(session)
                    .sourceAnswerId(answer.getId())
                    .category(answer.getCategory())
                    .statement(answer.getAnswerText().trim())
                    .rationale("User-provided answer to the discovery interview question.")
                    .status(DecisionStatus.ACTIVE)
                    .build());
        } else {
            assumptionRepository.save(AssumptionEntity.builder()
                    .session(session)
                    .sourceAnswerId(answer.getId())
                    .category(answer.getCategory())
                    .statement("The project team has not confirmed " + display(answer.getCategory())
                            + "; no generated requirement may treat it as a settled fact.")
                    .rationale("The owner explicitly marked the discovery question "
                            + answer.getDisposition().name().toLowerCase() + ".")
                    .impact(RiskLevel.HIGH)
                    .status(AssumptionStatus.OPEN)
                    .material(true)
                    .build());
        }
    }

    private void syncOpenQuestions(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<DiscoveryReadinessService.Finding> findings) {
        Map<InterviewCategory, InterviewAnswerEntity> byCategory = answers.stream()
                .collect(Collectors.toMap(InterviewAnswerEntity::getCategory, Function.identity(), (left, right) -> right));
        Set<String> currentFindingKeys = findings.stream()
                .map(DiscoveryReadinessService.Finding::key)
                .collect(Collectors.toSet());
        for (DiscoveryReadinessService.Finding finding : findings) {
            OpenQuestionEntity openQuestion = openQuestionRepository
                    .findBySessionIdAndQuestionKey(session.getId(), finding.key())
                    .orElseGet(() -> OpenQuestionEntity.builder()
                            .session(session)
                            .questionKey(finding.key())
                            .category(finding.category())
                            .origin("READINESS_RULE")
                            .build());
            InterviewAnswerEntity source = byCategory.get(finding.category());
            openQuestion.setSourceAnswerId(source == null ? null : source.getId());
            openQuestion.setQuestionText(finding.questionText());
            openQuestion.setReason(finding.reason());
            openQuestion.setRiskLevel(finding.riskLevel());
            openQuestion.setStatus(finding.status());
            openQuestion.setMaterial(finding.material());
            openQuestionRepository.save(openQuestion);
        }
        openQuestionRepository.findBySessionIdOrderByRiskLevelDescCreatedAtAsc(session.getId()).stream()
                .filter(question -> "READINESS_RULE".equals(question.getOrigin()))
                .filter(question -> !currentFindingKeys.contains(question.getQuestionKey()))
                .forEach(question -> {
                    question.setStatus(OpenQuestionStatus.RESOLVED);
                    question.setMaterial(false);
                    question.setReason("This gap is not required for the current project complexity profile.");
                    openQuestionRepository.save(question);
                });
    }

    private InterviewDtos.SessionResponse response(InterviewSessionEntity session) {
        List<InterviewAnswerEntity> currentAnswers = answerRepository.findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(session.getId());
        DiscoveryReadinessService.Assessment assessment = readinessService.assess(session.getProject(), currentAnswers);
        List<OpenQuestionEntity> openQuestions = openQuestionRepository
                .findBySessionIdOrderByRiskLevelDescCreatedAtAsc(session.getId());
        InterviewDtos.QuestionResponse nextQuestion = currentQuestionPlan(session);
        return new InterviewDtos.SessionResponse(
                session.getId(),
                session.getProject().getId(),
                session.getStatus(),
                nextQuestion,
                currentAnswers.stream().map(this::toAnswerResponse).toList(),
                assumptionRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream().map(this::toAssumptionResponse).toList(),
                openQuestions.stream().map(this::toOpenQuestionResponse).toList(),
                decisionRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream().map(this::toDecisionResponse).toList(),
                new InterviewDtos.BriefResponse(session.getBriefVersion(), session.getCanonicalBrief(), session.getConfirmedAt()),
                new InterviewDtos.ReadinessResponse(
                        assessment.minimumComplete(), assessment.generationReady(), assessment.answeredRequiredCategories(),
                        assessment.requiredCategoryCount(), assessment.blockers(), session.getReadinessSnapshot()),
                session.getReopenedAt(),
                session.getUpdatedAt());
    }

    private InterviewDtos.AnswerResponse toAnswerResponse(InterviewAnswerEntity answer) {
        DiscoveryQuestionCatalog.QuestionDefinition question = questionCatalog.requireByKey(answer.getQuestionKey());
        InterviewDtos.QuestionResponse plan = answerQuestionPlan(answer);
        List<InterviewDtos.ChoiceOptionResponse> options = plan == null
                ? question.options().stream().map(DiscoveryQuestionCatalog.ChoiceOption::toResponse).toList()
                : plan.options();
        return new InterviewDtos.AnswerResponse(answer.getId(), answer.getQuestionKey(), answer.getCategory(),
                answer.getQuestionText(), answer.getWhyWeAsk(), answer.getDisposition(), answer.getAnswerText(),
                answer.getRevisionNumber(), answer.isCurrent(), answer.getCreatedAt(), question.allowsMultiple(),
                options, selectedOptionKeys(answer), customAnswerText(answer),
                plan == null ? null : plan.selectionReason(),
                plan == null ? null : plan.missingRequirement(),
                plan == null ? List.of() : plan.sourceContext(),
                plan == null ? List.of() : plan.confirmedContextUsed(),
                plan == null ? List.of() : plan.assumptionsToValidate(),
                plan == null ? List.of() : plan.candidateScores(),
                plan == null ? null : plan.planner(),
                plan == null ? null : plan.model());
    }

    private InterviewDtos.AssumptionResponse toAssumptionResponse(AssumptionEntity assumption) {
        return new InterviewDtos.AssumptionResponse(assumption.getId(), assumption.getCategory(), assumption.getStatement(),
                assumption.getRationale(), assumption.getImpact(), assumption.getStatus().name(), assumption.isMaterial());
    }

    private InterviewDtos.OpenQuestionResponse toOpenQuestionResponse(OpenQuestionEntity question) {
        return new InterviewDtos.OpenQuestionResponse(question.getId(), question.getQuestionKey(), question.getCategory(),
                question.getQuestionText(), question.getReason(), question.getRiskLevel(), question.getStatus(), question.isMaterial());
    }

    private InterviewDtos.DecisionResponse toDecisionResponse(DecisionEntity decision) {
        return new InterviewDtos.DecisionResponse(decision.getId(), decision.getCategory(), decision.getStatement(),
                decision.getRationale(), decision.getStatus().name());
    }

    private InterviewSessionEntity requiredSession(UUID projectId, UUID ownerId) {
        return sessionRepository.findByProjectIdAndOwnerId(projectId, ownerId)
                .orElseThrow(InterviewSessionNotFoundException::new);
    }

    private InterviewSessionEntity lockedSession(UUID projectId, UUID ownerId) {
        return sessionRepository.findByProjectIdAndOwnerIdForUpdate(projectId, ownerId)
                .orElseThrow(InterviewSessionNotFoundException::new);
    }

    private ProjectEntity ownedProject(UUID projectId, UUID ownerId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    private ProjectEntity ownedProjectForUpdate(UUID projectId, UUID ownerId) {
        return projectRepository.findByIdAndOwnerIdForUpdate(projectId, ownerId)
                .orElseGet(() -> ownedProject(projectId, ownerId));
    }

    private void ensureInterviewAllowed(ProjectEntity project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.GENERATING) {
            throw new InterviewStateException("This project cannot be changed while it is " + project.getStatus() + ".");
        }
    }

    /**
     * Repairs sessions created by the brief auto-fill experiment. Those rows are
     * retained as history, but cannot masquerade as owner-provided evidence.
     */
    private boolean retireAutoFilledBriefAnswers(InterviewSessionEntity session, ProjectEntity project) {
        List<InterviewAnswerEntity> currentAnswers = answerRepository
                .findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(session.getId());
        List<InterviewAnswerEntity> autoFilledAnswers = currentAnswers.stream()
                .filter(this::isAutoFilledBriefAnswer)
                .toList();
        if (autoFilledAnswers.isEmpty()) {
            return false;
        }
        autoFilledAnswers.forEach(answer -> answer.setCurrent(false));
        answerRepository.saveAll(autoFilledAnswers);
        answerRepository.flush();
        session.setStatus(InterviewSessionStatus.IN_PROGRESS);
        session.setConfirmedAt(null);
        session.setReopenedAt(Instant.now());
        if (project.getStatus() != ProjectStatus.ARCHIVED && project.getStatus() != ProjectStatus.GENERATING) {
            project.setStatus(ProjectStatus.DISCOVERY);
            project.setProgress(0);
            projectRepository.save(project);
        }
        return true;
    }

    private boolean isAutoFilledBriefAnswer(InterviewAnswerEntity answer) {
        return "PROJECT_BRIEF".equals(answer.getSource()) && answer.getEvidence().path("derived").asBoolean(false);
    }

    private void reopenForEdit(InterviewSessionEntity session, ProjectEntity project) {
        if (session.getStatus() == InterviewSessionStatus.CONFIRMED) {
            session.setConfirmedAt(null);
            session.setReopenedAt(Instant.now());
            project.setStatus(ProjectStatus.DISCOVERY);
            project.setProgress(0);
            projectRepository.save(project);
        }
        session.setStatus(InterviewSessionStatus.IN_PROGRESS);
    }

    private void validateAnswer(
            DiscoveryQuestionCatalog.QuestionDefinition question,
            InterviewDtos.AnswerRequest request) {
        List<String> selectedOptionKeys = request.selectedOptionKeys();
        boolean hasCustomAnswer = request.answerText() != null && !request.answerText().isBlank();
        if (request.disposition() != InterviewAnswerDisposition.ANSWERED) {
            if (!selectedOptionKeys.isEmpty()) {
                throw new InterviewStateException("Only answered questions can include selected choices.");
            }
            return;
        }
        if (!hasCustomAnswer && selectedOptionKeys.isEmpty()) {
            throw new InterviewStateException("Choose an answer or add a custom response. You can also choose Unknown or Skip so the gap remains visible.");
        }
        if (selectedOptionKeys.contains("not-decided") && selectedOptionKeys.size() > 1) {
            throw new InterviewStateException("Not decided yet cannot be combined with a confirmed decision.");
        }
        if (!question.allowsMultiple() && selectedOptionKeys.size() > 1) {
            throw new InterviewStateException("This question accepts one suggested choice. Add details in the custom response if needed.");
        }
        if (selectedOptionKeys.stream().distinct().count() != selectedOptionKeys.size()) {
            throw new InterviewStateException("Each suggested choice can only be selected once.");
        }
        Set<String> supportedOptionKeys = question.options().stream()
                .map(DiscoveryQuestionCatalog.ChoiceOption::key)
                .collect(Collectors.toSet());
        if (!supportedOptionKeys.containsAll(selectedOptionKeys)) {
            throw new InterviewStateException("One or more selected choices do not belong to this question.");
        }
    }

    private String normalizeAnswer(
            DiscoveryQuestionCatalog.QuestionDefinition question,
            InterviewDtos.AnswerRequest request) {
        if (request.disposition() != InterviewAnswerDisposition.ANSWERED) {
            return null;
        }
        Map<String, String> labelsByKey = question.options().stream()
                .collect(Collectors.toMap(DiscoveryQuestionCatalog.ChoiceOption::key,
                        DiscoveryQuestionCatalog.ChoiceOption::label));
        List<String> parts = new ArrayList<>(request.selectedOptionKeys().stream()
                .map(labelsByKey::get)
                .toList());
        if (request.answerText() != null && !request.answerText().isBlank()) {
            parts.add(request.answerText().trim());
        }
        return String.join("; ", parts);
    }

    private JsonNode answerEvidence(
            DiscoveryQuestionCatalog.QuestionDefinition question,
            InterviewDtos.AnswerRequest request,
            InterviewDtos.QuestionResponse plannedQuestion) {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("source", "user_interview");
        evidence.put("disposition", request.disposition().name());
        evidence.put("recordedAt", Instant.now().toString());
        var selectedOptionKeys = evidence.putArray("selectedOptionKeys");
        request.selectedOptionKeys().forEach(selectedOptionKeys::add);
        if (request.disposition() == InterviewAnswerDisposition.ANSWERED
                && request.answerText() != null && !request.answerText().isBlank()) {
            evidence.put("customAnswerText", request.answerText().trim());
        }
        var selectedOptions = evidence.putArray("selectedOptions");
        question.options().stream()
                .filter(option -> request.selectedOptionKeys().contains(option.key()))
                .forEach(option -> selectedOptions.addObject()
                        .put("key", option.key())
                        .put("label", option.label()));
        if (plannedQuestion != null && plannedQuestion.questionKey().equals(question.key())) {
            evidence.set("questionPlan", objectMapper.valueToTree(plannedQuestion));
        }
        return evidence;
    }

    private void ensureCurrentQuestionPlan(InterviewSessionEntity session) {
        if (session.getCurrentQuestionPlan() != null && !session.getCurrentQuestionPlan().isEmpty()) {
            return;
        }
        List<InterviewAnswerEntity> answers = answerRepository
                .findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(session.getId());
        refreshCurrentQuestionPlan(session, answers);
        sessionRepository.save(session);
    }

    private void refreshCurrentQuestionPlan(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers) {
        List<OpenQuestionEntity> openQuestions = openQuestionRepository
                .findBySessionIdOrderByRiskLevelDescCreatedAtAsc(session.getId());
        InterviewDtos.QuestionResponse planned = questionPlanner.nextQuestion(session, answers, openQuestions);
        session.setCurrentQuestionPlan(planned == null
                ? objectMapper.createObjectNode()
                : objectMapper.valueToTree(planned));
    }

    private InterviewDtos.QuestionResponse currentQuestionPlan(InterviewSessionEntity session) {
        JsonNode plan = session.getCurrentQuestionPlan();
        if (plan == null || plan.isEmpty()) {
            return null;
        }
        try {
            return normalizeQuestionPlan(objectMapper.treeToValue(plan, InterviewDtos.QuestionResponse.class));
        } catch (Exception ex) {
            return null;
        }
    }

    private InterviewDtos.QuestionResponse answerQuestionPlan(InterviewAnswerEntity answer) {
        JsonNode plan = answer.getEvidence().path("questionPlan");
        if (!plan.isObject() || plan.isEmpty()) {
            return null;
        }
        try {
            return normalizeQuestionPlan(objectMapper.treeToValue(plan, InterviewDtos.QuestionResponse.class));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> selectedOptionKeys(InterviewAnswerEntity answer) {
        JsonNode selectedOptionKeys = answer.getEvidence().path("selectedOptionKeys");
        if (!selectedOptionKeys.isArray()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        selectedOptionKeys.forEach(option -> {
            if (option.isTextual() && !option.asText().isBlank()) {
                keys.add(option.asText());
            }
        });
        return List.copyOf(keys);
    }

    private InterviewDtos.QuestionResponse normalizeQuestionPlan(InterviewDtos.QuestionResponse plan) {
        return new InterviewDtos.QuestionResponse(
                plan.questionKey(), plan.category(), plan.questionText(), plan.whyWeAsk(), plan.riskLevel(),
                plan.allowsMultiple(), plan.options() == null ? List.of() : plan.options(),
                plan.selectionReason() == null ? "Persisted discovery question." : plan.selectionReason(),
                plan.missingRequirement() == null ? "A requirement needed by downstream documents." : plan.missingRequirement(),
                plan.sourceContext() == null ? List.of() : plan.sourceContext(),
                plan.confirmedContextUsed() == null ? List.of() : plan.confirmedContextUsed(),
                plan.assumptionsToValidate() == null ? List.of() : plan.assumptionsToValidate(),
                plan.candidateScores() == null ? List.of() : plan.candidateScores(),
                plan.planner() == null ? "persisted-question-plan" : plan.planner(),
                plan.model() == null ? "unknown" : plan.model());
    }

    private String customAnswerText(InterviewAnswerEntity answer) {
        String customAnswer = answer.getEvidence().path("customAnswerText").asText("");
        if (!customAnswer.isBlank()) {
            return customAnswer;
        }
        return selectedOptionKeys(answer).isEmpty() ? answer.getAnswerText() : null;
    }

    private String display(InterviewCategory category) {
        return category.name().toLowerCase().replace('_', ' ');
    }
}
