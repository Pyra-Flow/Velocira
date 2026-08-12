package com.velocira.backend.interview;

import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewSessionStatus;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the durable interview, automatic readiness gate, and answer revision path. */
@SpringBootTest
class InterviewServiceIntegrationTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

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
    }

    @Test
    void startingAnExistingInterviewReturnsTheSameSession() {
        TestProject fixture = createProject("Idempotent interview project");

        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        InterviewDtos.SessionResponse repeated = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(repeated.id()).isEqualTo(first.id());
    }

    @Test
    void replacingAnAiDerivedBriefRetiresItsCurrentAnswersBeforeCreatingNewOnes() {
        TestProject fixture = createProject("Replaceable brief project");

        interviewService.bootstrapFromBrief(fixture.project().getId(), fixture.owner().getId(),
                "A dashboard that helps a support team review customer feedback.");
        interviewService.bootstrapFromBrief(fixture.project().getId(), fixture.owner().getId(),
                "A dashboard that helps a support team review feedback trends each week.");

        InterviewDtos.SessionResponse current = interviewService.summary(fixture.project().getId(), fixture.owner().getId());
        assertThat(current.answers()).isNotEmpty();
        assertThat(current.answers()).allSatisfy(answer -> {
            assertThat(answer.current()).isTrue();
            assertThat(answer.answerText()).contains("feedback trends each week");
        });
        assertThat(interviewService.history(fixture.project().getId(), fixture.owner().getId()).answers())
                .hasSize(current.answers().size() * 2);
    }

    @Test
    void domainContextTailorsAndPersistsTheQuestionTheOwnerActuallySees() {
        TestProject fixture = createProject("ClinicFlow");

        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());
        assertThat(first.nextQuestion().questionText()).contains("healthcare product");

        InterviewDtos.SessionResponse answered = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest(first.nextQuestion().questionKey(), InterviewAnswerDisposition.ANSWERED,
                        "Care teams need one safe place to coordinate appointments."));

        assertThat(answered.answers()).first().extracting(InterviewDtos.AnswerResponse::questionText)
                .asString().contains("healthcare product");
    }

    @Test
    void suggestedChoicesAndCustomAnswerAreStoredAsOneAuditableAnswer() {
        TestProject fixture = createProject("Choice-based interview project");
        InterviewDtos.SessionResponse first = interviewService.start(fixture.project().getId(), fixture.owner().getId());

        assertThat(first.nextQuestion().allowsMultiple()).isFalse();
        assertThat(first.nextQuestion().options()).extracting(InterviewDtos.ChoiceOptionResponse::key)
                .contains("manual-work");

        InterviewDtos.SessionResponse answered = interviewService.submitAnswer(
                fixture.project().getId(), fixture.owner().getId(),
                new InterviewDtos.AnswerRequest("problem", InterviewAnswerDisposition.ANSWERED,
                        "The current handoff takes too long.", List.of("manual-work")));

        InterviewDtos.AnswerResponse answer = answered.answers().getFirst();
        assertThat(answer.selectedOptionKeys()).containsExactly("manual-work");
        assertThat(answer.customAnswerText()).isEqualTo("The current handoff takes too long.");
        assertThat(answer.answerText()).isEqualTo("Reduce manual work; The current handoff takes too long.");
    }

    private TestProject createProject(String name) {
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
                .description("A sufficiently detailed project description for adaptive discovery integration testing.")
                .type(ProjectType.WEB_APP)
                .status(ProjectStatus.DRAFT)
                .build());
        return new TestProject(owner, project);
    }

    private record TestProject(UserEntity owner, ProjectEntity project) {
    }
}
