package com.velocira.backend.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.generation.client.AiGenerationClient;
import com.velocira.backend.generation.client.AiGenerationException;
import com.velocira.backend.generation.client.AiFailureCode;
import com.velocira.backend.generation.client.AiGenerationRequest;
import com.velocira.backend.generation.client.AiGenerationResponse;
import com.velocira.backend.generation.dto.CreateGenerationJobRequest;
import com.velocira.backend.generation.model.ArtifactValidationStatus;
import com.velocira.backend.generation.model.ArtifactVersionEntity;
import com.velocira.backend.generation.model.GenerationJobEntity;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.model.PromptTemplateEntity;
import com.velocira.backend.generation.repository.ArtifactVersionRepository;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import com.velocira.backend.generation.repository.GenerationRunRepository;
import com.velocira.backend.generation.repository.PromptTemplateRepository;
import com.velocira.backend.generation.service.GenerationHashing;
import com.velocira.backend.generation.service.GenerationJobService;
import com.velocira.backend.interview.model.InterviewSessionEntity;
import com.velocira.backend.interview.model.InterviewSessionStatus;
import com.velocira.backend.interview.model.InterviewAnswerDisposition;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import com.velocira.backend.interview.repository.InterviewAnswerRepository;
import com.velocira.backend.interview.repository.InterviewSessionRepository;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runnable local integration test for the persisted asynchronous path. It uses
 * the same typed boundary as FastAPI while substituting a deterministic client,
 * so it verifies Spring/JPA/worker/idempotency without a provider credential.
 */
@SpringBootTest
@Import(GenerationJobLifecycleIntegrationTest.GenerationTestConfiguration.class)
class GenerationJobLifecycleIntegrationTest {

    private static final String PROMPT = "Create a concise structured test artifact.";

    @Autowired
    private GenerationJobService generationJobService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private GenerationRunRepository generationRunRepository;

    @Autowired
    private ArtifactVersionRepository artifactVersionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewAnswerRepository interviewAnswerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ControlledAiGenerationClient testAiClient;

    @BeforeEach
    void resetTestProviderAndEnsurePrompt() {
        testAiClient.succeed();
        if (promptTemplateRepository.findByTemplateKeyAndTemplateVersion(
                GenerationJobService.PHASE_TWO_PROMPT_KEY, "1.0.0").isEmpty()) {
            promptTemplateRepository.save(PromptTemplateEntity.builder()
                    .templateKey(GenerationJobService.PHASE_TWO_PROMPT_KEY)
                    .templateVersion("1.0.0")
                    .content(PROMPT)
                    .inputSchema(objectMapper.createObjectNode())
                    .outputSchema(objectMapper.createObjectNode())
                    .checksum(GenerationHashing.sha256(PROMPT))
                    .enabled(true)
                    .build());
        }
    }

    @Test
    void acceptedJobPublishesExactlyOnePersistedArtifactAndIdempotentReplayReturnsIt() throws Exception {
        TestProject fixture = createProject("Async artifact test");
        UserEntity owner = fixture.owner();
        ProjectEntity project = fixture.project();

        String idempotencyKey = UUID.randomUUID().toString();
        GenerationJobService.JobAcceptance accepted = generationJobService.requestJob(
                project.getId(), owner.getId(), new CreateGenerationJobRequest(DocumentType.SRS, null), idempotencyKey);
        GenerationJobEntity ready = waitForReady(accepted.job().getId());

        assertThat(accepted.newlyCreated()).isTrue();
        assertThat(ready.getStatus()).isEqualTo(GenerationJobStatus.READY);
        assertThat(ready.getDocument()).isNotNull();
        assertThat(ready.getArtifactVersion()).isNotNull();
        ArtifactVersionEntity artifact = artifactVersionRepository.findByGenerationJobId(ready.getId()).orElseThrow();
        assertThat(artifact.getValidationStatus()).isEqualTo(ArtifactValidationStatus.PASSED);
        assertThat(artifact.getProvider()).isEqualTo("integration-test-provider");
        assertThat(artifact.getModel()).isEqualTo("integration-test-model");
        assertThat(artifact.getSourceInputSnapshot().path("project").path("id").asText())
                .isEqualTo(project.getId().toString());
        assertThat(documentRepository.countByProjectId(project.getId())).isEqualTo(1);

        GenerationJobService.JobAcceptance replay = generationJobService.requestJob(
                project.getId(), owner.getId(), new CreateGenerationJobRequest(DocumentType.SRS, null), idempotencyKey);
        assertThat(replay.newlyCreated()).isFalse();
        assertThat(replay.job().getId()).isEqualTo(ready.getId());
        assertThat(documentRepository.countByProjectId(project.getId())).isEqualTo(1);
    }

