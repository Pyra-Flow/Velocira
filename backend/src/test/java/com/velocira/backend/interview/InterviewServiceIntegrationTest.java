package com.velocira.backend.interview;

import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewSessionStatus;
import com.velocira.backend.interview.repository.InterviewAnswerRepository;
import com.velocira.backend.interview.repository.InterviewSessionRepository;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the durable interview, automatic readiness gate, and answer revision path. */
@SpringBootTest
class InterviewServiceIntegrationTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private InterviewAnswerRepository answerRepository;

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Test
    void completedBriefAutomaticallyUnlocksGenerationAndKeepsAnswerRevisionHistory() {
        TestProject fixture = createProject("Interview-ready project");
        InterviewDtos.SessionResponse session = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        while (session.nextQuestion() != null) {
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            session.nextQuestion().questionKey(),
                            InterviewAnswerDisposition.ANSWERED,
                            "Confirmed details for " + session.nextQuestion().category() + "."));
        }

        assertThat(session.status()).isEqualTo(InterviewSessionStatus.CONFIRMED);
        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.brief().content().path("problem").asText()).contains("PROBLEM");
        assertThat(projectRepository.findById(fixture.project().getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.READY_FOR_GENERATION);
        interviewService.assertGenerationReady(fixture.project().getId(), fixture.owner().getId());

        InterviewDtos.AnswerResponse problem = session.answers().stream()
                .filter(answer -> answer.questionKey().equals("problem"))
                .findFirst().orElseThrow();
        InterviewDtos.SessionResponse revised = interviewService.reviseAnswer(
                fixture.project().getId(), problem.id(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "A revised, evidence-backed problem statement."));

        assertThat(revised.status()).isEqualTo(InterviewSessionStatus.CONFIRMED);
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .filteredOn(answer -> answer.questionKey().equals("problem"))
                .hasSize(2);
    }

    @Test
    void explicitUnknownRemainsVisibleAndDoesNotUnlockGeneration() {
        TestProject fixture = createProject("Interview gaps project");
        InterviewDtos.SessionResponse session = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        InterviewDtos.SessionResponse unknown = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest(session.nextQuestion().questionKey(), InterviewAnswerDisposition.UNKNOWN, null));

        assertThat(unknown.readiness().generationReady()).isFalse();
        assertThat(unknown.openQuestions())
                .anyMatch(question -> question.status().name().equals("ACKNOWLEDGED_UNKNOWN") && question.material());
        assertThat(unknown.assumptions()).anyMatch(assumption -> assumption.status().equals("OPEN"));
        assertThat(unknown.nextQuestion()).isNotNull();
        assertThat(unknown.nextQuestion().category()).isEqualTo(session.nextQuestion().category());
        assertThat(unknown.nextQuestion().questionText()).contains("earlier answer");
    }

    @Test
    void reviewingACompleteInterviewDoesNotRelockSrsGeneration() {
        TestProject fixture = createProject("Review-ready interview project");
        InterviewDtos.SessionResponse session = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        while (session.nextQuestion() != null) {
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            session.nextQuestion().questionKey(),
                            InterviewAnswerDisposition.ANSWERED,
                            "Confirmed review evidence for " + session.nextQuestion().category() + "."));
        }

        InterviewDtos.SessionResponse reviewed = interviewService.reopen(
                fixture.project().getId(), fixture.owner().getId());

        assertThat(reviewed.status()).isEqualTo(InterviewSessionStatus.CONFIRMED);
        assertThat(reviewed.readiness().generationReady()).isTrue();
        assertThat(reviewed.readiness().blockers()).isEmpty();
        assertThat(reviewed.nextQuestion()).isNull();
        interviewService.assertGenerationReady(fixture.project().getId(), fixture.owner().getId());
    }

    @Test
    void startingAnExistingInterviewReturnsTheSameSession() {
        TestProject fixture = createProject("Idempotent interview project");

        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        InterviewDtos.SessionResponse repeated = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(repeated.id()).isEqualTo(first.id());
    }

    @Test
    void startsWithAnIndividualQuestionInsteadOfCopyingTheProjectDescriptionIntoAnswers() {
        TestProject fixture = createProject("Honest discovery project");

        InterviewDtos.SessionResponse session = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(session.answers()).isEmpty();
        assertThat(session.nextQuestion()).isNotNull();
        assertThat(session.nextQuestion().questionKey()).isEqualTo("problem");
        assertThat(session.readiness().generationReady()).isFalse();
    }

    @Test
    void retiresAutoFilledAnswersWithoutDiscardingARealAnswer() {
        TestProject fixture = createProject("Mixed evidence project");
        InterviewDtos.SessionResponse started = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        interviewService.submitAnswer(fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest(started.nextQuestion().questionKey(), InterviewAnswerDisposition.ANSWERED,
                        "Care teams need a reliable way to coordinate their handoffs."));

        var persistedSession = sessionRepository.findByProjectIdAndOwnerId(fixture.project().getId(), fixture.owner().getId())
                .orElseThrow();
        answerRepository.save(InterviewAnswerEntity.builder()
                .session(persistedSession)
                .category(com.velocira.backend.interview.model.InterviewCategory.USERS)
                .questionKey("users")
                .questionText("Who will use this?")
                .whyWeAsk("This was incorrectly derived from the initial brief.")
                .disposition(InterviewAnswerDisposition.ANSWERED)
                .answerText("The initial project description")
                .source("PROJECT_BRIEF")
                .evidence(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("derived", true))
                .build());

        InterviewDtos.SessionResponse repaired = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(repaired.answers()).extracting(InterviewDtos.AnswerResponse::questionKey).containsExactly("problem");
        assertThat(repaired.nextQuestion()).isNotNull();
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .filteredOn(answer -> answer.questionKey().equals("users"))
                .allSatisfy(answer -> assertThat(answer.current()).isFalse());
    }

    @Test
    void domainContextTailorsAndPersistsTheQuestionTheOwnerActuallySees() {
        TestProject fixture = createProject("ClinicFlow");

        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        assertThat(first.nextQuestion().questionText()).contains("care-coordination");
        assertThat(first.nextQuestion().selectionReason()).isNotBlank();
        assertThat(first.nextQuestion().missingRequirement()).isNotBlank();
        assertThat(first.nextQuestion().sourceContext()).contains("project:title", "project:description");

        InterviewDtos.SessionResponse answered = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest(first.nextQuestion().questionKey(), InterviewAnswerDisposition.ANSWERED,
                        "Care teams need one safe place to coordinate appointments."));

        assertThat(answered.answers()).first().extracting(InterviewDtos.AnswerResponse::questionText)
                .asString().contains("care-coordination");
        assertThat(answered.answers().getFirst().planner()).isEqualTo("deterministic-discovery-strategist-v3");
        assertThat(answered.answers().getFirst().selectionReason()).isNotBlank();
        assertThat(answered.answers().getFirst().confirmedContextUsed()).isNotEmpty();
        assertThat(answered.answers().getFirst().candidateScores()).isNotEmpty();
    }

    @Test
    void simpleProjectsAskFewerQuestionsWhileRegulatedProjectsReceiveDeeperCoverageWithoutDuplicates() {
        TestProject simple = createProject(
                "Simple notes",
                "A small personal note organizer for one owner.",
                null,
                "solo business owners",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse simpleSession = interviewService.start(
                simple.project().getId(), simple.owner().getId());
        assertThat(simpleSession.readiness().requiredCategoryCount()).isEqualTo(7);

        TestProject regulated = createProject(
                "ClinicPayments",
                "A healthcare payment workflow for patient billing, staff approvals, private records, and an external payment API.",
                "Healthcare",
                "patients, clinic staff, and finance approvers",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                regulated.project().getId(), regulated.owner().getId());
        assertThat(session.readiness().requiredCategoryCount()).isGreaterThan(6);
        assertThat(session.nextQuestion().questionText()).containsAnyOf("care-coordination", "patient", "payment");

        var askedKeys = new HashSet<String>();
        while (session.nextQuestion() != null) {
            assertThat(askedKeys.add(session.nextQuestion().questionKey())).isTrue();
            session = interviewService.submitAnswer(
                    regulated.project().getId(), regulated.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            session.nextQuestion().questionKey(),
                            InterviewAnswerDisposition.ANSWERED,
                            "Confirmed owner evidence for " + session.nextQuestion().category() + "."));
        }

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(askedKeys).contains("entities", "integrations", "risks", "business-rules", "stakeholders");
    }

    @Test
    void explicitlyDeferredFeaturesDoNotInflateASimpleInterview() {
        TestProject fixture = createProject(
                "CleanSlot evaluation",
                "A booking web app where customers request cleaning appointments and owners assign cleaners.",
                "Home services",
                "cleaning-company owners, cleaners, and customers",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        var asked = new HashSet<String>();

        while (session.nextQuestion() != null) {
            asked.add(session.nextQuestion().questionKey());
            String answer = switch (session.nextQuestion().category()) {
                case PROBLEM -> "Availability conflicts create rework; preventing confirmed double-bookings is the priority.";
                case USERS -> "Customers request changes, owners confirm and reassign, and cleaners update assigned job status.";
                case SCOPE -> "The booking journey is required; payment and an external API are out of scope for this release.";
                case WORKFLOWS -> "Requests remain pending until owner confirmation; conflicts return alternatives and expired work is visible.";
                case QUALITY_GOALS -> "Two requests must never confirm the same cleaner and slot, with automated concurrency tests.";
                case METRICS -> "Reduce conflict rework from eight percent to below one percent during the first eight weeks.";
                case CONSTRAINTS -> "The two-person team and responsive web platform are fixed; scope may shrink.";
                default -> "This category should not be required for the explicitly bounded simple release.";
            };
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            session.nextQuestion().questionKey(), InterviewAnswerDisposition.ANSWERED, answer));
        }

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.readiness().requiredCategoryCount()).isEqualTo(7);
        assertThat(asked).containsExactlyInAnyOrder(
                "problem", "users", "scope", "workflows", "quality", "metrics", "constraints");
    }

    @Test
    void aMaterialCutoffDiscoveredInAnAnswerAddsOnlyTheBusinessRuleFollowUp() {
        TestProject fixture = createProject(
                "CleanSlot rule evaluation",
                "A booking web app where customers request cleaning appointments and owners assign cleaners.",
                "Home services",
                "cleaning-company owners, cleaners, and customers",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        var asked = new HashSet<String>();

        while (session.nextQuestion() != null) {
            asked.add(session.nextQuestion().questionKey());
            String answer = switch (session.nextQuestion().category()) {
                case PROBLEM -> "Prevent confirmed booking conflicts that currently create avoidable rework.";
                case USERS -> "Customers may change a booking before a cutoff; owners decide later exceptions and cleaners update status.";
                case BUSINESS_RULES -> "Customers may cancel until twenty-four hours before service; the owner may override with a recorded reason.";
                case SCOPE -> "Complete booking from request through completion; route optimization waits.";
                case WORKFLOWS -> "Pending requests are confirmed, reassigned, expired, or recovered through an owner queue.";
                case QUALITY_GOALS -> "Concurrent requests must never confirm the same cleaner and slot.";
                case METRICS -> "Reduce conflict rework from eight percent to below one percent in eight weeks.";
                case CONSTRAINTS -> "The team and launch window are fixed while scope may shrink.";
                default -> "No additional complexity is required for this bounded booking release.";
            };
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            session.nextQuestion().questionKey(), InterviewAnswerDisposition.ANSWERED, answer));
        }

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.readiness().requiredCategoryCount()).isEqualTo(8);
        assertThat(asked).contains("business-rules").doesNotContain("entities", "integrations", "risks", "stakeholders");
    }

    @Test
    void suggestedChoicesAndCustomAnswerAreStoredAsOneAuditableAnswer() {
        TestProject fixture = createProject("Choice-based interview project");
        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(first.nextQuestion().allowsMultiple()).isFalse();
        assertThat(first.nextQuestion().options()).extracting(InterviewDtos.ChoiceOptionResponse::key)
                .contains("delay", "not-decided");

        InterviewDtos.SessionResponse answered = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "The current handoff takes too long.", List.of("delay")));

        InterviewDtos.AnswerResponse answer = answered.answers().getFirst();
        assertThat(answer.selectedOptionKeys()).containsExactly("delay");
        assertThat(answer.customAnswerText()).isEqualTo("The current handoff takes too long.");
        assertThat(answer.answerText()).isEqualTo("Reduce avoidable delay; The current handoff takes too long.");
        assertThat(answered.nextQuestion().questionText())
                .startsWith("Because the priority is reducing avoidable delay,");
    }

    @Test
    void notDecidedRemainsABlockerAndCannotBeMixedWithConfirmedChoices() {
        TestProject fixture = createProject("Undecided interview project");
        InterviewDtos.SessionResponse started = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());

        assertThatThrownBy(() -> interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        null, List.of("not-decided", "delay"))))
                .hasMessageContaining("cannot be combined");

        InterviewDtos.SessionResponse undecided = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "The founder will validate this next week.", List.of("not-decided")));

        assertThat(undecided.readiness().generationReady()).isFalse();
        assertThat(undecided.openQuestions()).anyMatch(question ->
                question.questionKey().equals("incomplete-problem") && question.material());
        assertThat(undecided.nextQuestion().questionKey()).isEqualTo("problem");
        assertThat(undecided.nextQuestion().questionText())
                .contains("earlier answer did not identify one concrete outcome");
    }

    @Test
    void tentativeAnswerProducesAPreciseAuditedFollowUpInsteadOfBecomingARequirement() {
        TestProject fixture = createProject("Tentative discovery project");
        InterviewDtos.SessionResponse started = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        String firstQuestion = started.nextQuestion().questionText();

        InterviewDtos.SessionResponse tentative = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED, "not sure"));

        assertThat(tentative.readiness().generationReady()).isFalse();
        assertThat(tentative.openQuestions()).anyMatch(question ->
                question.questionKey().equals("incomplete-problem") && question.material());
        assertThat(tentative.nextQuestion().questionKey()).isEqualTo("problem");
        assertThat(tentative.nextQuestion().questionText()).isNotEqualTo(firstQuestion)
                .contains("earlier answer did not identify one concrete outcome");

        InterviewDtos.SessionResponse clarified = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "The current approval hand-off causes two-day delays and avoidable rework."));

        assertThat(clarified.nextQuestion().questionKey()).isNotEqualTo("problem");
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .filteredOn(answer -> answer.questionKey().equals("problem"))
                .hasSize(2);
    }

    private TestProject createProject(String name) {
        return createProject(
                name,
                "A sufficiently detailed project description for adaptive discovery integration testing.",
                null,
                null,
                ProjectType.WEB_APP);
    }

    private TestProject createProject(
            String name,
            String description,
            String industry,
            String targetAudience,
            ProjectType type) {
        UserEntity owner = userRepository.save(UserEntity.builder()
                .fullName("Interview Tester")
                .email("interview-" + UUID.randomUUID() + "@example.test")
                .password("not-used-by-this-test")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
        ProjectEntity project = projectRepository.save(ProjectEntity.builder()
                .owner(owner)
                .name(name)
                .description(description)
                .industry(industry)
                .targetAudience(targetAudience)
                .type(type)
                .status(ProjectStatus.DRAFT)
                .build());
        return new TestProject(owner, project);
    }

    private record TestProject(UserEntity owner, ProjectEntity project) {
    }
}
