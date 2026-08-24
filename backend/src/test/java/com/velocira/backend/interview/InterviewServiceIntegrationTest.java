package com.velocira.backend.interview;

import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewCategory;
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
        session = completeInterview(fixture, session, false, new HashSet<>());

        assertThat(session.status()).isEqualTo(InterviewSessionStatus.CONFIRMED);
        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.brief().content().path("problem").asText()).contains("Users experience two-day approval delays");
        assertThat(projectRepository.findById(fixture.project().getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.READY_FOR_GENERATION);
        interviewService.assertGenerationReady(fixture.project().getId(), fixture.owner().getId());

        InterviewDtos.AnswerResponse problem = session.answers().stream()
                .filter(answer -> answer.questionKey().equals("problem"))
                .findFirst().orElseThrow();
        InterviewDtos.SessionResponse revised = interviewService.reviseAnswer(
                fixture.project().getId(), problem.id(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "Users experience two-day approval delays and avoidable rework; reduce the delayed outcome first."));

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
        assertThat(unknown.nextQuestion().questionKey()).isNotEqualTo(session.nextQuestion().questionKey());
    }

    @Test
    void reviewingACompleteInterviewDoesNotRelockSrsGeneration() {
        TestProject fixture = createProject("Review-ready interview project");
        InterviewDtos.SessionResponse session = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        session = completeInterview(fixture, session, false, new HashSet<>());

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
                        "Care teams experience two-day hand-off delays and avoidable rework; reduce the delayed outcome first."));

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
        session = completeInterview(regulated, session, false, askedKeys);

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(askedKeys).contains("entities", "integrations", "risks", "business-rules", "stakeholders");
    }

    @Test
    void serviceBookingProjectsRequireCrossCuttingDecisionCoverage() {
        TestProject fixture = createProject(
                "CleanSlot evaluation",
                "A booking web app where customers request cleaning appointments and owners assign cleaners.",
                "Home services",
                "cleaning-company owners, cleaners, and customers",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        var asked = new HashSet<String>();
        session = completeInterview(fixture, session, true, asked);

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.readiness().requiredCategoryCount()).isEqualTo(13);
        assertThat(asked).containsExactlyInAnyOrder(
                "problem", "users", "stakeholders", "scope", "exclusions", "workflows", "entities",
                "integrations", "quality", "constraints", "risks", "business-rules", "metrics");
    }

    @Test
    void serviceBookingReadinessIncludesTheBusinessRuleCoverage() {
        TestProject fixture = createProject(
                "CleanSlot rule evaluation",
                "A booking web app where customers request cleaning appointments and owners assign cleaners.",
                "Home services",
                "cleaning-company owners, cleaners, and customers",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        var asked = new HashSet<String>();
        session = completeInterview(fixture, session, true, asked);

        assertThat(session.readiness().generationReady()).isTrue();
        assertThat(session.readiness().requiredCategoryCount()).isEqualTo(13);
        assertThat(asked).contains("business-rules", "entities", "integrations", "risks", "stakeholders");
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
        assertThat(answer.answerText())
                .contains("Reduce avoidable delay")
                .contains("Prioritizes hand-offs, status visibility, and response deadlines")
                .contains("The current handoff takes too long.");
        InterviewAnswerEntity persisted = answerRepository.findById(answer.id()).orElseThrow();
        assertThat(persisted.getEvidence().path("selectedOptions").get(0).path("description").asText())
                .contains("hand-offs", "status visibility", "response deadlines");
        assertThat(persisted.getEvidence().path("selectedOptions").get(0).path("confirmedMeaning").asText())
                .contains("Reduce avoidable delay", "Prioritizes hand-offs");
        assertThat(answered.nextQuestion().questionText())
                .doesNotContain("priority is", "release focus", "already prioritized");
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
                question.questionKey().startsWith("incomplete-problem-facet-") && question.material());
        assertThat(undecided.nextQuestion().questionKey()).isEqualTo("problem");
        assertThat(undecided.nextQuestion().questionText())
                .contains("Who experiences the problem");
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
                question.questionKey().startsWith("incomplete-problem-facet-") && question.material());
        assertThat(tentative.nextQuestion().questionKey()).isEqualTo("problem");
        assertThat(tentative.nextQuestion().questionText()).isNotEqualTo(firstQuestion)
                .contains("Who experiences the problem");

        InterviewDtos.SessionResponse clarified = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "Users experience two-day approval delays and avoidable rework; reduce the delayed outcome first."));

        assertThat(clarified.nextQuestion().questionKey()).isNotEqualTo("problem");
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .filteredOn(answer -> answer.questionKey().equals("problem"))
                .hasSize(2);
    }

    @Test
    void categoryValidatorAsksOneMissingFacetAndRetainsBothWorkflowDecisions() {
        TestProject fixture = createProject(
                "Workflow coverage project",
                "A request workflow for customers and operations staff.",
                null,
                "customers and operations staff",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        int guard = 0;
        while (session.nextQuestion() != null
                && session.nextQuestion().category() != InterviewCategory.WORKFLOWS
                && guard++ < 20) {
            InterviewDtos.QuestionResponse question = session.nextQuestion();
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(question.questionKey(), InterviewAnswerDisposition.ANSWERED,
                            completeAnswer(question.category(), false)));
        }
        assertThat(session.nextQuestion()).isNotNull();
        assertThat(session.nextQuestion().category()).isEqualTo(InterviewCategory.WORKFLOWS);

        InterviewDtos.SessionResponse incomplete = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("workflows", InterviewAnswerDisposition.ANSWERED,
                        "When a request arrives, it enters PENDING and later CONFIRMED as the successful outcome."));

        assertThat(incomplete.readiness().generationReady()).isFalse();
        assertThat(incomplete.nextQuestion().questionKey()).isEqualTo("workflows");
        assertThat(incomplete.nextQuestion().questionText()).contains("Which named role performs the next workflow action");
        assertThat(incomplete.openQuestions()).anyMatch(question ->
                question.questionKey().equals("incomplete-workflows-facet-actors") && question.material());

        InterviewDtos.SessionResponse completedFacet = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("workflows", InterviewAnswerDisposition.ANSWERED,
                        "The operations owner performs the next action; on conflict the system rejects it and routes an alternative retry."));

        InterviewDtos.AnswerResponse workflow = completedFacet.answers().stream()
                .filter(answer -> answer.questionKey().equals("workflows"))
                .findFirst().orElseThrow();
        assertThat(workflow.answerText())
                .contains("enters PENDING", "operations owner", "alternative retry");
        assertThat(completedFacet.brief().content().path("workflows").asText())
                .contains("enters PENDING", "operations owner", "alternative retry");
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .filteredOn(answer -> answer.questionKey().equals("workflows"))
                .hasSize(2);
    }

    @Test
    void serviceBookingReadinessStaysLockedUntilMetricFacetsAreComplete() {
        TestProject fixture = createProject(
                "SkillLink",
                "A local service-booking platform where customers discover, compare, and book trusted professionals with profiles, availability, pricing, ratings, confirmations, status, cancellation, rescheduling, no-show handling, notifications, privacy, retention, and audit history. Payments, subscriptions, analytics, AI, and complex admin tools are excluded.",
                "Local services",
                "customers and trusted professionals",
                ProjectType.WEB_APP);
        InterviewDtos.SessionResponse session = interviewService.start(
                fixture.project().getId(), fixture.owner().getId());
        int guard = 0;
        while (session.nextQuestion() != null
                && session.nextQuestion().category() != InterviewCategory.METRICS
                && guard++ < 25) {
            InterviewDtos.QuestionResponse question = session.nextQuestion();
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(question.questionKey(), InterviewAnswerDisposition.ANSWERED,
                            completeAnswer(question.category(), true)));
        }
        assertThat(session.nextQuestion()).isNotNull();
        assertThat(session.nextQuestion().category()).isEqualTo(InterviewCategory.METRICS);

        InterviewDtos.SessionResponse locked = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("metrics", InterviewAnswerDisposition.ANSWERED,
                        "Track successful booking requests."));

        assertThat(locked.readiness().generationReady()).isFalse();
        assertThat(locked.readiness().snapshot().path("readinessBasis").asText())
                .isEqualTo("material-decision-coverage");
        assertThat(locked.readiness().snapshot().path("coverage").path("METRICS")
                .path("missingFacets").toString()).contains("denominator", "target", "time-window");
        assertThat(locked.nextQuestion().questionKey()).isEqualTo("metrics");
        assertThat(locked.nextQuestion().questionText()).contains("total population");

        InterviewDtos.SessionResponse ready = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("metrics", InterviewAnswerDisposition.ANSWERED,
                        completeAnswer(InterviewCategory.METRICS, true)));

        assertThat(ready.readiness().generationReady()).isTrue();
        assertThat(ready.readiness().requiredCategoryCount()).isEqualTo(13);
    }

    private InterviewDtos.SessionResponse completeInterview(
            TestProject fixture,
            InterviewDtos.SessionResponse session,
            boolean serviceBooking,
            HashSet<String> askedKeys) {
        int answered = 0;
        while (session.nextQuestion() != null && answered < 30) {
            InterviewDtos.QuestionResponse question = session.nextQuestion();
            askedKeys.add(question.questionKey());
            session = interviewService.submitAnswer(
                    fixture.project().getId(), fixture.owner().getId(),
                    new InterviewDtos.AnswerRequest(
                            question.questionKey(), InterviewAnswerDisposition.ANSWERED,
                            completeAnswer(question.category(), serviceBooking)));
            answered++;
        }
        assertThat(answered).as("bounded discovery answer count").isLessThan(30);
        assertThat(session.nextQuestion()).as("all material facets are covered").isNull();
        return session;
    }

    private String completeAnswer(InterviewCategory category, boolean serviceBooking) {
        if (serviceBooking) {
            return switch (category) {
                case PROBLEM -> "Customers experience delays, rework, and loss of trust when confirmed bookings conflict; reduce double-booking errors first.";
                case USERS -> "Customers request bookings and may view their current booking status; the assigned professional has authority to confirm, and an operations owner is responsible for support.";
                case STAKEHOLDERS -> "The product owner has authority to accept scope, while the named risk owner may block an unsafe release.";
                case SCOPE -> "The first release delivers one complete confirmed booking outcome: customers search and filter professionals by service category, location, and availability; compare professional profiles, verified skills, and ratings; reserve an available slot; see the displayed price and booking status.";
                case EXCLUSIONS -> "Payments, subscriptions, advanced analytics, AI matching, and complex administration are explicitly excluded and outside the first-release scope.";
                case WORKFLOWS -> "When a customer requests an available slot, the system creates PENDING and the professional accepts it into CONFIRMED, the successful state. A conflict rejects the request and offers an alternative retry. An authorized reschedule changes the slot, cancellation moves to CANCELLED, and a no-show moves to NO_SHOW. Failed email notification delivery keeps the status visible and retries the message.";
                case ENTITIES -> "The Booking record is authoritative and operations owns it; authorized customers and professionals may view it. It is created at request, updated by status events, retained for 24 months, then deleted. Professionals own Availability in an IANA time zone and UTC timestamps; service duration plus travel buffer defines occupied slots. Profile, verified skill, Rating, private location, and immutable Audit Event records preserve accountable history and sensitive access ends after service.";
                case INTEGRATIONS -> "A transactional email service is the only essential external dependency; if delivery fails or times out, the system records a visible failed status, queues a retry, and lets the operations owner use a manual message without changing booking status.";
                case QUALITY_GOALS -> "A confirmed double-booking conflict is the unacceptable launch failure; the measurable target is zero overlapping confirmed bookings in every weekly window.";
                case CONSTRAINTS -> "The exact fixed platform boundary is a responsive web application; scope or schedule may change before that platform is replaced.";
                case RISKS -> "The failure event is a double-booked overlapping slot, harming customer trust and operations. An atomic availability check and conflict alert detect it; the operations owner rejects the later confirmation and recovers by returning an alternative slot.";
                case BUSINESS_RULES -> "When a customer requests a slot, the system must create one 5-minute hold; after timeout it expires and releases the slot. A conflicting request is rejected with an alternative retry. The same idempotency request key returns one booking. Only the assigned professional or atomic system action may confirm. The displayed price is informational and payment processing remains outside scope.";
                case METRICS -> "The reliability numerator counts confirmed booking conflicts and the denominator is all confirmed bookings; the target is 0% in each weekly window. The request-to-confirmation numerator counts valid booking requests confirmed within 5 minutes, divided by all valid requests; the target is 95% in each monthly window.";
            };
        }
        return switch (category) {
            case PROBLEM -> "Users experience two-day approval delays and avoidable rework; reduce the delayed outcome first.";
            case USERS -> "The requester performs the core action, and the named product owner has final approval authority and is responsible for the result.";
            case STAKEHOLDERS -> "The named product owner may accept the release, while the risk owner has authority to block it.";
            case SCOPE -> "The first release delivers one complete request from submission to an accepted and visible completed outcome.";
            case EXCLUSIONS -> "Advanced analytics and native applications are explicitly excluded and outside the first-release scope.";
            case WORKFLOWS -> "When a user submits a request, the operator moves it from PENDING to CONFIRMED; CONFIRMED is the successful outcome. On failure or conflict, the system rejects it and routes an alternative retry to the owner.";
            case ENTITIES -> "The Request record is authoritative and owned by operations; authorized staff may view it. It is created on submission, updated through status history, retained for 24 months, then deleted with an audit event.";
            case INTEGRATIONS -> "No external service is essential because the first release is self-contained; if a future email service fails or times out, users see a visible error and operations uses a manual fallback.";
            case QUALITY_GOALS -> "An unauthorized-access failure is unacceptable; the measurable target is zero unlogged access events in every weekly window.";
            case CONSTRAINTS -> "The exact fixed platform boundary is a responsive web application; scope may shrink before the platform changes.";
            case RISKS -> "The failure event is an unauthorized record change that harms users and trust. An audit alert detects it; the operations owner contains the record and recovers by restoring the prior version.";
            case BUSINESS_RULES -> "When a request exceeds the limit, the system must require approval within 24 hours; after the deadline expires it cancels and releases the request.";
            case METRICS -> "The numerator counts successful completed requests and the denominator is all valid requests; the target is 95% in each monthly review window.";
        };
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
