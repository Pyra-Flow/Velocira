package com.velocira.backend.documentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import com.velocira.backend.documentation.model.DocumentationExportStyle;
import com.velocira.backend.documentation.service.DocumentationExportService;
import com.velocira.backend.documentation.service.DocumentationPackageService;
import com.velocira.backend.knowledge.model.SrsRequirementEntity;
import com.velocira.backend.knowledge.model.SrsVersionEntity;
import com.velocira.backend.knowledge.model.SrsVersionStatus;
import com.velocira.backend.knowledge.model.StandardsProfileEntity;
import com.velocira.backend.knowledge.repository.SrsRequirementRepository;
import com.velocira.backend.knowledge.repository.SrsVersionRepository;
import com.velocira.backend.knowledge.repository.StandardsProfileRepository;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.repository.ProjectRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in, end-to-end QA harness for a real document-model SRS payload.
 *
 * <p>Run with {@code -Ddocumentation.live.srsJson=<file>
 * -Ddocumentation.live.outputDir=<directory>}. An optional
 * {@code -Ddocumentation.live.briefJson=<file>} supplies the original canonical
 * discovery brief. Diagnostics and canonical source artifacts are written before
 * the export gate is asserted. PDF/DOCX are rendered only when the generated
 * package is valid, approval-eligible, export-ready, and actually APPROVED.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class LiveDocumentationPackageHarnessIntegrationTest {
    @Autowired private DocumentationPackageService packageService;
    @Autowired private DocumentationExportService exportService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private StandardsProfileRepository profileRepository;
    @Autowired private SrsVersionRepository srsVersionRepository;
    @Autowired private SrsRequirementRepository requirementRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void renderLiveCompiledSrsWhenPathsAreProvided() throws Exception {
        String sourceProperty = System.getProperty("documentation.live.srsJson");
        String outputProperty = System.getProperty("documentation.live.outputDir");
        Assumptions.assumeTrue(sourceProperty != null && !sourceProperty.isBlank()
                        && outputProperty != null && !outputProperty.isBlank(),
                "Set documentation.live.srsJson and documentation.live.outputDir to run the live package harness.");

        Path sourcePath = Path.of(sourceProperty).toAbsolutePath().normalize();
        Path outputDirectory = Path.of(outputProperty).toAbsolutePath().normalize();
        assertThat(sourcePath).isRegularFile();
        Files.createDirectories(outputDirectory);

        JsonNode raw = objectMapper.readTree(Files.readString(sourcePath, StandardCharsets.UTF_8));
        ObjectNode srsContent = unwrapSrs(raw);
        ObjectNode brief = loadBrief(raw);
        Files.writeString(outputDirectory.resolve("live-input-srs.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(srsContent), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("live-input-brief.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(brief), StandardCharsets.UTF_8);

        UserEntity owner = userRepository.save(UserEntity.builder().fullName("Live documentation QA")
                .email("live-docs-" + UUID.randomUUID() + "@example.test").password("not-used")
                .role(Role.USER).authProvider(AuthProvider.LOCAL).emailVerified(true).build());
        String projectName = System.getProperty("documentation.live.projectName", "SkillLink");
        ProjectEntity project = projectRepository.save(ProjectEntity.builder().owner(owner).name(projectName)
                .description(srsContent.path("executive_summary").asText("Live compiled SRS documentation QA."))
                .type(ProjectType.WEB_APP).status(ProjectStatus.APPROVED).build());
        StandardsProfileEntity profile = profileRepository.save(StandardsProfileEntity.builder()
                .profileKey("LIVE-" + UUID.randomUUID()).name("Live compiled SRS profile")
                .description("Profile persisted by the opt-in documentation QA harness.")
                .sourceLicense("Input payload metadata").ownerName("Documentation QA")
                .effectiveDate(LocalDate.now()).controls(objectMapper.createArrayNode()).active(true).build());

        JsonNode manifest = srsContent.path("generation_manifest");
        SrsVersionStatus sourceStatus = SrsVersionStatus.valueOf(
                System.getProperty("documentation.live.sourceStatus", "APPROVED").toUpperCase(Locale.ROOT));
        Instant now = Instant.now();
        SrsVersionEntity srs = srsVersionRepository.save(SrsVersionEntity.builder()
                .project(project).owner(owner).profile(profile).versionNumber(1).status(sourceStatus)
                .briefSnapshot(brief).srsContent(srsContent)
                .validationOutcome(objectMapper.createObjectNode().put("valid",
                        "PASSED".equalsIgnoreCase(manifest.path("validation_status").asText())))
                .citationCoverage(new BigDecimal("100.00"))
                .provider(manifest.path("provider").asText("live-fixture"))
                .model(manifest.path("actual_model").asText(manifest.path("requested_model").asText("unknown-live-model")))
                .promptVersion(manifest.path("prompt_version").asText("live-compiled-srs"))
                .generatedAt(now).approvedAt(sourceStatus == SrsVersionStatus.APPROVED ? now : null).build());
        persistCanonicalRequirements(srs, srsContent.path("requirements"));

        var generated = packageService.generate(project.getId(), owner.getId(), srs.getId());
        Files.writeString(outputDirectory.resolve("package-validation.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated.validation()), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("canonical-package-model.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated.canonicalModel()), StandardCharsets.UTF_8);

        Path sources = outputDirectory.resolve("source-artifacts");
        Files.createDirectories(sources);
        generated.artifacts().forEach(artifact -> {
            try {
                String extension = switch (artifact.type()) {
                    case OPENAPI -> "json";
                    case USE_CASES -> "puml";
                    case C4_CONTEXT, WORKFLOWS, ERD -> "mmd";
                    default -> "md";
                };
                Files.writeString(sources.resolve(artifact.type().name().toLowerCase(Locale.ROOT).replace('_', '-') + "." + extension),
                        artifact.sourceContent(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        // Keep the diagnostics above even when the real payload is not eligible
        // for export. This makes a failed live run actionable without bypassing
        // the same material-gap contract enforced by the application service.
        Files.writeString(outputDirectory.resolve("package-status.txt"), generated.status(), StandardCharsets.UTF_8);
        assertThat(generated.validation().path("valid").asBoolean(false))
                .as("package validation (see package-validation.json)").isTrue();
        assertThat(generated.validation().path("approvalEligible").asBoolean(false))
                .as("package approval eligibility (see package-validation.json)").isTrue();
        assertThat(generated.validation().path("exportReady").asBoolean(false))
                .as("package export readiness (see package-validation.json)").isTrue();
        assertThat(generated.status()).as("generated package status").isEqualTo("APPROVED");

        // Exercise the same transactional export path used by the API. Calling the
        // low-level renderer with detached JPA proxies makes this harness unlike
        // production and can fail before any bytes are produced.
        var pdfExport = exportService.create(project.getId(), generated.id(), owner.getId(),
                DocumentationExportFormat.PDF, DocumentationExportStyle.defaults());
        var docxExport = exportService.create(project.getId(), generated.id(), owner.getId(),
                DocumentationExportFormat.DOCX, DocumentationExportStyle.defaults());
        var pdf = exportService.download(project.getId(), generated.id(), pdfExport.id(), owner.getId()).bytes();
        var docx = exportService.download(project.getId(), generated.id(), docxExport.id(), owner.getId()).bytes();
        Path pdfPath = outputDirectory.resolve("skilllink-documentation-package-v" + generated.versionNumber() + ".pdf");
        Path docxPath = outputDirectory.resolve("skilllink-documentation-package-v" + generated.versionNumber() + ".docx");
        Files.write(pdfPath, pdf);
        Files.write(docxPath, docx);

        try (PDDocument pdfDocument = Loader.loadPDF(pdf);
             XWPFDocument docxDocument = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(pdfDocument.getNumberOfPages()).isGreaterThan(2);
            assertThat(pdfDocument.getDocumentInformation().getTitle()).contains(projectName);
            assertThat(pdfDocument.getDocumentCatalog().getDocumentOutline()).isNotNull();
            assertThat(pdfDocument.getDocumentCatalog().getDocumentOutline().getFirstChild()).isNotNull();
            assertThat(docxDocument.getProperties().getCoreProperties().getTitle()).contains(projectName);
            assertThat(docxDocument.getDocument().xmlText()).contains("TOC \\o", "w:w=\"9360\"");
        }
    }

    private ObjectNode unwrapSrs(JsonNode raw) {
        for (String key : List.of("artifact", "srs_content", "srsContent", "srs")) {
            if (raw.path(key).isObject() && raw.path(key).path("requirements").isArray()) return (ObjectNode) raw.path(key).deepCopy();
        }
        if (raw.isObject() && raw.path("requirements").isArray()) return (ObjectNode) raw.deepCopy();
        throw new IllegalArgumentException("Live SRS JSON must contain a compiled object with a requirements array.");
    }

    private ObjectNode loadBrief(JsonNode raw) throws Exception {
        String briefProperty = System.getProperty("documentation.live.briefJson");
        if (briefProperty != null && !briefProperty.isBlank()) {
            JsonNode brief = objectMapper.readTree(Files.readString(Path.of(briefProperty), StandardCharsets.UTF_8));
            if (brief.isObject()) return (ObjectNode) brief.deepCopy();
        }
        for (String key : List.of("brief_snapshot", "briefSnapshot", "brief")) {
            if (raw.path(key).isObject()) return (ObjectNode) raw.path(key).deepCopy();
        }
        ObjectNode derived = objectMapper.createObjectNode();
        ObjectNode srs = unwrapSrs(raw);
        derived.put("scope", srs.path("scope").asText());
        derived.put("users", joinNames(srs.path("stakeholders"), "name", "title"));
        List<String> entities = new ArrayList<>();
        for (JsonNode definition : srs.path("definitions")) {
            String category = definition.path("category").asText().replace('_', ' ').replace('-', ' ').trim();
            if ("Domain entity".equalsIgnoreCase(category) || "Entity".equalsIgnoreCase(category)) {
                String value = firstText(definition, "term", "name", "title");
                if (!value.isBlank()) entities.add(value);
            }
        }
        derived.put("entities", String.join(", ", entities));
        derived.put("integrations", joinNames(srs.path("integrations"), "name", "title", "system"));
        List<String> workflowSteps = new ArrayList<>();
        srs.path("workflows").forEach(workflow -> workflow.path("main_flow").forEach(step -> workflowSteps.add(step.asText())));
        derived.put("workflows", String.join("; ", workflowSteps));
        return derived;
    }

    private void persistCanonicalRequirements(SrsVersionEntity srs, JsonNode requirements) {
        assertThat(requirements.isArray()).as("canonical requirements array").isTrue();
        assertThat(requirements).as("at least one canonical requirement").isNotEmpty();
        for (JsonNode requirement : requirements) {
            String id = firstText(requirement, "id", "requirement_id", "requirementId");
            String statement = firstText(requirement, "statement", "normative_statement", "requirement");
            assertThat(id).as("requirement ID").isNotBlank();
            assertThat(statement).as("statement for %s", id).isNotBlank();
            requirementRepository.save(SrsRequirementEntity.builder().srsVersion(srs).requirementId(id)
                    .requirementType(firstTextOr(requirement, "FUNCTIONAL", "type", "requirement_type").toUpperCase(Locale.ROOT))
                    .priority(firstTextOr(requirement, "MUST", "priority").toUpperCase(Locale.ROOT))
                    .statement(statement).rationale(firstTextOr(requirement, "Canonical SRS requirement.", "rationale"))
                    .acceptanceCriteria(joinAcceptance(requirement.path("acceptance_criteria"), requirement.path("acceptanceCriteria")))
                    .sourceKind(firstTextOr(requirement, "CITATION", "source_kind", "sourceKind", "source_classification").toUpperCase(Locale.ROOT))
                    .sourceDetail(firstTextOr(requirement, "Live compiled SRS payload.", "source_detail", "sourceDetail", "evidence"))
                    .verificationMethod(firstTextOr(requirement, "TEST", "verification_method", "verificationMethod", "verification").toUpperCase(Locale.ROOT))
                    .qualityOutcome(objectMapper.createObjectNode().put("valid", true)).build());
        }
    }

    private String joinAcceptance(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate.isTextual() && !candidate.asText().isBlank()) return candidate.asText();
            if (candidate.isArray() && !candidate.isEmpty()) {
                List<String> values = new ArrayList<>();
                candidate.forEach(item -> values.add(item.isTextual() ? item.asText()
                        : firstTextOr(item, "Acceptance evidence requires review.", "text", "criterion", "description")));
                return String.join("\n", values);
            }
        }
        return "A reviewer confirms the normative statement with retained evidence.";
    }

    private String joinNames(JsonNode values, String... keys) {
        if (values.isTextual()) return values.asText();
        List<String> names = new ArrayList<>();
        if (values.isArray()) values.forEach(value -> {
            String name = value.isTextual() ? value.asText() : firstText(value, keys);
            if (!name.isBlank()) names.add(name);
        });
        return String.join(", ", names);
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) if (node.path(key).isTextual() && !node.path(key).asText().isBlank()) return node.path(key).asText().trim();
        return "";
    }

    private String firstTextOr(JsonNode node, String fallback, String... keys) {
        String value = firstText(node, keys);
        return value.isBlank() ? fallback : value;
    }
}
