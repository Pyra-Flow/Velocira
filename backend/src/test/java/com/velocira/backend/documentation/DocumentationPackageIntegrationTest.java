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
import com.velocira.backend.documentation.model.DocumentationExportLayout;
import com.velocira.backend.documentation.model.DocumentationExportStyle;
import com.velocira.backend.documentation.model.DocumentationExportTemplate;
import com.velocira.backend.documentation.model.DocumentationExportTheme;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
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
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void generatedPackageIsStructurallyValidButDoesNotAutoApproveOrEmitUnsupportedArtifacts() {
        Fixture fixture = fixture();
        DocumentationDtos.PackageResponse generated = packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        assertThat(generated.validation().path("valid").asBoolean()).isTrue();
        assertThat(generated.validation().path("approvalEligible").asBoolean()).isFalse();
        assertThat(generated.validation().path("materialGaps")).isNotEmpty();
        assertThat(generated.validation().path("artifactsChecked").asInt()).isLessThan(DocumentationArtifactType.values().length);
        assertThat(generated.validation().path("requirementTraceabilityCoverage").asDouble()).isEqualTo(100.0);
        assertThat(generated.artifacts()).extracting(DocumentationDtos.ArtifactResponse::type)
                .contains(DocumentationArtifactType.SRS, DocumentationArtifactType.BRD, DocumentationArtifactType.ARCHITECTURE,
                        DocumentationArtifactType.USE_CASES, DocumentationArtifactType.C4_CONTEXT, DocumentationArtifactType.DATA_DICTIONARY,
                        DocumentationArtifactType.SECURITY, DocumentationArtifactType.TEST_PLAN, DocumentationArtifactType.USER_MANUAL,
                        DocumentationArtifactType.RISK_REGISTER, DocumentationArtifactType.TRACEABILITY)
                .doesNotContain(DocumentationArtifactType.OPENAPI, DocumentationArtifactType.ERD,
                        DocumentationArtifactType.WORKFLOWS, DocumentationArtifactType.DEPLOYMENT, DocumentationArtifactType.OPERATIONS);
        String srsSource = generated.artifacts().stream()
                .filter(artifact -> artifact.type() == com.velocira.backend.documentation.model.DocumentationArtifactType.SRS)
                .findFirst().orElseThrow().sourceContent();
        assertThat(srsSource).doesNotContain("\\n", "| Generation mode | EXHAUSTIVE |")
                .contains("| Generation mode | STANDARD |");
        assertThat(generated.traceLinks()).hasSize(1);
        assertThat(generated.status()).isEqualTo("NEEDS_REVIEW");
        DocumentationDtos.TraceResponse trace = generated.traceLinks().getFirst();
        assertThat(trace.useCaseId()).isEqualTo("UC-001");
        assertThat(trace.apiOperationId()).isNull();
        assertThat(trace.acceptanceCriterionId()).isEqualTo("AC-srs-fr-001-001");

        assertThat(generated.canonicalModel().path("documentPlan").toString())
                .contains("\"artifactType\":\"OPENAPI\",\"status\":\"OMITTED\"")
                .contains("zero-endpoint contract would be misleading");
        assertThatThrownBy(() -> exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.ZIP))
                .hasMessageContaining("Approve the documentation package");
        assertThatThrownBy(() -> packageService.approve(fixture.project().getId(), generated.id(), fixture.owner().getId()))
                .hasMessageContaining("not approval-eligible");
    }

    @Test
    void packageDocumentsReflectTheSourceSrsApprovalState() {
        Fixture fixture = fixture();
        fixture.srs().setStatus(SrsVersionStatus.APPROVED);
        srsVersionRepository.save(fixture.srs());

        DocumentationDtos.PackageResponse generated = packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        String srsSource = generated.artifacts().stream()
                .filter(artifact -> artifact.type() == DocumentationArtifactType.SRS)
                .findFirst().orElseThrow().sourceContent();
        String architectureSource = generated.artifacts().stream()
                .filter(artifact -> artifact.type() == DocumentationArtifactType.ARCHITECTURE)
                .findFirst().orElseThrow().sourceContent();
        assertThat(srsSource).contains("| Status | Approved |", "Status: Approved");
        assertThat(architectureSource).contains("Status: Approved");
        assertThat(srsSource).doesNotContain("Needs stakeholder review");
    }

    @Test
    void styledExportsPersistChoicesAndKeepEachTemplateReadable() throws Exception {
        Fixture fixture = fixture(true);
        fixture.project().setName("ClinicFlow — Review");
        projectRepository.save(fixture.project());
        DocumentationDtos.PackageResponse generated = packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());
        assertThat(generated.validation().path("totalWords").asInt()).isGreaterThanOrEqualTo(7_000);
        assertThat(generated.validation().path("declaredPackageWordTarget").asInt()).isEqualTo(7_000);
        assertThat(generated.validation().path("generationMode").asText()).isEqualTo("EXHAUSTIVE");
        assertThat(generated.validation().path("approvalEligible").asBoolean()).isTrue();
        assertThat(generated.status()).isEqualTo("APPROVED");
        var canonicalApiRequirement = java.util.stream.StreamSupport.stream(generated.canonicalModel().path("requirements").spliterator(), false)
                .filter(requirement -> "SRS-API-001".equals(requirement.path("id").asText())).findFirst().orElseThrow();
        assertThat(canonicalApiRequirement.path("trigger").asText()).isNotBlank();
        assertThat(canonicalApiRequirement.path("actors")).isNotEmpty();
        assertThat(canonicalApiRequirement.path("api_path").asText()).isEqualTo("/reminder-outcomes");
        assertThat(canonicalApiRequirement.path("apiOperationId").asText()).isEqualTo("recordReminderOutcome");
        assertThat(generated.validation().path("narrativeSectionsChecked").asInt()).isEqualTo(12);
        assertThat(generated.validation().path("narrativeWords").asInt()).isGreaterThanOrEqualTo(2_000);
        writePackageSourcesWhenRequested(generated);
        for (DocumentationArtifactType type : List.of(
                DocumentationArtifactType.ERD, DocumentationArtifactType.C4_CONTEXT,
                DocumentationArtifactType.WORKFLOWS, DocumentationArtifactType.USE_CASES)) {
            String svg = new String(exportService.preview(fixture.project().getId(), generated.id(), fixture.owner().getId(), type).bytes(), StandardCharsets.UTF_8);
            assertThat(svg).contains("fill=\"#").doesNotContainPattern("(?:fill|stroke)=\"[0-9A-Fa-f]{6}\"");
            assertThat(svg.toLowerCase()).doesNotContain(">null<");
        }
        DocumentationExportStyle[] styles = {
                new DocumentationExportStyle(DocumentationExportTemplate.EXECUTIVE, DocumentationExportTheme.SIGNAL, DocumentationExportLayout.STANDARD),
                new DocumentationExportStyle(DocumentationExportTemplate.TECHNICAL, DocumentationExportTheme.OCEAN, DocumentationExportLayout.COMPACT),
                new DocumentationExportStyle(DocumentationExportTemplate.MINIMAL, DocumentationExportTheme.MONOCHROME, DocumentationExportLayout.PRESENTATION)
        };

        for (DocumentationExportStyle style : styles) {
            DocumentationDtos.ExportResponse pdf = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.PDF, style);
            DocumentationDtos.ExportResponse docx = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.DOCX, style);

            assertThat(pdf.template()).isEqualTo(style.template().name());
            assertThat(pdf.theme()).isEqualTo(style.theme().name());
            assertThat(pdf.layout()).isEqualTo(style.layout().name());
            assertThat(docx.template()).isEqualTo(style.template().name());

            byte[] pdfBytes = exportService.download(fixture.project().getId(), generated.id(), pdf.id(), fixture.owner().getId()).bytes();
            byte[] docxBytes = exportService.download(fixture.project().getId(), generated.id(), docx.id(), fixture.owner().getId()).bytes();
            writePreviewWhenRequested(style, pdfBytes, docxBytes);
            try (PDDocument pdfDocument = Loader.loadPDF(pdfBytes);
                 XWPFDocument docxDocument = new XWPFDocument(new java.io.ByteArrayInputStream(docxBytes))) {
                assertThat(pdfDocument.getNumberOfPages()).isPositive();
                assertThat(pdfDocument.getDocumentInformation().getTitle()).isEqualTo("ClinicFlow — Review documentation package v1");
                assertThat(pdfDocument.getDocumentCatalog().getDocumentOutline()).isNotNull();
                assertThat(pdfDocument.getDocumentCatalog().getDocumentOutline().getFirstChild()).isNotNull();
                assertThat(docxDocument.getParagraphs()).isNotEmpty();
                String pdfText = new PDFTextStripper().getText(pdfDocument);
                String docxText = docxDocument.getParagraphs().stream().map(paragraph -> paragraph.getText())
                        .collect(java.util.stream.Collectors.joining("\n")) + "\n" +
                        docxDocument.getTables().stream().flatMap(table -> table.getRows().stream())
                                .flatMap(row -> row.getTableCells().stream()).map(cell -> cell.getText())
                                .collect(java.util.stream.Collectors.joining("\n"));
                assertThat(pdfText).contains("Document control", "Field", "Value").doesNotContain("**", "| Field |", "|---");
                assertThat(pdfText).contains("ClinicFlow - Review").doesNotContain("ClinicFlow ? Review");
                assertThat(pdfText).contains("Contents", "Page 1", "Page 2");
                assertThat(docxText).contains("Document control", "Field", "Value").doesNotContain("**", "| Field |", "|---");
                assertThat(docxDocument.getProperties().getCoreProperties().getTitle()).isEqualTo("ClinicFlow — Review documentation package v1");
                String documentXml = docxDocument.getDocument().xmlText();
                assertThat(documentXml).contains("TOC \\o", "outlineLvl", "w:w=\"9360\"", "w:tblInd", "w:w=\"120\"");
                java.util.regex.Matcher diagramExtents = java.util.regex.Pattern.compile("<wp:extent[^>]*cx=\"(\\d+)\"").matcher(documentXml);
                java.util.List<Long> diagramWidths = new java.util.ArrayList<>();
                while (diagramExtents.find()) diagramWidths.add(Long.parseLong(diagramExtents.group(1)));
                assertThat(diagramWidths).hasSize(4).allMatch(width -> width >= 5_000_000L);
                var margins = docxDocument.getDocument().getBody().getSectPr().getPgMar();
                assertThat(margins.getTop()).isEqualTo(BigInteger.valueOf(1_440));
                assertThat(margins.getRight()).isEqualTo(BigInteger.valueOf(1_440));
                assertThat(margins.getBottom()).isEqualTo(BigInteger.valueOf(1_440));
                assertThat(margins.getLeft()).isEqualTo(BigInteger.valueOf(1_440));
                assertThat(margins.getHeader()).isEqualTo(BigInteger.valueOf(708));
                assertThat(margins.getFooter()).isEqualTo(BigInteger.valueOf(708));
                assertThat(docxDocument.getParagraphs().stream().filter(paragraph -> "System requirements".equals(paragraph.getText())).count()).isEqualTo(1);
                int expectedBodySize = switch (style.layout()) {
                    case COMPACT -> 10;
                    case STANDARD -> 11;
                    case PRESENTATION -> 12;
                };
                assertThat(docxDocument.getParagraphs().stream().filter(paragraph -> paragraph.getText().contains("This SRS is"))
                        .flatMap(paragraph -> paragraph.getRuns().stream()).mapToInt(run -> run.getFontSize()).filter(size -> size == expectedBodySize).count()).isPositive();
            }
        }

        DocumentationDtos.ExportResponse json = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.OPENAPI_JSON);
        DocumentationDtos.ExportResponse yaml = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.OPENAPI_YAML);
        var jsonContract = objectMapper.readTree(exportService.download(fixture.project().getId(), generated.id(), json.id(), fixture.owner().getId()).bytes());
        var yamlContract = new YAMLMapper().readTree(exportService.download(fixture.project().getId(), generated.id(), yaml.id(), fixture.owner().getId()).bytes());
        assertThat(jsonContract.path("paths").size()).isEqualTo(1);
        assertThat(jsonContract.path("paths").path("/reminder-outcomes").path("post").path("operationId").asText()).isEqualTo("recordReminderOutcome");
        assertThat(jsonContract.path("paths").path("/reminder-outcomes").path("post").path("x-velocira-requirement-id").asText()).isEqualTo("SRS-API-001");
        assertThat(yamlContract).isEqualTo(jsonContract);

        DocumentationExportStyle archiveStyle = new DocumentationExportStyle(DocumentationExportTemplate.TECHNICAL, DocumentationExportTheme.VIOLET, DocumentationExportLayout.COMPACT);
        DocumentationDtos.ExportResponse archive = exportService.create(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationExportFormat.ZIP, archiveStyle);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(exportService.download(fixture.project().getId(), generated.id(), archive.id(), fixture.owner().getId()).bytes()))) {
            java.util.Map<String, byte[]> entries = new java.util.HashMap<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
            assertThat(entries.keySet()).contains("documentation-package.docx", "documentation-package.pdf", "diagrams/erd.svg", "diagrams/use-cases.svg", "diagrams/c4-context.svg", "diagrams/workflow.svg");
            assertThat(new String(entries.get("README.md"), StandardCharsets.UTF_8)).contains("Template: TECHNICAL", "Theme: VIOLET", "Layout: COMPACT");
        }
    }

    private void writePreviewWhenRequested(DocumentationExportStyle style, byte[] pdfBytes, byte[] docxBytes) throws Exception {
        String previewDirectory = System.getProperty("documentation.export.previewDir");
        if (previewDirectory == null || previewDirectory.isBlank()) return;
        Path directory = Path.of(previewDirectory);
        Files.createDirectories(directory);
        String prefix = style.template().name().toLowerCase();
        Files.write(directory.resolve(prefix + ".pdf"), pdfBytes);
        Files.write(directory.resolve(prefix + ".docx"), docxBytes);
        Path renderedPages = directory.resolve("rendered-" + prefix + "-pdf-pages");
        if (Files.exists(renderedPages)) {
            try (java.util.stream.Stream<Path> existingPages = Files.list(renderedPages)) {
                for (Path existingPage : existingPages.toList()) Files.delete(existingPage);
            }
        }
        Files.createDirectories(renderedPages);
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage page = renderer.renderImageWithDPI(pageIndex, 144, ImageType.RGB);
                ImageIO.write(page, "png", renderedPages.resolve(String.format("page-%03d.png", pageIndex + 1)).toFile());
            }
        }
    }

    @Test
    void nullBriefValuesNeverBecomeCanonicalNodesOrDiagramLabels() {
        Fixture fixture = fixture();
        ObjectNode brief = (ObjectNode) fixture.srs().getBriefSnapshot().deepCopy();
        brief.putNull("integrations");
        fixture.srs().setBriefSnapshot(brief);
        srsVersionRepository.save(fixture.srs());

        DocumentationDtos.PackageResponse generated = packageService.generate(
                fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        assertThat(generated.canonicalModel().path("integrations").isEmpty()).isTrue();
        String svg = new String(exportService.preview(fixture.project().getId(), generated.id(), fixture.owner().getId(), DocumentationArtifactType.C4_CONTEXT).bytes(), StandardCharsets.UTF_8);
        assertThat(svg.toLowerCase()).doesNotContain(">null<");
    }

    @Test
    void policyPhrasesNeverBecomeEntitiesAndUnsupportedErdIsOmitted() {
        Fixture fixture = fixture();
        ObjectNode brief = (ObjectNode) fixture.srs().getBriefSnapshot().deepCopy();
        brief.put("entities", "Professional Profile, Access only while needed, Availability Slot");
        fixture.srs().setBriefSnapshot(brief);
        srsVersionRepository.save(fixture.srs());

        DocumentationDtos.PackageResponse generated = packageService.generate(
                fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        assertThat(generated.canonicalModel().path("entities").findValuesAsText("name"))
                .containsExactly("Professional Profile", "Availability Slot")
                .doesNotContain("Access only while needed");
        assertThat(generated.artifacts()).extracting(DocumentationDtos.ArtifactResponse::type)
                .contains(DocumentationArtifactType.DATA_DICTIONARY).doesNotContain(DocumentationArtifactType.ERD);
        assertThat(generated.canonicalModel().path("documentPlan").toString())
                .contains("Entity names alone do not justify relationship cardinality");
    }

    @Test
    void standardsWhoseOnlyApplicabilitySignalIsExplicitlyExcludedAreRemoved() {
        Fixture fixture = fixture();
        ObjectNode content = objectMapper.createObjectNode();
        content.putArray("exclusions").add("Payments are excluded.").add("AI recommendations are excluded.");
        var standards = content.putArray("standards_applied");
        standards.addObject().put("id", "STD-PCI").put("title", "PCI DSS").put("description", "Applicable context signal: payment. Payment controls.");
        standards.addObject().put("id", "STD-AI").put("title", "AI guidance").put("description", "Applicable context signal: AI. AI governance.");
        standards.addObject().put("id", "STD-29148").put("title", "ISO/IEC/IEEE 29148").put("description", "Requirements quality and traceability guidance.");
        fixture.srs().setSrsContent(content);
        srsVersionRepository.save(fixture.srs());

        DocumentationDtos.PackageResponse generated = packageService.generate(
                fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId());

        assertThat(generated.canonicalModel().path("standardsApplied").findValuesAsText("title"))
                .containsExactly("ISO/IEC/IEEE 29148");
        String srsSource = generated.artifacts().stream().filter(artifact -> artifact.type() == DocumentationArtifactType.SRS)
                .findFirst().orElseThrow().sourceContent();
        assertThat(srsSource).contains("ISO/IEC/IEEE 29148").doesNotContain("PCI DSS", "AI guidance");
    }

    @Test
    void exhaustivePackageRejectsACompactArtifactSetInsteadOfReportingItAsValid() {
        Fixture fixture = fixture();
        ObjectNode compactContent = objectMapper.createObjectNode();
        compactContent.putObject("generation_manifest").put("mode", "EXHAUSTIVE");
        fixture.srs().setSrsContent(compactContent);
        srsVersionRepository.save(fixture.srs());

        assertThatThrownBy(() -> packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId()))
                .isInstanceOf(com.velocira.backend.documentation.exceptions.DocumentationPackageException.class)
                .hasMessageContaining("does not declare expected_package_word_target");
    }

    @Test
    void exhaustivePackageHonorsItsEvidenceBackedLongFormWordTarget() {
        Fixture fixture = fixture(true);
        ObjectNode content = fixture.srs().getSrsContent().deepCopy();
        content.with("generation_manifest").with("long_form_provenance")
                .put("expected_package_word_target", 25_000);
        fixture.srs().setSrsContent(content);
        srsVersionRepository.save(fixture.srs());

        assertThatThrownBy(() -> packageService.generate(fixture.project().getId(), fixture.owner().getId(), fixture.srs().getId()))
                .isInstanceOf(com.velocira.backend.documentation.exceptions.DocumentationPackageException.class)
                .hasMessageContaining("at least 25000 useful words");
    }

    private void writePackageSourcesWhenRequested(DocumentationDtos.PackageResponse generated) throws Exception {
        String previewDirectory = System.getProperty("documentation.export.previewDir");
        if (previewDirectory == null || previewDirectory.isBlank()) return;
        Path directory = Path.of(previewDirectory);
        Files.createDirectories(directory);
        DocumentationDtos.ArtifactResponse srs = generated.artifacts().stream()
                .filter(artifact -> artifact.type() == com.velocira.backend.documentation.model.DocumentationArtifactType.SRS)
                .findFirst().orElseThrow();
        Files.writeString(directory.resolve("representative-srs.md"), srs.sourceContent(), StandardCharsets.UTF_8);
        Path artifactsDirectory = directory.resolve("source-artifacts");
        Files.createDirectories(artifactsDirectory);
        for (DocumentationDtos.ArtifactResponse artifact : generated.artifacts()) {
            String extension = switch (artifact.type()) {
                case OPENAPI -> "json";
                case USE_CASES -> "puml";
                case WORKFLOWS -> "mmd";
                default -> "md";
            };
            String filename = artifact.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "." + extension;
            Files.writeString(artifactsDirectory.resolve(filename), artifact.sourceContent(), StandardCharsets.UTF_8);
        }
        Files.writeString(directory.resolve("validation-metrics.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated.validation()), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("canonical-model.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated.canonicalModel()), StandardCharsets.UTF_8);
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("total_words", generated.validation().path("totalWords").asInt());
        metrics.put("narrative_words", generated.validation().path("narrativeWords").asInt());
        metrics.put("narrative_sections", generated.validation().path("narrativeSectionsChecked").asInt());
        metrics.put("requirements", generated.validation().path("requirementsChecked").asInt());
        metrics.put("traceability_coverage", generated.validation().path("requirementTraceabilityCoverage").asDouble());
        int requirementsWithAcceptance = 0;
        for (com.fasterxml.jackson.databind.JsonNode requirement : generated.canonicalModel().path("requirements")) {
            if (requirement.path("acceptanceCriteria").isArray() && !requirement.path("acceptanceCriteria").isEmpty()) {
                requirementsWithAcceptance++;
            }
        }
        int requirementsChecked = generated.validation().path("requirementsChecked").asInt();
        metrics.put("acceptance_coverage", requirementsChecked == 0 ? 100.0
                : Math.round(requirementsWithAcceptance * 10_000.0 / requirementsChecked) / 100.0);
        int decisionCount = generated.canonicalModel().path("decisions").isArray() ? generated.canonicalModel().path("decisions").size() : 0;
        int reviewableDecisions = 0;
        for (com.fasterxml.jackson.databind.JsonNode decision : generated.canonicalModel().path("decisions")) {
            if (!decision.path("id").asText().isBlank() && !decision.path("title").asText().isBlank()
                    && !decision.path("owner").asText().isBlank() && !decision.path("description").asText().isBlank()
                    && !decision.path("status").asText().isBlank()) reviewableDecisions++;
        }
        metrics.put("decision_count", decisionCount);
        metrics.put("decision_coverage", decisionCount == 0 ? 100.0
                : Math.round(reviewableDecisions * 10_000.0 / decisionCount) / 100.0);
        ObjectNode artifactWords = metrics.putObject("artifact_words");
        generated.artifacts().forEach(artifact -> artifactWords.put(artifact.type().name(), wordCount(artifact.sourceContent())));
        java.util.Set<String> normalizedSections = new java.util.HashSet<>();
        int duplicateSections = 0;
        for (com.fasterxml.jackson.databind.JsonNode section : generated.canonicalModel().path("srs").path("narrative_sections")) {
            String normalized = section.path("content").asText().replaceAll("\\W+", " ").trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalized.isBlank() && !normalizedSections.add(normalized)) duplicateSections++;
        }
        metrics.put("duplicate_narrative_sections", duplicateSections);
        Files.writeString(directory.resolve("quality-metrics.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics), StandardCharsets.UTF_8);
    }

    private int wordCount(String value) {
        return value == null || value.isBlank() ? 0 : value.trim().split("\\s+").length;
    }

    private Fixture fixture() {
        return fixture(false);
    }

    private Fixture fixture(boolean richDocumentationSample) {
        UserEntity owner = userRepository.save(UserEntity.builder().fullName("Package Tester")
                .email("package-" + UUID.randomUUID() + "@example.test").password("not-used")
                .role(Role.USER).authProvider(AuthProvider.LOCAL).emailVerified(true).build());
        ProjectEntity project = projectRepository.save(ProjectEntity.builder().owner(owner).name("ClinicFlow")
                .description("A secure clinic workflow application.").type(ProjectType.WEB_APP).status(ProjectStatus.APPROVED).build());
        StandardsProfileEntity profile = profileRepository.save(StandardsProfileEntity.builder().profileKey("TESTER-" + UUID.randomUUID())
                .name("Test controls").description("Test profile").sourceLicense("Internal").ownerName("Test")
                .effectiveDate(LocalDate.now()).controls(objectMapper.createArrayNode().add("Trace every requirement")).active(true).build());
        ObjectNode brief = objectMapper.createObjectNode();
        brief.put("users", "Receptionists, Doctors, Patients");
        brief.put("entities", "Patient, Appointment, Visit");
        brief.put("business_rules", "An appointment slot cannot overlap for the same doctor.");
        if (richDocumentationSample) {
            brief.put("problem", "Clinic teams coordinate appointments, visit records, and follow-up across disconnected tools.");
            brief.put("scope", "The first release supports patient registration, appointment scheduling, visit documentation, reminders, and operational review for one clinic group.");
            brief.put("workflows", "Reception registers a patient; reception books an available slot; the clinician records the visit; the patient receives confirmed follow-up instructions.");
            brief.put("qualityTargets", "Authorized clinic workflows must remain auditable, accessible, recoverable, and measurable; numeric thresholds require approval.");
            brief.put("constraints", "The first release is a browser-based service; hosting topology and recovery targets remain owner decisions.");
            brief.put("risks", "Unauthorized record access; overlapping appointments; notification failure; incomplete recovery evidence.");
            brief.put("integrations", "SMS notification provider");
        }
        ObjectNode srsContent = richDocumentationSample ? richSrsContent() : objectMapper.createObjectNode();
        SrsVersionEntity srs = srsVersionRepository.save(SrsVersionEntity.builder().project(project).owner(owner).profile(profile)
                .versionNumber(1).status(richDocumentationSample ? SrsVersionStatus.APPROVED : SrsVersionStatus.NEEDS_REVIEW).briefSnapshot(brief).srsContent(srsContent)
                .validationOutcome(objectMapper.createObjectNode().put("valid", true)).citationCoverage(new BigDecimal("100.00"))
                .provider("deterministic").model("test").promptVersion("test-v1").generatedAt(Instant.now()).approvedAt(Instant.now()).build());
        requirementRepository.save(SrsRequirementEntity.builder().srsVersion(srs).requirementId("SRS-FR-001").requirementType("FUNCTIONAL")
                .priority("MUST").statement("The system shall create an Appointment for a Patient.").rationale("Avoid scheduling conflicts.")
                .acceptanceCriteria("A receptionist can select an available slot.\nThe system rejects an overlapping slot.")
                .sourceKind("CITATION").sourceDetail("Clinic workflow evidence").verificationMethod("TEST")
                .qualityOutcome(objectMapper.createObjectNode().put("valid", true)).build());
        if (richDocumentationSample) {
            saveRequirement(srs, "SRS-FR-002", "FUNCTIONAL", "The system shall prevent confirmation of an appointment that overlaps another confirmed appointment for the same clinician.", "Avoid conflicting clinical commitments.", "An overlapping appointment is rejected without changing either booking.\nThe rejection identifies the conflicting time without exposing unrelated patient data.", "TEST");
            saveRequirement(srs, "SRS-DATA-001", "DATA", "The system shall associate each Appointment and Visit with the applicable Patient record.", "Clinic staff need reliable longitudinal context.", "A stored appointment references one patient.\nDeleting or merging a patient requires an explicit reviewed policy.", "INSPECTION");
            saveRequirement(srs, "SRS-API-001", "API", "The system shall exchange confirmed reminder delivery outcomes with the SMS notification provider.", "Failed reminders require operational follow-up.", "A successful provider outcome is recorded.\nA provider failure remains visible for authorized follow-up.", "TEST");
            saveRequirement(srs, "SRS-SEC-001", "SECURITY", "The system shall deny access to patient and visit records when the requesting user lacks the applicable role permission.", "Clinical information requires enforceable authorization boundaries.", "A permitted role can access its authorized record.\nA forbidden role receives no record content and no data is changed.", "TEST");
            saveRequirement(srs, "SRS-PRIV-001", "PRIVACY", "The system shall record the purpose and authorized actor for each access to a Patient record.", "Privacy review requires accountable access evidence.", "Each patient-record access has an actor, time, purpose, and outcome.\nAuthorized reviewers can inspect the access history.", "INSPECTION");
            saveRequirement(srs, "SRS-ACC-001", "ACCESSIBILITY", "The system shall expose appointment and validation status through text that does not rely on color alone.", "Clinic workflows must remain understandable to users with varied visual perception.", "Success, warning, and failure states have visible text labels.\nAn accessibility inspection verifies equivalent meaning without color.", "INSPECTION");
            saveRequirement(srs, "SRS-NFR-001", "NON_FUNCTIONAL", "The system shall measure the completion and failure outcome of the confirmed booking workflow.", "Release decisions require observable workflow evidence.", "Monitoring distinguishes successful, rejected, and failed bookings.\nThe reporting interval and target remain an explicit owner decision.", "ANALYSIS");
            saveRequirement(srs, "SRS-OPS-001", "OPERATIONS", "The system shall expose the health of the application and its confirmed SMS dependency to authorized operators.", "Operators need to distinguish local failure from dependency failure.", "The service health view reports application and SMS dependency state separately.\nA dependency failure produces an operator-visible event.", "DEMONSTRATION");
            saveRequirement(srs, "SRS-TEST-001", "TEST", "The release process shall retain verification evidence for every MUST requirement.", "The package is not implementation-ready without reviewable proof.", "Each MUST requirement links to a test or inspection result.\nA missing result blocks a final pass claim.", "INSPECTION");
            saveRequirement(srs, "SRS-BR-001", "BUSINESS", "The system shall provide clinic staff one reviewable source for appointment and visit workflow status.", "The product exists to reduce fragmented coordination.", "Authorized staff can determine the current appointment and visit status from the product.\nThe source evidence and unresolved boundaries remain visible.", "DEMONSTRATION");
            saveRequirement(srs, "SRS-UX-001", "UX", "The system shall present the next permitted action and recovery guidance when a booking cannot be completed.", "Non-technical clinic staff need actionable failure feedback.", "A rejected booking explains the safe next action.\nThe message does not claim that an unconfirmed booking succeeded.", "DEMONSTRATION");
        }
        return new Fixture(owner, project, srs);
    }

    private void saveRequirement(SrsVersionEntity srs, String id, String type, String statement, String rationale,
                                 String acceptance, String verification) {
        requirementRepository.save(SrsRequirementEntity.builder().srsVersion(srs).requirementId(id).requirementType(type)
                .priority("MUST").statement(statement).rationale(rationale).acceptanceCriteria(acceptance)
                .sourceKind("CITATION").sourceDetail("Owner-confirmed discovery brief and approved review decisions.")
                .verificationMethod(verification).qualityOutcome(objectMapper.createObjectNode().put("valid", true)).build());
    }

    private ObjectNode richSrsContent() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schema_version", "2.0");
        content.put("executive_summary", "ClinicFlow provides a governed first-release workflow for patient registration, appointment coordination, visit documentation, reminders, and operational review. Confirmed facts remain separate from recommendations and unresolved implementation, retention, topology, and service-level decisions.");
        content.put("scope", "The first release covers one clinic group's confirmed registration, scheduling, visit, reminder, and review workflows. Billing, insurance claims, prescribing automation, and unconfirmed external systems are excluded.");
        content.putArray("objectives").add("Replace fragmented appointment and visit status with one reviewable workflow source.").add("Prevent overlapping clinician appointments.").add("Preserve accountable access and verification evidence.");
        content.putArray("exclusions").add("Billing and insurance claims.").add("Automated clinical diagnosis or treatment recommendations.");
        content.putArray("stakeholders").add("Patients").add("Receptionists").add("Clinicians").add("Clinic administrators");
        var manifest = content.putObject("generation_manifest");
        manifest.put("mode", "EXHAUSTIVE").put("requested_model", "gemini-3.1-pro-preview").put("actual_model", "gemini-3.1-pro-preview").put("prompt_version", "srs-compiler-v2").put("validation_status", "PASSED");
        manifest.putObject("long_form_provenance").put("expected_package_word_target", 7_000);
        var sections = content.putArray("narrative_sections");
        String[][] chapterData = {
                {"INTRODUCTION", "Introduction and document purpose", "Defines the governed product baseline and review conventions."},
                {"BUSINESS_CONTEXT", "Business context and success", "Connects fragmented clinic coordination to observable workflow outcomes."},
                {"SCOPE", "Scope, boundaries, and exclusions", "Prevents billing, diagnosis automation, and unconfirmed integrations from entering release scope."},
                {"STAKEHOLDERS", "Stakeholders, users, and authority", "Separates patient, reception, clinician, administrative, and review responsibilities."},
                {"WORKFLOWS", "Operational workflows and recovery", "Covers booking, clinical documentation, reminders, exceptions, and safe recovery."},
                {"BUSINESS_RULES", "Business rules and state transitions", "Makes overlap prevention and reviewed status changes testable."},
                {"DATA", "Information model and lifecycle", "Keeps ownership, validation, classification, retention, deletion, and audit decisions visible."},
                {"INTEGRATIONS", "Interfaces and integrations", "Treats the SMS provider as a failure boundary without inventing its protocol."},
                {"QUALITY", "Quality attributes and measurable scenarios", "Requires observed workflow, accessibility, recoverability, and audit evidence."},
                {"SECURITY_PRIVACY", "Security, privacy, and abuse resistance", "Defines role and record boundaries without claiming legal compliance."},
                {"DELIVERY_OPERATIONS", "Delivery, deployment, and operations", "Records environment, monitoring, backup, release, and support decisions."},
                {"VERIFICATION_TRACEABILITY", "Verification and traceability strategy", "Links every normative requirement to acceptance and review evidence."}
        };
        for (String[] chapter : chapterData) sections.addObject().put("id", chapter[0]).put("title", chapter[1]).put("purpose", chapter[2]).put("content", richChapterContent(chapter[0])).put("source_status", "DERIVED");
        var workflow = content.putArray("workflows").addObject();
        workflow.put("id", "WF-001").put("title", "Register, book, document, and follow up").put("trigger", "A patient requests a clinic appointment.");
        workflow.putArray("actors").add("Patient").add("Receptionist").add("Clinician");
        workflow.putArray("preconditions").add("The clinic is accepting appointment requests.");
        workflow.putArray("main_flow").add("Reception identifies or registers the patient.").add("Reception selects an available clinician slot.").add("The system confirms a non-overlapping appointment.").add("The clinician records the visit outcome.").add("The patient receives confirmed follow-up instructions.");
        workflow.putArray("alternate_flows").add("Reception selects another slot when the requested slot is unavailable.");
        workflow.putArray("failure_recovery").add("A notification failure remains visible for authorized follow-up without reversing a confirmed booking.");
        workflow.putArray("postconditions").add("The appointment and visit workflow state is reviewable by authorized staff.");
        var quality = content.putArray("quality_scenarios").addObject();
        quality.put("id", "QS-001").put("quality_attribute", "Workflow observability").put("source", "Confirmed quality goal").put("stimulus", "A booking succeeds, is rejected, or fails because a dependency is unavailable.").put("environment", "The confirmed clinic operating environment.").put("artifact", "Booking workflow and operational telemetry.").put("response", "The system records a distinguishable outcome without exposing unrelated patient data.").put("response_measure", "Target and review interval require owner approval.").put("status", "UNRESOLVED");
        var risk = content.putArray("risks").addObject();
        risk.put("id", "RISK-001").put("category", "Privacy and safety").put("title", "Unauthorized patient-record access").put("description", "A user may access a patient record outside the user's confirmed authority.").put("status", "CONFIRMED").put("owner", "Clinic product owner").put("source_detail", "Confirmed discovery risk.");
        var decision = content.putArray("decisions").addObject();
        decision.put("id", "DEC-001").put("category", "Decision").put("title", "Recovery targets").put("description", "Recovery targets remain governed by the business-impact review and are not release claims in this package.").put("status", "RESOLVED").put("owner", "Clinic product owner").put("source_detail", "Reviewed package fixture decision.");
        var standards = content.putArray("standards_applied");
        standards.addObject().put("id", "STD-ISO_29148").put("category", "Standards guidance").put("title", "ISO/IEC/IEEE 29148:2018").put("description", "Requirements quality and traceability guidance; no certification claim.").put("status", "RECOMMENDED").put("owner", "Documentation reviewer").put("source_detail", "Official source metadata checked 2026-08-16.");
        standards.addObject().put("id", "STD-ISO_42010").put("category", "Standards guidance").put("title", "ISO/IEC/IEEE 42010:2022").put("description", "Architecture-description guidance; no certification claim.").put("status", "RECOMMENDED").put("owner", "Architecture reviewer").put("source_detail", "Official source metadata checked 2026-08-16.");
        var diagrams = content.putArray("diagrams");
        diagrams.addObject().put("id", "DGM-001").put("type", "C4_CONTEXT").put("title", "ClinicFlow system context").put("notation", "MERMAID").put("source", "flowchart LR\n  PATIENT[\"Patient\"] --> SYSTEM[\"ClinicFlow\"]\n  RECEPTION[\"Receptionist\"] --> SYSTEM\n  CLINICIAN[\"Clinician\"] --> SYSTEM\n  SYSTEM --> SMS[\"SMS provider\"]").put("rationale", "Shows confirmed actors and the SMS dependency.").put("status", "DERIVED");
        diagrams.addObject().put("id", "DGM-002").put("type", "WORKFLOW").put("title", "Clinic workflow").put("notation", "MERMAID").put("source", "flowchart TD\n  REGISTER[\"Identify or register patient\"] --> SLOT[\"Select available slot\"]\n  SLOT --> BOOK[\"Confirm appointment\"]\n  BOOK --> VISIT[\"Record visit\"]\n  VISIT --> FOLLOWUP[\"Provide follow-up\"]").put("rationale", "Shows the confirmed success path.").put("status", "DERIVED");
        diagrams.addObject().put("id", "DGM-003").put("type", "ERD").put("title", "Conceptual data model").put("notation", "MERMAID").put("source", "erDiagram\n  PATIENT {\n    uuid id PK\n  }\n  APPOINTMENT {\n    uuid id PK\n  }\n  VISIT {\n    uuid id PK\n  }\n  PATIENT ||--o{ APPOINTMENT : has\n  PATIENT ||--o{ VISIT : has").put("rationale", "Shows only relationships stated in the canonical data requirement and narrative.").put("status", "DERIVED");
        var requirementDetails = content.putArray("requirements");
        for (String[] detail : new String[][] {
                {"SRS-FR-001", "Create an appointment", "FUNCTIONAL"}, {"SRS-FR-002", "Prevent overlapping appointments", "FUNCTIONAL"},
                {"SRS-DATA-001", "Associate clinical records", "DATA"}, {"SRS-API-001", "Record reminder outcomes", "API"},
                {"SRS-SEC-001", "Enforce record authorization", "SECURITY"}, {"SRS-PRIV-001", "Audit patient access", "PRIVACY"},
                {"SRS-ACC-001", "Expose status without color", "ACCESSIBILITY"}, {"SRS-NFR-001", "Measure workflow outcomes", "NON_FUNCTIONAL"},
                {"SRS-OPS-001", "Expose service health", "OPERATIONS"}, {"SRS-TEST-001", "Retain release evidence", "TEST"},
                {"SRS-BR-001", "Provide one workflow source", "BUSINESS"}, {"SRS-UX-001", "Present safe recovery guidance", "UX"}
        }) {
            var item = requirementDetails.addObject();
            item.put("id", detail[0]).put("title", detail[1]).put("type", detail[2]).put("status", "CONFIRMED")
                    .put("trigger", "The applicable confirmed clinic workflow reaches this obligation.")
                    .put("failure_behavior", "The system preserves the prior safe state, records a reviewable failure, and does not claim success.");
            item.putArray("actors").add("Authorized clinic user"); item.putArray("preconditions").add("The actor is authenticated and authorized.");
            item.putArray("data_involved").add("Applicable confirmed clinic record"); item.putArray("dependencies"); item.putArray("risks");
            if ("SRS-API-001".equals(detail[0])) {
                item.put("api_path", "/reminder-outcomes").put("http_method", "post").put("operation_id", "recordReminderOutcome")
                        .put("source_detail", "Confirmed HTTP contract: POST /reminder-outcomes uses operation ID recordReminderOutcome.");
            }
        }
        return content;
    }

    /** A grounded, varied fixture prevents export QA from approving a page count made from boilerplate. */
    private String richChapterContent(String id) {
        return switch (id) {
            case "INTRODUCTION" -> """
                    ClinicFlow is a first-release coordination product for one clinic group. Its purpose is to replace the fragmented handoff between patient registration, appointment scheduling, visit recording, reminders, and operational review with one reviewable workflow record. The product is not a clinical decision system and does not create treatment recommendations, prescribe medication, process insurance claims, or make billing decisions.

                    This specification is intended for the clinic product owner, reception staff, clinicians, administrators, delivery team, security reviewer, QA team, and operators. It distinguishes an observed project fact from a recommendation or unresolved decision. A statement marked CONFIRMED can be traced to the approved clinic brief. A decision record identifies the owner and consequence of missing information. That distinction matters because the first release must be safe to review without pretending that topology, retention policy, recovery objectives, or vendor contracts have already been approved.

                    The package is a delivery baseline, not a certification claim. Before implementation begins, reviewers must confirm the decisions that affect patient-data handling, service recovery, role authority, and reminder-provider behavior. Changes to approved scope, workflow state, or requirement acceptance evidence require a new reviewed package version.
                    """;
            case "BUSINESS_CONTEXT" -> """
                    Clinic teams currently coordinate appointments, visit records, and follow-up through disconnected tools. A receptionist needs to identify or register a patient, determine whether a clinician slot is available, and communicate a confirmed outcome. A clinician needs the resulting visit to remain connected to the applicable patient and appointment. Clinic administrators need a reviewable operational view rather than relying on informal handoffs or assumptions about notification delivery.

                    The first-release business outcome is a single accountable workflow source for appointment and visit status. The package therefore treats an overlapping appointment, an unauthorized record request, a failed reminder, and missing verification evidence as business-relevant exceptions, not edge cases to be ignored. Success is not defined by a generic claim of efficiency. Success requires authorized staff to determine the current state of the confirmed workflow and to see a distinct outcome when booking, notification, or recovery does not complete as expected.

                    Numeric service targets, staffing savings, and conversion targets are not confirmed. The clinic product owner must approve the measures, observation interval, target, and escalation use before they are treated as release criteria. Until then, the product must record outcomes in a way that makes the eventual measurement decision possible.
                    """;
            case "SCOPE" -> """
                    The confirmed first release covers patient registration, appointment scheduling, visit documentation, reminders, and operational review for one clinic group. Reception may identify or register a patient, select an available clinician slot, and confirm an appointment only when it does not overlap another confirmed appointment for that clinician. A clinician may record the visit outcome. The system must retain the status needed for authorized staff to review the workflow and the delivery outcome of a confirmed reminder.

                    The scope stops at the clinic workflow described above. Billing, insurance claims, prescribing automation, clinical diagnosis or treatment recommendations, and unconfirmed external systems are explicitly excluded. An SMS notification provider is in scope only as a named dependency whose exact protocol, data contract, retry policy, and provider commitments require confirmation. The document does not authorize the delivery team to infer those details from normal industry practice.

                    Scope review should occur whenever a requested feature changes the named workflow, introduces a new patient-data consumer, changes role permissions, or requires a new external contract. The product owner owns those decisions. A change is not in scope merely because it appears adjacent to scheduling or clinical administration.
                    """;
            case "STAKEHOLDERS" -> """
                    Patients are the people whose appointments, visits, and follow-up information are managed by the clinic workflow. They need an understandable confirmed outcome, but the exact patient-facing channels and self-service capabilities are not established in this first-release brief. Receptionists initiate patient identification, registration, and booking activity. Clinicians record visit outcomes. Clinic administrators review workflow status, access evidence, and operational conditions within their approved authority.

                    The product owner is accountable for approving scope, workflow policy, data lifecycle decisions, measurable quality targets, and recovery objectives. The delivery team is accountable for implementing only approved requirements and for recording evidence against their acceptance criteria. The security and privacy reviewer validates that access and audit decisions are represented before release; that role does not create an unsupported legal compliance conclusion.

                    Permission boundaries remain material. The brief confirms that access to patient and visit records must be denied when the requesting user lacks the applicable role permission, but it does not yet define a complete role-to-action matrix. The owner must confirm which actor may read, create, update, review, or recover each record type before design and test cases can claim complete authorization coverage.
                    """;
            case "WORKFLOWS" -> """
                    The primary confirmed workflow begins when a patient requests an appointment. Reception identifies an existing patient or registers the patient, selects an available clinician slot, and submits the booking for confirmation. The system must prevent confirmation when another confirmed appointment already occupies that clinician and time. When the booking is confirmed, the resulting appointment becomes available for the applicable clinical visit workflow. The clinician records the visit outcome, and the patient receives confirmed follow-up instructions through the approved workflow.

                    The workflow has an explicit alternate path for an unavailable requested slot: reception selects another slot rather than treating the original request as confirmed. It also has a failure boundary around notification. A failed reminder remains visible for authorized follow-up and must not reverse a confirmed booking or create a false claim that the patient received it. The exact retry policy, timeout, provider callback format, and reconciliation procedure are unresolved provider-contract decisions.

                    Recovery must preserve the last known safe state. When a booking is rejected, the original appointment state is unchanged. When a dependency is unavailable, authorized staff need a reviewable failure outcome and a permitted next action. The detailed workflow and requirement acceptance evidence must show whether the result was successful, rejected, failed, or awaiting recovery.
                    """;
            case "BUSINESS_RULES" -> """
                    The confirmed booking rule is that an appointment slot cannot overlap for the same doctor. The rule is evaluated before a booking becomes confirmed. A rejection must not alter either the previously confirmed appointment or the unconfirmed request. The user-facing response must make the safe next action understandable without exposing unrelated patient information. The rule therefore links booking validation, access control, auditability, and recovery rather than functioning as a database uniqueness statement alone.

                    Workflow status is also a business rule concern. Authorized clinic staff need one reviewable source for appointment and visit workflow status. The status must be understandable without relying on color alone and must remain distinguishable when booking is rejected, notification fails, or a dependency is unavailable. The brief does not confirm a complete state model, permitted manual override, cancellation policy, reschedule policy, or the authority that may merge patient records.

                    These gaps are decisions rather than design freedom. The clinic product owner must approve the allowed state transitions, the evidence required for a status change, and the role permitted to perform each transition. QA must verify both allowed and rejected cases for every approved rule before a release baseline is accepted.
                    """;
            case "DATA" -> """
                    ClinicFlow manages Patient, Appointment, and Visit records. Each Appointment and Visit must be associated with the applicable Patient record so authorized staff can review the relevant workflow context. The present evidence confirms those entities and relationships at a conceptual level; it does not confirm field definitions, unique identifiers beyond the technical package placeholder, validation constraints, data classification labels, retention periods, deletion process, archival process, or record-merge policy.

                    Data ownership and lifecycle choices must be reviewed before implementation makes them irreversible. The owner must decide which team owns patient identity correction, what evidence permits a merge, what happens to connected appointments and visits, and how the system records the decision. Similar review is needed for visit outcomes and reminder delivery records. A data dictionary may identify confirmed concepts and open questions, but it must not invent schemas or retention commitments.

                    Access to patient data has an explicit accountability requirement. Every patient-record access must record the authorized actor, purpose, time, and outcome. The operational and security artifacts must use the same terminology as this section so that access evidence, workflow review, test evidence, and incident investigation can be connected without ambiguous synonyms.
                    """;
            case "INTEGRATIONS" -> """
                    The only confirmed external dependency is an SMS notification provider. ClinicFlow must exchange confirmed reminder delivery outcomes with that provider and keep a provider failure visible for authorized follow-up. The dependency is part of the workflow boundary because a reminder result affects operational review, but it is not evidence of a confirmed HTTP endpoint, event broker, payload schema, authentication mechanism, delivery guarantee, retry policy, or service-level commitment.

                    The package consequently treats the OpenAPI contract as unresolved rather than fabricating requirement-derived paths. Before an interface contract is published, the product owner and integration owner must confirm the provider, direction of exchange, data minimization boundary, method or channel, authentication decision, identifiers, idempotency behavior, provider response categories, outage handling, reconciliation process, and evidence retained for a delivery outcome.

                    Integration failure cannot silently become a booking failure. A confirmed appointment remains confirmed when the reminder provider fails, while the failed reminder needs an authorized follow-up path. The operations runbook must identify the dependency state separately from application health. Any later integration is a scope and security review event because it may introduce a new data consumer or workflow state.
                    """;
            case "QUALITY" -> """
                    The confirmed quality priorities are auditability, accessibility, recoverability, and measurable workflow outcomes. These priorities apply to the clinic workflows rather than serving as abstract labels. Booking outcomes must be distinguishable as successful, rejected, or failed. Authorized review must be able to establish which actor accessed a patient record, for what purpose, when the access occurred, and what the outcome was. Status meaning must not rely on color alone.

                    Recoverability is defined in the current evidence as preserving the prior safe state and exposing a reviewable failure when a dependency or workflow step cannot complete. Recovery-time and recovery-point targets are not confirmed. The clinic product owner must approve the business-impact analysis, target, owner, observation interval, and escalation process before numeric recovery claims are used. The specification must therefore retain the decision and the verification evidence needed to review it later.

                    Quality evidence must support release review. Monitoring distinguishes booking success, booking rejection, booking failure, application health, and SMS dependency health. The detailed metric design remains unresolved: reviewers must approve definitions, data collection boundaries, reporting interval, target, and alert use. Until then, the package must not imply performance, availability, or compliance targets that the clinic has not supplied.
                    """;
            case "SECURITY_PRIVACY" -> """
                    Patient and visit records require enforceable authorization boundaries. The system must deny access when the requesting user lacks the applicable role permission and must not disclose record content or alter data as part of the denied request. Each patient-record access must record the actor, purpose, time, and outcome so authorized reviewers can inspect the access history. These are confirmed product requirements, not a statement that a particular privacy law, certification, or security standard has been satisfied.

                    The security review should consider the booking workflow, patient identity handling, appointment and visit relationships, reminder outcome visibility, administrative review, and failed dependency recovery. The evidence identifies unauthorized record access as a risk. It does not define authentication technology, session policy, encryption approach, audit-log retention, incident response timeline, threat-model ranking, lawful basis, processor relationship, or geographic data obligations. Those items must remain decision records with named owners rather than becoming invented controls.

                    Verification focuses on observable boundaries: a permitted role can access its authorized record; a forbidden role receives no record content; denied access leaves the record unchanged; and each access produces reviewable evidence. Accessibility requirements complement these controls by requiring status text that remains understandable without color alone.
                    """;
            case "DELIVERY_OPERATIONS" -> """
                    The first release is confirmed as a browser-based service. Hosting topology, environment separation, backup design, restoration process, recovery targets, monitoring platform, deployment ownership, support hours, and change-management process remain owner decisions. The delivery team must not infer a cloud provider, regional strategy, database, queue, backup schedule, or deployment automation from the fact that the product is browser based.

                    Operations nevertheless has confirmed responsibilities. Authorized operators need a health view that reports application state and the SMS dependency state separately. A dependency failure must produce an operator-visible event. The workflow evidence must make it possible to distinguish a confirmed booking from a failed reminder, and a recovery action must not claim a successful outcome without evidence. The release process must retain verification evidence for every MUST requirement.

                    Before production approval, the product owner and operations owner must resolve the deployment topology, environment ownership, monitoring and alert decisions, backup and restoration expectations, incident contact path, change approval process, and service review cadence. The runbook should then translate those approved decisions into executable steps and escalation guidance. Until they are approved, the package presents the gaps rather than pseudo-procedures.
                    """;
            case "VERIFICATION_TRACEABILITY" -> """
                    Every MUST requirement in the ClinicFlow baseline requires reviewable verification evidence. The package traces the requirement to its acceptance criteria, primary use case or non-functional view, applicable entity where supported, and API contract status. A missing result blocks a final pass claim. This approach allows a reviewer to inspect whether an appointment conflict was rejected, whether unauthorized access exposed no record content, whether reminder outcomes remained visible, and whether accessibility status text was reviewed without relying on color.

                    Verification methods are selected according to the obligation. Booking and reminder outcomes require tests. Record associations and access evidence may require inspection. Workflow status and recovery guidance may require demonstration. Operational measurement and release readiness require analysis. The evidence must identify the environment, setup, actor or role, input, expected outcome, actual outcome, and reviewer disposition. A generic statement that testing occurred is not enough.

                    Traceability is intentionally conservative. Confirmed HTTP methods and paths are absent, so API requirements remain explicitly unresolved rather than linked to fabricated operations. Similarly, detailed data fields and topology are not claimed until approved. The product owner, QA lead, and relevant reviewer must resolve gaps before a release baseline is marked complete.
                    """;
            default -> throw new IllegalArgumentException("Unknown ClinicFlow fixture section: " + id);
        };
    }

    private record Fixture(UserEntity owner, ProjectEntity project, SrsVersionEntity srs) { }
}
