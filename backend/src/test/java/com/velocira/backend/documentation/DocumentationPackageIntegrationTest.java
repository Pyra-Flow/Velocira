package com.velocira.backend.documentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.documentation.dto.DocumentationDtos;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the generated-SRS -> linked package -> immediate export path. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentationPackageIntegrationTest {
    @Autowired private DocumentationPackageService packageService;
    @Autowired private DocumentationExportService exportService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private StandardsProfileRepository profileRepository;
    @Autowired private SrsVersionRepository srsVersionRepository;
    @Autowired private SrsRequirementRepository requirementRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void generatedSrsProducesValidatedPackageAndImmediateImmutableZip() throws Exception {
        Fixture fixture = fixture();
        DocumentationDtos.PackageResponse generated = packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        assertThat(generated.validation().path("valid").asBoolean()).isTrue();
        assertThat(generated.artifacts()).extracting(DocumentationDtos.ArtifactResponse::type)
                .containsExactlyInAnyOrder(com.velocira.backend.documentation.model.DocumentationArtifactType.values());
        assertThat(generated.artifacts().stream()
                .filter(artifact -> artifact.type() == com.velocira.backend.documentation.model.DocumentationArtifactType.SRS)
                .findFirst().orElseThrow().sourceContent()).doesNotContain("\\n");
        assertThat(generated.traceLinks()).hasSize(1);
        assertThat(generated.status()).isEqualTo("APPROVED");
        DocumentationDtos.TraceResponse trace = generated.traceLinks().getFirst();
        assertThat(trace.useCaseId()).isEqualTo("UC-001");
        assertThat(trace.apiOperationId()).isEqualTo("op-srs-fr-001");
        assertThat(trace.acceptanceCriterionId()).isEqualTo("AC-srs-fr-001-001");

        DocumentationDtos.ExportResponse exported = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.ZIP);
        DocumentationExportService.Download download = exportService.download(fixture.project().getId(), generated.id(), exported.id(), fixture.owner().getId());

        assertThat(download.bytes()).isNotEmpty();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(download.bytes()))) {
            java.util.Map<String, byte[]> entries = new java.util.HashMap<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
            assertThat(entries.keySet()).contains("openapi.json", "openapi.yaml", "diagrams/use-cases.puml", "diagrams/erd.mmd", "documentation-package.docx", "documentation-package.pdf");
            assertThat(new String(entries.get("README.md"), StandardCharsets.UTF_8)).contains("Package v1", "generated from SRS v1");
            try (PDDocument pdf = Loader.loadPDF(entries.get("documentation-package.pdf"));
                 XWPFDocument docx = new XWPFDocument(new java.io.ByteArrayInputStream(entries.get("documentation-package.docx")))) {
                assertThat(pdf.getNumberOfPages()).isPositive();
                assertThat(docx.getParagraphs()).isNotEmpty();
                assertThat(docx.getDocument().getBody().isSetSectPr()).isTrue();
            }
        }

        DocumentationDtos.ExportResponse yaml = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.OPENAPI_YAML);
        DocumentationExportService.Download yamlDownload = exportService.download(fixture.project().getId(), generated.id(), yaml.id(), fixture.owner().getId());
        DocumentationDtos.ExportResponse json = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.OPENAPI_JSON);
        DocumentationExportService.Download jsonDownload = exportService.download(fixture.project().getId(), generated.id(), json.id(), fixture.owner().getId());
        String yamlText = new String(yamlDownload.bytes(), StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode yamlContract = new YAMLMapper().readTree(yamlText);
        assertThat(yamlContract.path("openapi").asText()).isEqualTo("3.1.1");
        assertThat(yamlContract.path("paths").path("/requirements/srs-fr-001").path("post").path("operationId").asText())
                .isEqualTo("op-srs-fr-001");
        assertThat(yamlContract.path("components").path("schemas").path("Patient").isObject()).isTrue();
        assertThat(yamlContract).isEqualTo(objectMapper.readTree(jsonDownload.bytes()));
    }

    private Fixture fixture() {
        UserEntity owner = userRepository.save(UserEntity.builder().fullName("Package Tester")
                .email("package-" + UUID.randomUUID() + "@example.test").password("not-used")
                .role(Role.USER).authProvider(AuthProvider.LOCAL).emailVerified(true).build());
        ProjectEntity project = projectRepository.save(ProjectEntity.builder().owner(owner).name("ClinicFlow")
                .description("A secure clinic workflow application.").type(ProjectType.WEB_APP).status(ProjectStatus.APPROVED).build());
        StandardsProfileEntity profile = profileRepository.save(StandardsProfileEntity.builder().profileKey("TESTER")
                .name("Test controls").description("Test profile").sourceLicense("Internal").ownerName("Test")
                .effectiveDate(LocalDate.now()).controls(objectMapper.createArrayNode().add("Trace every requirement")).active(true).build());
        ObjectNode brief = objectMapper.createObjectNode();
        brief.put("users", "Receptionists, Doctors, Patients");
        brief.put("entities", "Patient, Appointment, Visit");
        brief.put("business_rules", "An appointment slot cannot overlap for the same doctor.");
        SrsVersionEntity srs = srsVersionRepository.save(SrsVersionEntity.builder().project(project).owner(owner).profile(profile)
                .versionNumber(1).status(SrsVersionStatus.NEEDS_REVIEW).briefSnapshot(brief).srsContent(objectMapper.createObjectNode())
                .validationOutcome(objectMapper.createObjectNode().put("valid", true)).citationCoverage(new BigDecimal("100.00"))
                .provider("deterministic").model("test").promptVersion("test-v1").generatedAt(Instant.now()).approvedAt(Instant.now()).build());
        requirementRepository.save(SrsRequirementEntity.builder().srsVersion(srs).requirementId("SRS-FR-001").requirementType("FUNCTIONAL")
                .priority("MUST").statement("The system shall create an Appointment for a Patient.").rationale("Avoid scheduling conflicts.")
                .acceptanceCriteria("A receptionist can select an available slot.\nThe system rejects an overlapping slot.")
                .sourceKind("CITATION").sourceDetail("Clinic workflow evidence").verificationMethod("TEST")
                .qualityOutcome(objectMapper.createObjectNode().put("valid", true)).build());
        return new Fixture(owner, project, srs);
    }

    private record Fixture(UserEntity owner, ProjectEntity project, SrsVersionEntity srs) { }
}