    @Test
    void retryableProviderTimeoutsBecomeDurableFailuresAndManualRetryCreatesOneArtifact() throws Exception {
        testAiClient.failWithTimeout();
        TestProject fixture = createProject("Retry behavior test");
        GenerationJobService.JobAcceptance accepted = generationJobService.requestJob(
                fixture.project().getId(),
                fixture.owner().getId(),
                new CreateGenerationJobRequest(DocumentType.SRS, null),
                UUID.randomUUID().toString());

        GenerationJobEntity failed = waitForStatus(accepted.job().getId(), GenerationJobStatus.FAILED, Duration.ofSeconds(8));
        assertThat(failed.isRetryable()).isTrue();
        assertThat(failed.getAttemptCount()).isEqualTo(failed.getMaxAttempts());
        assertThat(documentRepository.countByProjectId(fixture.project().getId())).isZero();

        testAiClient.succeed();
        GenerationJobService.JobAcceptance retry = generationJobService.retryJob(
                fixture.project().getId(), failed.getId(), fixture.owner().getId(), UUID.randomUUID().toString());
        GenerationJobEntity ready = waitForReady(retry.job().getId());
        assertThat(retry.newlyCreated()).isTrue();
        assertThat(ready.getStatus()).isEqualTo(GenerationJobStatus.READY);
        assertThat(documentRepository.countByProjectId(fixture.project().getId())).isEqualTo(1);
    }

    @Test
    void cancellationPreventsABlockedProviderResponseFromBeingPublished() throws Exception {
        testAiClient.blockUntilReleased();
        TestProject fixture = createProject("Cancellation behavior test");
        GenerationJobService.JobAcceptance accepted = generationJobService.requestJob(
                fixture.project().getId(),
                fixture.owner().getId(),
                new CreateGenerationJobRequest(DocumentType.SRS, null),
                UUID.randomUUID().toString());

        assertThat(testAiClient.awaitProviderEntry()).isTrue();
        generationJobService.cancelJob(fixture.project().getId(), accepted.job().getId(), fixture.owner().getId());
        testAiClient.releaseBlockedCall();
        GenerationJobEntity cancelled = waitForStatus(
                accepted.job().getId(), GenerationJobStatus.CANCELLED, Duration.ofSeconds(3));

        assertThat(cancelled.isCancelRequested()).isTrue();
        assertThat(documentRepository.countByProjectId(fixture.project().getId())).isZero();
    }

    @Test
    void archivingProjectDuringProviderWorkCancelsPublicationAndPreservesTheArchiveState() throws Exception {
        testAiClient.blockUntilReleased();
        TestProject fixture = createProject("Archived generation test");
        GenerationJobService.JobAcceptance accepted = generationJobService.requestJob(
                fixture.project().getId(),
                fixture.owner().getId(),
                new CreateGenerationJobRequest(DocumentType.SRS, null),
                UUID.randomUUID().toString());

        assertThat(testAiClient.awaitProviderEntry()).isTrue();
        ProjectEntity archived = projectRepository.findById(fixture.project().getId()).orElseThrow();
        archived.setStatus(ProjectStatus.ARCHIVED);
        projectRepository.save(archived);
        testAiClient.releaseBlockedCall();

        GenerationJobEntity cancelled = waitForStatus(
                accepted.job().getId(), GenerationJobStatus.CANCELLED, Duration.ofSeconds(3));
        assertThat(cancelled.isCancelRequested()).isTrue();
        assertThat(documentRepository.countByProjectId(fixture.project().getId())).isZero();
        assertThat(projectRepository.findById(fixture.project().getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.ARCHIVED);
    }

    private TestProject createProject(String name) {
        UserEntity owner = userRepository.save(UserEntity.builder()
                .fullName("Generation Tester")
                .email("generation-" + UUID.randomUUID() + "@example.test")
                .password("not-used-by-this-test")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
        ProjectEntity project = projectRepository.save(ProjectEntity.builder()
                .owner(owner)
                .name(name)
                .description("A small project that proves the durable generation path.")
                .type(ProjectType.WEB_APP)
                .status(ProjectStatus.DRAFT)
                .build());
        project.setStatus(ProjectStatus.READY_FOR_GENERATION);
        project = projectRepository.save(project);
        List<ConfirmedDiscoveryAnswer> confirmedAnswers = List.of(
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.PROBLEM,
                        "problem",
                        "problem",
                        "The user experiences delayed durable generation, and successful artifact publication time should improve."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.USERS,
                        "users",
                        "users",
                        "The project owner has authority to decide and accept the generated artifact outcome."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.SCOPE,
                        "scope",
                        "scope",
                        "The first release completes one accepted artifact result from a submitted generation request."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.WORKFLOWS,
                        "workflows",
                        "workflows",
                        "When the user submits a generation request, the system enters pending status; the system completes with an accepted result, and after failure it retries or reports an error for recovery."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.QUALITY_GOALS,
                        "quality",
                        "qualityTargets",
                        "Every generation failure remains visible, and zero successful artifacts may be lost or marked accepted incorrectly."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.CONSTRAINTS,
                        "constraints",
                        "constraints",
                        "The platform boundary is fixed to responsive web for the first release; that exact scope cannot move."),
                new ConfirmedDiscoveryAnswer(
                        InterviewCategory.METRICS,
                        "metrics",
                        "metrics",
                        "The numerator is the count of successful accepted artifacts, the denominator is total requests, and the target is 100 percent in each weekly window."));
        var canonicalBrief = objectMapper.createObjectNode();
        confirmedAnswers.forEach(answer -> canonicalBrief.put(answer.briefKey(), answer.answerText()));
        InterviewSessionEntity session = interviewSessionRepository.save(InterviewSessionEntity.builder()
                .project(project)
                .owner(owner)
                .status(InterviewSessionStatus.CONFIRMED)
                .canonicalBrief(canonicalBrief)
                .readinessSnapshot(objectMapper.createObjectNode()
                        .put("minimumComplete", true)
                        .put("generationReady", true)
                        .put("readinessBasis", "material-decision-coverage"))
                .briefVersion(1)
                .confirmedAt(Instant.now())
                .build());
        // The production readiness check intentionally recomputes eligibility
        // from persisted answers instead of trusting this denormalized snapshot.
        // Seed facet-complete evidence using the exact catalog question keys.
        for (ConfirmedDiscoveryAnswer answer : confirmedAnswers) {
            interviewAnswerRepository.save(InterviewAnswerEntity.builder()
                    .session(session)
                    .category(answer.category())
                    .questionKey(answer.questionKey())
                    .questionText("Confirmed " + answer.category().name() + " question")
                    .whyWeAsk("Required evidence for the durable generation lifecycle test.")
                    .disposition(InterviewAnswerDisposition.ANSWERED)
                    .answerText(answer.answerText())
                    .source("USER")
                    .evidence(objectMapper.createObjectNode())
                    .build());
        }
        return new TestProject(owner, project);
    }

    private GenerationJobEntity waitForReady(UUID jobId) throws Exception {
        return waitForStatus(jobId, GenerationJobStatus.READY, Duration.ofSeconds(5));
    }

    private GenerationJobEntity waitForStatus(UUID jobId, GenerationJobStatus expected, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        GenerationJobEntity latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest = generationJobRepository.findById(jobId).orElseThrow();
            if (latest.getStatus() == expected) {
                return latest;
            }
            Thread.sleep(25);
        }
        assertThat(latest).isNotNull();
        assertThat(latest.getStatus()).as("job should reach its expected durable state within the local worker timeout")
                .isEqualTo(expected);
        throw new AssertionError("Unreachable");
    }

    private record TestProject(UserEntity owner, ProjectEntity project) {
    }

    private record ConfirmedDiscoveryAnswer(
            InterviewCategory category,
            String questionKey,
            String briefKey,
            String answerText) {
    }

    @TestConfiguration
    static class GenerationTestConfiguration {

        @Bean
        @Primary
        ControlledAiGenerationClient deterministicAiGenerationClient() {
            return new ControlledAiGenerationClient();
        }
    }

    static class ControlledAiGenerationClient implements AiGenerationClient {

        private enum Behavior { SUCCESS, TIMEOUT, BLOCKED }

        private final AtomicReference<Behavior> behavior = new AtomicReference<>(Behavior.SUCCESS);
        private volatile CountDownLatch providerEntered = new CountDownLatch(0);
        private volatile CountDownLatch unblockProvider = new CountDownLatch(0);

        void succeed() {
            behavior.set(Behavior.SUCCESS);
            providerEntered = new CountDownLatch(0);
            unblockProvider = new CountDownLatch(0);
        }

        void failWithTimeout() {
            behavior.set(Behavior.TIMEOUT);
        }

        void blockUntilReleased() {
            providerEntered = new CountDownLatch(1);
            unblockProvider = new CountDownLatch(1);
            behavior.set(Behavior.BLOCKED);
        }

        boolean awaitProviderEntry() throws InterruptedException {
            return providerEntered.await(3, TimeUnit.SECONDS);
        }

        void releaseBlockedCall() {
            unblockProvider.countDown();
        }

        @Override
        public AiGenerationResponse generate(AiGenerationRequest request) {
            Behavior current = behavior.get();
            if (current == Behavior.TIMEOUT) {
                throw new AiGenerationException(AiFailureCode.TIMEOUT, "Timed out for test", true);
            }
            if (current == Behavior.BLOCKED) {
                providerEntered.countDown();
                try {
                    if (!unblockProvider.await(3, TimeUnit.SECONDS)) {
                        throw new AiGenerationException(AiFailureCode.TIMEOUT, "Timed out for test", true);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AiGenerationException(AiFailureCode.TIMEOUT, "Interrupted for test", true, ex);
                }
            }
            return new AiGenerationResponse(
                    true,
                    "integration-test-provider",
                    "integration-test-model",
                    request.prompt().version(),
                    new AiGenerationResponse.Artifact(
                            "Async artifact test SRS",
                            "## Scope\n\nA durable test artifact.",
                            List.of(new AiGenerationResponse.Section("scope", "Scope", "A durable test artifact."))),
                    new AiGenerationResponse.Validation(true, List.of()),
                    new AiGenerationResponse.Usage(12L, 8L, BigDecimal.ONE),
                    5L,
                    null);
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }
}
