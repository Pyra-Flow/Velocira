package com.velocira.backend.documentation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.velocira.backend.documentation.model.DocumentationArtifactEntity;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import com.velocira.backend.documentation.model.DocumentationExportLayout;
import com.velocira.backend.documentation.model.DocumentationExportStyle;
import com.velocira.backend.documentation.model.DocumentationExportTemplate;
import com.velocira.backend.documentation.model.DocumentationExportTheme;
import com.velocira.backend.documentation.model.DocumentationPackageEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Renders every immutable export from the same package snapshot. Styled document formats
 * share a small document model so PDF, DOCX, ZIP and visual diagram assets remain aligned.
 */
@Component
public class DocumentationExportRenderer {
    private static final List<DocumentationArtifactType> DOCUMENT_ORDER = List.of(
            DocumentationArtifactType.SRS, DocumentationArtifactType.BRD, DocumentationArtifactType.ARCHITECTURE,
            DocumentationArtifactType.USE_CASES, DocumentationArtifactType.C4_CONTEXT, DocumentationArtifactType.WORKFLOWS,
            DocumentationArtifactType.DATA_DICTIONARY, DocumentationArtifactType.ERD, DocumentationArtifactType.OPENAPI,
            DocumentationArtifactType.SECURITY, DocumentationArtifactType.TEST_PLAN, DocumentationArtifactType.DEPLOYMENT,
            DocumentationArtifactType.OPERATIONS, DocumentationArtifactType.USER_MANUAL,
            DocumentationArtifactType.RISK_REGISTER, DocumentationArtifactType.TRACEABILITY);

    public RenderedExport render(DocumentationExportFormat format, DocumentationPackageEntity documentationPackage,
                                 List<DocumentationArtifactEntity> artifacts) {
        return render(format, documentationPackage, artifacts, DocumentationExportStyle.defaults());
    }

    public RenderedExport render(DocumentationExportFormat format, DocumentationPackageEntity documentationPackage,
                                 List<DocumentationArtifactEntity> artifacts, DocumentationExportStyle requestedStyle) {
        DocumentationExportStyle style = requestedStyle == null ? DocumentationExportStyle.defaults() : requestedStyle;
        Map<DocumentationArtifactType, DocumentationArtifactEntity> byType = new EnumMap<>(DocumentationArtifactType.class);
        artifacts.forEach(artifact -> byType.put(artifact.getArtifactType(), artifact));
        PackageSnapshot snapshot = PackageSnapshot.from(documentationPackage, byType);
        String projectSlug = slug(snapshot.projectName());
        try {
            return switch (format) {
                case ZIP -> new RenderedExport(projectSlug + "-documentation-package-v" + snapshot.versionNumber() + ".zip",
                        "application/zip", zip(snapshot, style));
                case MARKDOWN -> new RenderedExport(projectSlug + "-documentation-package-v" + snapshot.versionNumber() + ".md",
                        "text/markdown; charset=utf-8", snapshot.combinedMarkdown().getBytes(StandardCharsets.UTF_8));
                case PDF -> new RenderedExport(projectSlug + "-documentation-package-v" + snapshot.versionNumber() + ".pdf",
                        "application/pdf", pdf(snapshot, style));
                case DOCX -> new RenderedExport(projectSlug + "-documentation-package-v" + snapshot.versionNumber() + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(snapshot, style));
                case OPENAPI_JSON -> sourceExport(projectSlug, snapshot, DocumentationArtifactType.OPENAPI, ".openapi.json", "application/vnd.oai.openapi+json;version=3.1");
                case OPENAPI_YAML -> new RenderedExport(projectSlug + "-v" + snapshot.versionNumber() + ".openapi.yaml",
                        "application/yaml; charset=utf-8", requireArtifact(snapshot, DocumentationArtifactType.OPENAPI,
                        openApiYaml(snapshot.artifact(DocumentationArtifactType.OPENAPI)).getBytes(StandardCharsets.UTF_8)));
                case UML_SOURCE -> sourceExport(projectSlug, snapshot, DocumentationArtifactType.USE_CASES, ".use-cases.puml", "text/plain; charset=utf-8");
                case ERD_SOURCE -> sourceExport(projectSlug, snapshot, DocumentationArtifactType.ERD, ".erd.mmd", "text/plain; charset=utf-8");
            };
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the documentation export.", exception);
        }
    }

    /**
     * Produces the exact same visual diagram payload that is placed in a ZIP export.
     * The web viewer consumes this response so diagram preview and download never drift.
     */
    public RenderedExport renderPreview(DocumentationArtifactType artifactType, DocumentationPackageEntity documentationPackage,
                                        List<DocumentationArtifactEntity> artifacts, DocumentationExportStyle requestedStyle) {
        DocumentationExportStyle style = requestedStyle == null ? DocumentationExportStyle.defaults() : requestedStyle;
        Map<DocumentationArtifactType, DocumentationArtifactEntity> byType = new EnumMap<>(DocumentationArtifactType.class);
        artifacts.forEach(artifact -> byType.put(artifact.getArtifactType(), artifact));
        PackageSnapshot snapshot = PackageSnapshot.from(documentationPackage, byType);
        Theme theme = Theme.from(style.theme());
        return switch (artifactType) {
            case USE_CASES -> new RenderedExport("use-case-map.svg", "image/svg+xml; charset=utf-8",
                    requireArtifact(snapshot, artifactType, useCaseSvg(snapshot.canonicalModel(), theme, style.template()).getBytes(StandardCharsets.UTF_8)));
            case C4_CONTEXT -> new RenderedExport("c4-system-context.svg", "image/svg+xml; charset=utf-8",
                    requireArtifact(snapshot, artifactType, contextSvg(snapshot.canonicalModel(), theme).getBytes(StandardCharsets.UTF_8)));
            case WORKFLOWS -> new RenderedExport("primary-workflow.svg", "image/svg+xml; charset=utf-8",
                    requireArtifact(snapshot, artifactType, workflowSvg(snapshot.canonicalModel(), theme).getBytes(StandardCharsets.UTF_8)));
            case ERD -> new RenderedExport("entity-relationship-diagram.svg", "image/svg+xml; charset=utf-8",
                    requireArtifact(snapshot, artifactType, erdSvg(snapshot.canonicalModel(), theme, style.template()).getBytes(StandardCharsets.UTF_8)));
            default -> new RenderedExport(artifactType.name().toLowerCase(Locale.ROOT) + ".md", "text/markdown; charset=utf-8",
                    requireArtifact(snapshot, artifactType, snapshot.artifact(artifactType).sourceContent().getBytes(StandardCharsets.UTF_8)));
        };
    }

    private byte[] requireArtifact(PackageSnapshot snapshot, DocumentationArtifactType type, byte[] bytes) {
        if (!snapshot.hasArtifact(type)) throw new IllegalArgumentException(type + " was omitted from this package: " + snapshot.omissionReason(type));
        return bytes;
    }

    private RenderedExport sourceExport(String projectSlug, PackageSnapshot snapshot, DocumentationArtifactType type,
                                        String extension, String contentType) {
        if (!snapshot.hasArtifact(type)) throw new IllegalArgumentException(type + " was omitted from this package: " + snapshot.omissionReason(type));
        return new RenderedExport(projectSlug + "-v" + snapshot.versionNumber() + extension, contentType,
                snapshot.artifact(type).sourceContent().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] zip(PackageSnapshot snapshot, DocumentationExportStyle style) throws IOException {
        Theme theme = Theme.from(style.theme());
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "README.md", readme(snapshot, style));
            for (DocumentationArtifactType type : DOCUMENT_ORDER) {
                if (type == DocumentationArtifactType.OPENAPI || type == DocumentationArtifactType.USE_CASES
                        || type == DocumentationArtifactType.C4_CONTEXT || type == DocumentationArtifactType.WORKFLOWS
                        || type == DocumentationArtifactType.ERD) continue;
                if (!snapshot.hasArtifact(type)) continue;
                put(zip, "documents/" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".md", snapshot.artifact(type).sourceContent());
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.SRS)) put(zip, "srs.md", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.USE_CASES)) {
                put(zip, "use-cases.md", snapshot.artifact(DocumentationArtifactType.USE_CASES).content());
                put(zip, "diagrams/use-cases.puml", snapshot.artifact(DocumentationArtifactType.USE_CASES).sourceContent());
                put(zip, "diagrams/use-cases.svg", useCaseSvg(snapshot.canonicalModel(), theme, style.template()));
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.C4_CONTEXT)) {
                put(zip, "diagrams/c4-context.mmd", snapshot.artifact(DocumentationArtifactType.C4_CONTEXT).sourceContent());
                put(zip, "diagrams/c4-context.svg", contextSvg(snapshot.canonicalModel(), theme));
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.WORKFLOWS)) {
                put(zip, "diagrams/workflow.mmd", snapshot.artifact(DocumentationArtifactType.WORKFLOWS).sourceContent());
                put(zip, "diagrams/workflow.svg", workflowSvg(snapshot.canonicalModel(), theme));
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.ERD)) {
                put(zip, "erd.md", snapshot.artifact(DocumentationArtifactType.ERD).content());
                put(zip, "diagrams/erd.mmd", snapshot.artifact(DocumentationArtifactType.ERD).sourceContent());
                put(zip, "diagrams/erd.svg", erdSvg(snapshot.canonicalModel(), theme, style.template()));
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.OPENAPI)) {
                put(zip, "openapi.json", snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent());
                put(zip, "openapi.yaml", openApiYaml(snapshot.artifact(DocumentationArtifactType.OPENAPI)));
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.TRACEABILITY)) put(zip, "traceability.md", snapshot.artifact(DocumentationArtifactType.TRACEABILITY).sourceContent());
            put(zip, "canonical-model.json", snapshot.canonicalModel().toPrettyString());
            put(zip, "validation.json", snapshot.validation().toPrettyString());
            put(zip, "documentation-package.docx", docx(snapshot, style));
            put(zip, "documentation-package.pdf", pdf(snapshot, style));
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private String readme(PackageSnapshot snapshot, DocumentationExportStyle style) {
        StringBuilder readme = new StringBuilder("# ").append(snapshot.projectName()).append(" documentation package\n\n")
                .append("Package v").append(snapshot.versionNumber()).append(" is generated from SRS v").append(snapshot.srsVersionNumber()).append(".\n\n")
                .append("## Presentation\n\n")
                .append("- Template: ").append(style.template()).append("\n")
                .append("- Theme: ").append(style.theme()).append("\n")
                .append("- Layout: ").append(style.layout()).append("\n\n")
                .append("## Included files\n\n")
                .append("- `documentation-package.pdf` - styled, print-ready package with visual diagrams.\n")
                .append("- `documentation-package.docx` - editable Word package with the same visual system.\n")
                .append("- `diagrams/*.svg` - themed visual diagrams; `.mmd` and `.puml` remain faithful source files.\n")
                .append("- `documents/*.md` - emitted human-readable source deliverables.\n\n")
                .append("## Artifact disposition\n\n");
        for (JsonNode item : snapshot.canonicalModel().path("documentPlan")) {
            readme.append("- **").append(item.path("artifactType").asText()).append(" — ")
                    .append(item.path("status").asText()).append(":** ").append(item.path("reason").asText()).append("\n");
        }
        return readme.toString();
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        put(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private byte[] pdf(PackageSnapshot snapshot, DocumentationExportStyle style) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle(snapshot.projectName() + " documentation package v" + snapshot.versionNumber());
            information.setAuthor("Velocira");
            information.setSubject("Canonical SRS documentation package and validation evidence");
            information.setCreator("Velocira documentation compiler");
            information.setKeywords("SRS, traceability, documentation, canonical source");
            document.setDocumentInformation(information);
            PdfComposer composer = new PdfComposer(document, snapshot, style);
            composer.cover();
            composer.contents();
            if (snapshot.hasArtifact(DocumentationArtifactType.SRS)) composer.section("System requirements", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.BRD)) composer.section("Business requirements", snapshot.artifact(DocumentationArtifactType.BRD).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.ARCHITECTURE)) composer.section("Architecture description", snapshot.artifact(DocumentationArtifactType.ARCHITECTURE).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.C4_CONTEXT)) composer.diagram("C4 system context", diagramPng(snapshot.canonicalModel(), DiagramKind.CONTEXT, style));
            if (snapshot.hasArtifact(DocumentationArtifactType.USE_CASES)) {
                composer.diagram("Use case map", diagramPng(snapshot.canonicalModel(), DiagramKind.USE_CASES, style));
                composer.section("Use cases", snapshot.artifact(DocumentationArtifactType.USE_CASES).content());
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.WORKFLOWS)) {
                composer.diagram("Primary workflow", diagramPng(snapshot.canonicalModel(), DiagramKind.WORKFLOW, style));
                composer.section("Workflow and recovery", snapshot.artifact(DocumentationArtifactType.WORKFLOWS).content());
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.ERD)) composer.diagram("Entity relationship diagram", diagramPng(snapshot.canonicalModel(), DiagramKind.ERD, style));
            if (snapshot.hasArtifact(DocumentationArtifactType.DATA_DICTIONARY)) composer.section("Data dictionary", snapshot.artifact(DocumentationArtifactType.DATA_DICTIONARY).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.SECURITY)) composer.section("Security and privacy", snapshot.artifact(DocumentationArtifactType.SECURITY).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.TEST_PLAN)) composer.section("Verification and testing", snapshot.artifact(DocumentationArtifactType.TEST_PLAN).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.DEPLOYMENT)) composer.section("Deployment", snapshot.artifact(DocumentationArtifactType.DEPLOYMENT).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.OPERATIONS)) composer.section("Operations", snapshot.artifact(DocumentationArtifactType.OPERATIONS).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.USER_MANUAL)) composer.section("User manual", snapshot.artifact(DocumentationArtifactType.USER_MANUAL).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.RISK_REGISTER)) composer.section("Risk and decision register", snapshot.artifact(DocumentationArtifactType.RISK_REGISTER).sourceContent());
            if (snapshot.hasArtifact(DocumentationArtifactType.TRACEABILITY)) composer.traceability(snapshot.canonicalModel());
            if (snapshot.hasArtifact(DocumentationArtifactType.OPENAPI)) composer.openApi(snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent());
            composer.close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docx(PackageSnapshot snapshot, DocumentationExportStyle style) throws IOException {
        Theme theme = Theme.from(style.theme());
        TemplateSpec template = TemplateSpec.from(style.template());
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            document.enforceUpdateFields();
            document.getProperties().getCoreProperties().setTitle(snapshot.projectName() + " documentation package v" + snapshot.versionNumber());
            document.getProperties().getCoreProperties().setCreator("Velocira");
            document.getProperties().getCoreProperties().setSubjectProperty("Canonical SRS documentation package and validation evidence");
            document.getProperties().getCoreProperties().setDescription("Generated from one immutable canonical SRS snapshot; omissions and material gaps are recorded in the package validation.");
            configureDocxFurniture(document, snapshot, style, theme);
            addDocxCover(document, snapshot, style, theme, template);
            addDocxContents(document, snapshot, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.SRS)) addDocxSection(document, "System requirements", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.BRD)) addDocxSection(document, "Business requirements", snapshot.artifact(DocumentationArtifactType.BRD).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.ARCHITECTURE)) addDocxSection(document, "Architecture description", snapshot.artifact(DocumentationArtifactType.ARCHITECTURE).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.C4_CONTEXT)) addDocxDiagram(document, "C4 system context", diagramPng(snapshot.canonicalModel(), DiagramKind.CONTEXT, style), theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.USE_CASES)) {
                addDocxDiagram(document, "Use case map", diagramPng(snapshot.canonicalModel(), DiagramKind.USE_CASES, style), theme);
                addDocxSection(document, "Use cases", snapshot.artifact(DocumentationArtifactType.USE_CASES).content(), style, theme);
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.WORKFLOWS)) {
                addDocxDiagram(document, "Primary workflow", diagramPng(snapshot.canonicalModel(), DiagramKind.WORKFLOW, style), theme);
                addDocxSection(document, "Workflow and recovery", snapshot.artifact(DocumentationArtifactType.WORKFLOWS).content(), style, theme);
            }
            if (snapshot.hasArtifact(DocumentationArtifactType.ERD)) addDocxDiagram(document, "Entity relationship diagram", diagramPng(snapshot.canonicalModel(), DiagramKind.ERD, style), theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.DATA_DICTIONARY)) addDocxSection(document, "Data dictionary", snapshot.artifact(DocumentationArtifactType.DATA_DICTIONARY).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.SECURITY)) addDocxSection(document, "Security and privacy", snapshot.artifact(DocumentationArtifactType.SECURITY).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.TEST_PLAN)) addDocxSection(document, "Verification and testing", snapshot.artifact(DocumentationArtifactType.TEST_PLAN).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.DEPLOYMENT)) addDocxSection(document, "Deployment", snapshot.artifact(DocumentationArtifactType.DEPLOYMENT).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.OPERATIONS)) addDocxSection(document, "Operations", snapshot.artifact(DocumentationArtifactType.OPERATIONS).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.USER_MANUAL)) addDocxSection(document, "User manual", snapshot.artifact(DocumentationArtifactType.USER_MANUAL).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.RISK_REGISTER)) addDocxSection(document, "Risk and decision register", snapshot.artifact(DocumentationArtifactType.RISK_REGISTER).sourceContent(), style, theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.TRACEABILITY)) addDocxTraceability(document, snapshot.canonicalModel(), theme);
            if (snapshot.hasArtifact(DocumentationArtifactType.OPENAPI)) addDocxOpenApi(document, snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent(), theme);
            document.write(output);
            return output.toByteArray();
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(12_240));
        pageSize.setH(BigInteger.valueOf(15_840));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1_440));
        margins.setRight(BigInteger.valueOf(1_440));
        margins.setBottom(BigInteger.valueOf(1_440));
        margins.setLeft(BigInteger.valueOf(1_440));
        margins.setHeader(BigInteger.valueOf(708));
        margins.setFooter(BigInteger.valueOf(708));
        margins.setGutter(BigInteger.ZERO);
    }

    private void configureDocxFurniture(XWPFDocument document, PackageSnapshot snapshot, DocumentationExportStyle style, Theme theme) {
        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph headerParagraph = header.createParagraph();
        headerParagraph.setSpacingAfter(0);
        XWPFRun headerRun = headerParagraph.createRun();
        headerRun.setText("VELOCIRA  /  " + snapshot.projectName());
        headerRun.setFontFamily("Calibri");
        headerRun.setFontSize(8);
        headerRun.setColor(theme.mutedHex());

        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph footerParagraph = footer.createParagraph();
        footerParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        XWPFRun footerLabel = footerParagraph.createRun();
        footerLabel.setText("Documentation package  |  Page ");
        footerLabel.setFontFamily("Calibri");
        footerLabel.setFontSize(8);
        footerLabel.setColor(theme.mutedHex());
        addPageField(footerParagraph, theme.mutedHex());
    }

    private void addPageField(XWPFParagraph paragraph, String color) {
        XWPFRun begin = paragraph.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = paragraph.createRun();
        instruction.getCTR().addNewInstrText().setStringValue(" PAGE ");
        XWPFRun end = paragraph.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        end.setColor(color);
    }

    private void addDocxCover(XWPFDocument document, PackageSnapshot snapshot, DocumentationExportStyle style, Theme theme, TemplateSpec template) {
        addDocxBand(document, "VELOCIRA  |  DOCUMENTATION MEMO", theme.primaryHex());
        XWPFParagraph title = document.createParagraph();
        title.setSpacingBefore(120);
        title.setSpacingAfter(60);
        addDocxText(title, snapshot.projectName(), 20, theme.inkHex(), true);
        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setSpacingAfter(180);
        addDocxText(subtitle, "Software delivery documentation brief", 13, theme.mutedHex(), false);

        XWPFTable metadata = document.createTable(5, 2);
        metadata.setWidth("9360");
        addMetadataRow(metadata.getRow(0), "Subject", "Documentation package v" + snapshot.versionNumber(), theme);
        addMetadataRow(metadata.getRow(1), "Canonical source", "SRS v" + snapshot.srsVersionNumber(), theme);
        addMetadataRow(metadata.getRow(2), "Generation mode", snapshot.generationMode(), theme);
        addMetadataRow(metadata.getRow(3), "Validation", snapshot.validationLabel(), theme);
        addMetadataRow(metadata.getRow(4), "Format", "standard_business_brief / memo_masthead", theme);
        configureDocxTable(metadata, 1760, 7600);

        XWPFParagraph purpose = document.createParagraph();
        purpose.setSpacingBefore(160);
        purpose.setSpacingAfter(80);
        purpose.setSpacingBetween(1.10);
        addDocxText(purpose, "Purpose: ", 11, theme.inkHex(), true);
        addDocxText(purpose, "Provide one reviewable hand-off whose emitted artifacts, omissions, diagrams, contracts, and trace links all resolve to the same canonical SRS snapshot.", 11, theme.inkHex(), false);
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private void addMetadataRow(org.apache.poi.xwpf.usermodel.XWPFTableRow row, String label, String value, Theme theme) {
        XWPFTableCell labelCell = row.getCell(0);
        XWPFTableCell valueCell = row.getCell(1);
        labelCell.setColor(theme.softHex());
        valueCell.setColor(theme.canvasHex());
        setDocxCell(labelCell, label.toUpperCase(Locale.ROOT), 8, theme.primaryHex(), true);
        setDocxCell(valueCell, value, 10, theme.inkHex(), false);
    }

    private void setDocxCell(XWPFTableCell cell, String text, int size, String color, boolean bold) {
        XWPFParagraph paragraph = cell.getParagraphArray(0);
        paragraph.setSpacingAfter(0);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Calibri");
        run.setFontSize(size);
        run.setColor(color);
        run.setBold(bold);
    }

    private void addDocxContents(XWPFDocument document, PackageSnapshot snapshot, Theme theme) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(120);
        heading.setSpacingAfter(140);
        setDocxOutlineLevel(heading, 0);
        addDocxText(heading, "Contents", 17, theme.primaryHex(), true);
        XWPFParagraph liveToc = document.createParagraph();
        liveToc.setSpacingAfter(120);
        XWPFRun begin = liveToc.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = liveToc.createRun();
        instruction.getCTR().addNewInstrText().setStringValue(" TOC \\o \"1-3\" \\h \\z \\u ");
        XWPFRun separate = liveToc.createRun();
        separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        addDocxText(liveToc, "Update fields when opening this document to refresh page numbers and hyperlinks.", 10, theme.mutedHex(), false);
        XWPFRun end = liveToc.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        XWPFParagraph callout = document.createParagraph();
        callout.setSpacingBefore(100);
        addDocxText(callout, "Package validation: " + snapshot.validationLabel(), 11, theme.primaryHex(), true);
        addDocxPageBreak(document);
    }

    private void addDocxSection(XWPFDocument document, String title, String markdown, DocumentationExportStyle style, Theme theme) {
        addDocxBand(document, title.toUpperCase(Locale.ROOT), theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(90);
        heading.setKeepNext(true);
        setDocxOutlineLevel(heading, 0);
        addDocxText(heading, title, 16, theme.primaryHex(), true);

        boolean code = false;
        boolean skippedArtifactTitle = false;
        String[] lines = markdown.split("\\r?\\n");
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String line = raw == null ? "" : raw;
            if (line.startsWith("```")) {
                code = !code;
                continue;
            }
            if (!code && isMarkdownTableStart(lines, index)) {
                List<List<String>> rows = new ArrayList<>();
                rows.add(parseMarkdownRow(line));
                index += 2;
                while (index < lines.length && isMarkdownTableRow(lines[index])) {
                    rows.add(parseMarkdownRow(lines[index]));
                    index++;
                }
                index--;
                addDocxMarkdownTable(document, rows, theme);
                continue;
            }
            int level = headingLevel(line);
            String text = level > 0 ? line.substring(level + 1).trim() : stripMarkdownQuote(line);
            if (text.isBlank()) continue;
            if (level == 1 && !skippedArtifactTitle) {
                skippedArtifactTitle = true;
                continue;
            }
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(code ? 20 : 70);
            paragraph.setSpacingBetween(1.10);
            if (level > 0) {
                setDocxOutlineLevel(paragraph, Math.min(2, level));
                paragraph.setKeepNext(true);
                addDocxMarkdownText(paragraph, text, level == 1 ? 16 : level == 2 ? 13 : 12, level == 1 ? theme.primaryHex() : theme.inkHex(), true);
            } else if (line.startsWith("- ") || line.startsWith("  - ")) {
                addDocxMarkdownText(paragraph, "- " + cleanMarkdownInline(line.replaceFirst("^\\s*-\\s+", "")), 11, theme.inkHex(), false);
            } else if (code) {
                addDocxText(paragraph, text, 10, theme.mutedHex(), false);
            } else {
                addDocxMarkdownText(paragraph, text, 11, theme.inkHex(), false);
            }
        }
    }

    private void addDocxMarkdownTable(XWPFDocument document, List<List<String>> rows, Theme theme) {
        if (rows.isEmpty() || rows.get(0).isEmpty()) return;
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        int[] widths = docxColumnWidths(rows, columns);
        XWPFTable table = document.createTable(1, columns);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            org.apache.poi.xwpf.usermodel.XWPFTableRow row = rowIndex == 0 ? table.getRow(0) : table.createRow();
            for (int column = 0; column < columns; column++) {
                String value = column < rows.get(rowIndex).size() ? rows.get(rowIndex).get(column) : "";
                row.getCell(column).setColor(rowIndex == 0 ? "E7EBEF" : "FFFFFF");
                setDocxCell(row.getCell(column), cleanMarkdownInline(value), rowIndex == 0 ? 10 : 10,
                        rowIndex == 0 ? theme.mutedHex() : theme.inkHex(), rowIndex == 0);
            }
        }
        table.getRow(0).setRepeatHeader(true);
        configureDocxTable(table, widths);
        document.createParagraph().setSpacingAfter(60);
    }

    private int[] docxColumnWidths(List<List<String>> rows, int columns) {
        double[] weights = new double[columns];
        for (int column = 0; column < columns; column++) {
            int longest = 8;
            for (List<String> row : rows) if (column < row.size()) longest = Math.max(longest, Math.min(72, cleanMarkdownInline(row.get(column)).length()));
            weights[column] = Math.sqrt(longest);
        }
        int minimum = columns <= 6 ? 720 : 480;
        int distributable = Math.max(0, 9360 - minimum * columns);
        double totalWeight = java.util.Arrays.stream(weights).sum();
        int[] widths = new int[columns];
        int used = 0;
        for (int column = 0; column < columns; column++) {
            widths[column] = column == columns - 1 ? 9360 - used
                    : minimum + (int) Math.round(distributable * weights[column] / Math.max(1.0, totalWeight));
            used += widths[column];
        }
        return widths;
    }

    private void addDocxMarkdownText(XWPFParagraph paragraph, String value, int size, String color, boolean defaultBold) {
        String text = stripMarkdownQuote(value);
        boolean bold = defaultBold;
        int cursor = 0;
        while (cursor < text.length()) {
            int marker = text.indexOf("**", cursor);
            if (marker < 0) {
                addDocxText(paragraph, cleanMarkdownInline(text.substring(cursor)), size, color, bold);
                return;
            }
            if (marker > cursor) addDocxText(paragraph, cleanMarkdownInline(text.substring(cursor, marker)), size, color, bold);
            bold = !bold;
            cursor = marker + 2;
        }
    }

    private void addDocxTraceability(XWPFDocument document, JsonNode canonical, Theme theme) {
        addDocxBand(document, "TRACEABILITY", theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(80);
        setDocxOutlineLevel(heading, 0);
        heading.setKeepNext(true);
        addDocxText(heading, "Traceability matrix", 16, theme.inkHex(), true);
        XWPFParagraph introduction = document.createParagraph();
        introduction.setSpacingAfter(140);
        addDocxText(introduction, "Each generated requirement is connected to the downstream design, API, and acceptance evidence used for review.", 10, theme.mutedHex(), false);

        int[] widths = {1320, 1180, 1140, 2160, 1760, 1800};
        XWPFTable table = document.createTable(1, widths.length);
        String[] headers = {"REQUIREMENT", "USE CASE", "ENTITY", "API OPERATION", "ACCEPTANCE", "SOURCE"};
        for (int index = 0; index < headers.length; index++) {
            XWPFTableCell cell = table.getRow(0).getCell(index);
            cell.setColor("E7EBEF");
            setDocxCell(cell, headers[index], 10, theme.mutedHex(), true);
        }
        table.getRow(0).setRepeatHeader(true);
        for (JsonNode requirement : canonical.path("requirements")) {
            org.apache.poi.xwpf.usermodel.XWPFTableRow row = table.createRow();
            setDocxCell(row.getCell(0), requirement.path("id").asText("Not recorded"), 10, theme.inkHex(), true);
            setDocxCell(row.getCell(1), valueOrDash(requirement.path("useCaseId").asText()), 10, theme.inkHex(), false);
            setDocxCell(row.getCell(2), valueOrDash(requirement.path("entityId").asText()), 10, theme.inkHex(), false);
            setDocxCell(row.getCell(3), valueOrDash(requirement.path("apiOperationId").asText()), 10, theme.inkHex(), false);
            setDocxCell(row.getCell(4), firstAcceptanceId(requirement), 10, theme.inkHex(), false);
            setDocxCell(row.getCell(5), valueOrDash(requirement.path("sourceKind").asText()), 10, theme.inkHex(), false);
        }
        configureDocxTable(table, widths);

        XWPFParagraph readingGuide = document.createParagraph();
        readingGuide.setSpacingBefore(120);
        readingGuide.setSpacingAfter(0);
        addDocxText(readingGuide, "Reading the matrix: requirement -> use case -> entity -> API operation -> acceptance criterion.", 10, theme.mutedHex(), false);
    }

    private void addDocxOpenApi(XWPFDocument document, String source, Theme theme) {
        addDocxBand(document, "OPENAPI CONTRACT", theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(80);
        setDocxOutlineLevel(heading, 0);
        heading.setKeepNext(true);
        addDocxText(heading, "OpenAPI contract", 16, theme.inkHex(), true);
        try {
            JsonNode contract = new ObjectMapper().readTree(source);
            JsonNode info = contract.path("info");
            XWPFParagraph introduction = document.createParagraph();
            introduction.setSpacingAfter(120);
            addDocxText(introduction, info.path("description").asText("Generated API contract."), 10, theme.mutedHex(), false);

            XWPFTable overview = document.createTable(2, 6);
            String[][] overviewRows = {
                    {"OPENAPI", contract.path("openapi").asText("Not declared"), "ENDPOINTS", String.valueOf(contract.path("paths").size()), "SCHEMAS", String.valueOf(contract.path("components").path("schemas").size())},
                    {"VERSION", info.path("version").asText("Not declared"), "AUTHENTICATION", contract.path("components").path("securitySchemes").isMissingNode() ? "Not declared" : "Declared", "STATUS", "Generated review contract"}
            };
            for (int rowIndex = 0; rowIndex < overviewRows.length; rowIndex++) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow row = overview.getRow(rowIndex);
                for (int cellIndex = 0; cellIndex < overviewRows[rowIndex].length; cellIndex++) {
                    boolean labelCell = cellIndex % 2 == 0;
                    row.getCell(cellIndex).setColor(labelCell ? "F4F6F8" : "FFFFFF");
                    setDocxCell(row.getCell(cellIndex), overviewRows[rowIndex][cellIndex], 10, labelCell ? theme.mutedHex() : theme.inkHex(), labelCell);
                }
            }
            configureDocxTable(overview, 900, 2220, 1050, 2220, 900, 2070);

            addDocxSubheading(document, "Endpoints", theme);
            contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation ->
                    addDocxEndpoint(document, operation.getKey().toUpperCase(Locale.ROOT), path.getKey(), operation.getValue(), theme)));

            JsonNode schemas = contract.path("components").path("schemas");
            if (schemas.isObject() && !schemas.isEmpty()) {
                addDocxSubheading(document, "Schemas", theme);
                XWPFTable schemaTable = document.createTable(1, 3);
                String[] headers = {"SCHEMA", "TYPE", "REQUIRED FIELDS"};
                for (int index = 0; index < headers.length; index++) {
                    schemaTable.getRow(0).getCell(index).setColor("E7EBEF");
                    setDocxCell(schemaTable.getRow(0).getCell(index), headers[index], 10, theme.mutedHex(), true);
                }
                schemaTable.getRow(0).setRepeatHeader(true);
                schemas.properties().forEach(schema -> {
                    org.apache.poi.xwpf.usermodel.XWPFTableRow row = schemaTable.createRow();
                    setDocxCell(row.getCell(0), schema.getKey(), 10, theme.inkHex(), true);
                    setDocxCell(row.getCell(1), schema.getValue().path("type").asText("object"), 10, theme.inkHex(), false);
                    setDocxCell(row.getCell(2), jsonValues(schema.getValue().path("required")), 10, theme.inkHex(), false);
                });
                configureDocxTable(schemaTable, 2700, 1500, 5160);
            }
        } catch (IOException exception) {
            XWPFParagraph fallback = document.createParagraph();
            fallback.setSpacingAfter(80);
            addDocxText(fallback, "The generated contract could not be parsed for the structured layout. The source contract follows.", 10, theme.mutedHex(), false);
            addDocxSection(document, "OpenAPI source", "```json\n" + source + "\n```", DocumentationExportStyle.defaults(), theme);
        }
    }

    private void addDocxEndpoint(XWPFDocument document, String method, String path, JsonNode operation, Theme theme) {
        XWPFTable endpoint = document.createTable(1, 2);
        endpoint.getRow(0).getCell(0).setColor("E7EBEF");
        endpoint.getRow(0).getCell(1).setColor("FFFFFF");
        setDocxCell(endpoint.getRow(0).getCell(0), method, 10, theme.inkHex(), true);
        setDocxCell(endpoint.getRow(0).getCell(1), path, 10, theme.inkHex(), true);
        configureDocxTable(endpoint, 1080, 8280);

        XWPFParagraph summary = document.createParagraph();
        summary.setSpacingBefore(50);
        summary.setSpacingAfter(30);
        addDocxText(summary, operation.path("summary").asText("No summary provided."), 11, theme.inkHex(), false);
        addDocxKeyValue(document, "Operation ID", operation.path("operationId").asText("Not declared"), theme);
        addDocxKeyValue(document, "Requirement", operation.path("x-velocira-requirement-id").asText("Not declared"), theme);
        addDocxKeyValue(document, "Responses", responseSummary(operation.path("responses")), theme);
        document.createParagraph().setSpacingAfter(70);
    }

    private void addDocxSubheading(XWPFDocument document, String value, Theme theme) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(150);
        heading.setSpacingAfter(80);
        addDocxText(heading, value, 12, theme.inkHex(), true);
    }

    private void addDocxKeyValue(XWPFDocument document, String label, String value, Theme theme) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(20);
        addDocxText(paragraph, label + ": ", 10, theme.mutedHex(), true);
        addDocxText(paragraph, value, 10, theme.inkHex(), false);
    }

    private void configureDocxTable(XWPFTable table, int... widths) {
        table.setWidth("9360");
        table.setCellMargins(80, 120, 80, 120);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl tableXml = table.getCTTbl();
        CTTblPr properties = tableXml.getTblPr();
        if (properties == null) properties = tableXml.addNewTblPr();
        CTTblWidth tableWidth = properties.getTblW();
        if (tableWidth == null) tableWidth = properties.addNewTblW();
        tableWidth.setW(BigInteger.valueOf(9360));
        tableWidth.setType(STTblWidth.DXA);
        CTTblWidth tableIndent = properties.getTblInd();
        if (tableIndent == null) tableIndent = properties.addNewTblInd();
        tableIndent.setW(BigInteger.valueOf(120));
        tableIndent.setType(STTblWidth.DXA);
        if (properties.getTblLayout() == null) properties.addNewTblLayout();
        properties.getTblLayout().setType(STTblLayoutType.FIXED);
        CTTblGrid grid = tableXml.getTblGrid();
        if (grid == null) grid = tableXml.addNewTblGrid();
        while (grid.sizeOfGridColArray() > widths.length) grid.removeGridCol(widths.length);
        for (int index = 0; index < widths.length; index++) {
            if (index >= grid.sizeOfGridColArray()) grid.addNewGridCol();
            grid.getGridColArray(index).setW(BigInteger.valueOf(widths[index]));
        }
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D6DAE1");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "E5E7EB");
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D6DAE1");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D6DAE1");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D6DAE1");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D6DAE1");
        for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
            for (int index = 0; index < Math.min(widths.length, row.getTableCells().size()); index++) {
                row.getCell(index).setWidth(String.valueOf(widths[index]));
                row.getCell(index).setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            }
        }
    }

    private String firstAcceptanceId(JsonNode requirement) {
        JsonNode criteria = requirement.path("acceptanceCriteria");
        return criteria.isArray() && !criteria.isEmpty() ? criteria.get(0).path("id").asText("Not recorded") : "Not recorded";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String jsonValues(JsonNode values) {
        List<String> items = new ArrayList<>();
        if (values.isArray()) values.forEach(value -> items.add(value.asText()));
        return items.isEmpty() ? "None declared" : String.join(", ", items);
    }

    private String responseSummary(JsonNode responses) {
        List<String> items = new ArrayList<>();
        responses.properties().forEach(response -> items.add(response.getKey() + " " + response.getValue().path("description").asText("")));
        return items.isEmpty() ? "None declared" : String.join("; ", items);
    }

    private void addDocxDiagram(XWPFDocument document, String title, byte[] png, Theme theme) throws IOException {
        addDocxBand(document, title.toUpperCase(Locale.ROOT), theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(80);
        heading.setKeepNext(true);
        setDocxOutlineLevel(heading, 0);
        addDocxText(heading, title, 16, theme.primaryHex(), true);
        XWPFParagraph diagram = document.createParagraph();
        diagram.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(png));
        if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
            throw new IOException("Unable to inspect rendered diagram dimensions.");
        }
        double widthInches = 6.5;
        double heightInches = widthInches * sourceImage.getHeight() / sourceImage.getWidth();
        if (heightInches > 7.2) {
            heightInches = 7.2;
            widthInches = heightInches * sourceImage.getWidth() / sourceImage.getHeight();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            // Apache POI's Units.toEMU(double) accepts points, not inches.
            diagram.createRun().addPicture(input, Document.PICTURE_TYPE_PNG, slug(title) + ".png",
                    Units.toEMU(widthInches * 72.0), Units.toEMU(heightInches * 72.0));
        } catch (InvalidFormatException exception) {
            throw new IOException("Unable to embed rendered diagram in DOCX.", exception);
        }
        XWPFParagraph caption = document.createParagraph();
        caption.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        caption.setSpacingAfter(120);
        addDocxText(caption, "Rendered from the canonical package model; editable Mermaid or PlantUML source is included when emitted.", 10, theme.mutedHex(), false);
    }

    private void addDocxText(XWPFParagraph paragraph, String text, int size, String color, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Calibri");
        run.setFontSize(size);
        run.setColor(color);
        run.setBold(bold);
    }

    private void addDocxPageBreak(XWPFDocument document) {
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private void setDocxOutlineLevel(XWPFParagraph paragraph, int level) {
        if (paragraph.getCTP().getPPr() == null) paragraph.getCTP().addNewPPr();
        if (paragraph.getCTP().getPPr().getOutlineLvl() == null) paragraph.getCTP().getPPr().addNewOutlineLvl();
        paragraph.getCTP().getPPr().getOutlineLvl().setVal(BigInteger.valueOf(Math.max(0, Math.min(8, level))));
    }

    private void addDocxBand(XWPFDocument document, String label, String fill) {
        XWPFParagraph band = document.createParagraph();
        band.setSpacingAfter(0);
        band.setKeepNext(true);
        addDocxText(band, label.replace('_', ' '), 8, "64748B", true);
    }

    private byte[] diagramPng(JsonNode canonical, DiagramKind kind, DocumentationExportStyle style) throws IOException {
        Theme theme = Theme.from(style.theme());
        int width = 1200;
        List<String> labels = new ArrayList<>();
        if (kind == DiagramKind.ERD) {
            for (JsonNode entity : canonical.path("entities")) labels.add(entity.path("name").asText("Entity"));
        } else if (kind == DiagramKind.WORKFLOW) {
            canonical.path("workflowSteps").forEach(step -> labels.add(step.asText("Workflow step")));
        } else if (kind == DiagramKind.CONTEXT) {
            canonical.path("actors").forEach(actor -> labels.add("Actor: " + actor.path("name").asText("Actor")));
            canonical.path("integrations").forEach(integration -> labels.add("External: " + integration.path("name").asText("System")));
        } else {
            for (JsonNode requirement : canonical.path("requirements")) {
                if ("FUNCTIONAL".equals(requirement.path("type").asText())) {
                    String useCaseId = requirement.path("useCaseId").asText(requirement.path("id").asText("Use case"));
                    List<String> requirementActors = new ArrayList<>();
                    requirement.path("actors").forEach(actor -> addMeaningfulName(requirementActors, actor.asText("")));
                    String actor = requirementActors.isEmpty() ? firstMeaningfulName(canonical.path("actors"), "Project user")
                            : String.join(", ", requirementActors.stream().limit(2).toList());
                    labels.add(actor + " -> " + useCaseId + " - " + requirement.path("title").asText("Confirmed capability"));
                }
            }
        }
        if (labels.isEmpty()) labels.add(switch (kind) {
            case ERD -> "No entities recorded";
            case WORKFLOW -> "Workflow steps require confirmation";
            case CONTEXT -> "Actors and external systems require confirmation";
            case USE_CASES -> "No functional use cases recorded";
        });
        int totalLabels = labels.size();
        List<String> displayLabels = labels;
        if (labels.size() > 8) {
            displayLabels = new ArrayList<>(labels.subList(0, 7));
            displayLabels.add("+" + (totalLabels - 7) + " additional items; see the emitted source/specification");
        }
        int items = displayLabels.size();
        boolean grid = kind == DiagramKind.ERD || kind == DiagramKind.CONTEXT;
        int rows = grid ? (int) Math.ceil(items / 2.0) : items;
        int height = Math.max(420, 180 + rows * (grid ? 120 : 82));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(theme.canvas());
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(theme.primary());
            graphics.fillRect(0, 0, width, 5);
            graphics.setColor(theme.ink());
            graphics.setFont(new Font("Arial", Font.BOLD, 26));
            graphics.drawString(switch (kind) {
                case ERD -> "Entity model";
                case CONTEXT -> "C4 system context";
                case WORKFLOW -> "Primary workflow";
                case USE_CASES -> "Use case map";
            }, 56, 58);
            graphics.setFont(new Font("Arial", Font.PLAIN, 14));
            graphics.setColor(theme.muted());
            graphics.drawString(switch (kind) {
                case ERD -> "Confirmed entities only; unsupported relationships are omitted.";
                case CONTEXT -> "Confirmed people and external systems around the product boundary.";
                case WORKFLOW -> "Confirmed workflow sequence; unresolved recovery remains labelled in the source.";
                case USE_CASES -> "Actor-to-capability links derived from reviewed functional requirements.";
            }, 56, 84);
            graphics.setStroke(new BasicStroke(2f));

            if (kind == DiagramKind.USE_CASES) {
                int boundaryX = 310;
                int boundaryY = 118;
                int boundaryHeight = Math.max(120, items * 82 + 36);
                graphics.setColor(new Color(226, 232, 240));
                graphics.drawRoundRect(boundaryX, boundaryY, 820, boundaryHeight, 14, 14);
                graphics.setColor(theme.muted());
                graphics.setFont(new Font("Arial", Font.BOLD, 12));
                graphics.drawString("SYSTEM CAPABILITIES", boundaryX + 22, boundaryY + 24);
                graphics.setColor(theme.ink());
                graphics.fillOval(126, boundaryY + boundaryHeight / 2 - 48, 26, 26);
                graphics.drawLine(139, boundaryY + boundaryHeight / 2 - 22, 139, boundaryY + boundaryHeight / 2 + 35);
                graphics.drawLine(114, boundaryY + boundaryHeight / 2, 164, boundaryY + boundaryHeight / 2);
                graphics.drawLine(139, boundaryY + boundaryHeight / 2 + 35, 114, boundaryY + boundaryHeight / 2 + 68);
                graphics.drawLine(139, boundaryY + boundaryHeight / 2 + 35, 164, boundaryY + boundaryHeight / 2 + 68);
                String actor = canonical.path("actors").isEmpty() ? "Project user" : canonical.path("actors").get(0).path("name").asText("Project user");
                graphics.setFont(new Font("Arial", Font.PLAIN, 14));
                drawCentered(graphics, ellipsize(actor, 22), 139, boundaryY + boundaryHeight / 2 + 94);
                for (int index = 0; index < items; index++) {
                    int y = boundaryY + 40 + index * 82;
                    graphics.setColor(theme.primary());
                    graphics.drawLine(176, y + 25, 422, y + 25);
                    graphics.setColor(theme.soft());
                    graphics.fillRoundRect(422, y, 610, 50, 10, 10);
                    graphics.setColor(theme.primary());
                    graphics.drawRoundRect(422, y, 610, 50, 10, 10);
                    graphics.setColor(theme.ink());
                    graphics.setFont(new Font("Arial", Font.BOLD, 15));
                    graphics.drawString(ellipsize(displayLabels.get(index), 56), 446, y + 31);
                }
            } else if (kind == DiagramKind.WORKFLOW) {
                for (int index = 0; index < items; index++) {
                    int y = 124 + index * 82;
                    if (index > 0) {
                        graphics.setColor(theme.primary());
                        graphics.drawLine(600, y - 31, 600, y);
                    }
                    graphics.setColor(theme.soft());
                    graphics.fillRoundRect(170, y, 860, 54, 10, 10);
                    graphics.setColor(theme.primary());
                    graphics.drawRoundRect(170, y, 860, 54, 10, 10);
                    graphics.setColor(theme.ink());
                    graphics.setFont(new Font("Arial", Font.BOLD, 15));
                    graphics.drawString((index + 1) + ". " + ellipsize(displayLabels.get(index), 78), 196, y + 33);
                }
            } else if (kind == DiagramKind.CONTEXT) {
                List<String> actors = new ArrayList<>();
                List<String> externals = new ArrayList<>();
                canonical.path("actors").forEach(actor -> actors.add(actor.path("name").asText("Actor")));
                canonical.path("integrations").forEach(integration -> externals.add(integration.path("name").asText("External system")));
                String system = canonical.path("project").path("name").asText("Product boundary");
                int centerY = height / 2;
                graphics.setColor(theme.primary());
                graphics.fillRoundRect(455, centerY - 58, 290, 116, 14, 14);
                int visibleActors = Math.min(4, actors.size());
                for (int index = 0; index < visibleActors; index++) {
                    int y = 120 + index * 78;
                    graphics.setColor(theme.soft());
                    graphics.fillRoundRect(60, y, 300, 54, 10, 10);
                    graphics.setColor(theme.primary());
                    graphics.drawRoundRect(60, y, 300, 54, 10, 10);
                    graphics.drawLine(360, y + 27, 455, centerY);
                    graphics.setColor(theme.ink());
                    graphics.setFont(new Font("Arial", Font.BOLD, 14));
                    graphics.drawString("Person: " + ellipsize(actors.get(index), 25), 78, y + 32);
                }
                if (actors.size() > visibleActors) {
                    graphics.setColor(theme.muted());
                    graphics.setFont(new Font("Arial", Font.PLAIN, 12));
                    graphics.drawString("+" + (actors.size() - visibleActors) + " additional actors", 78, 120 + visibleActors * 78 + 18);
                }
                int visibleExternals = Math.min(4, externals.size());
                for (int index = 0; index < visibleExternals; index++) {
                    int y = 120 + index * 78;
                    graphics.setColor(Color.WHITE);
                    graphics.fillRoundRect(840, y, 300, 54, 10, 10);
                    graphics.setColor(theme.muted());
                    graphics.drawRoundRect(840, y, 300, 54, 10, 10);
                    graphics.drawLine(745, centerY, 840, y + 27);
                    graphics.setColor(theme.ink());
                    graphics.setFont(new Font("Arial", Font.BOLD, 14));
                    graphics.drawString("External: " + ellipsize(externals.get(index), 23), 858, y + 32);
                }
                if (externals.size() > visibleExternals) {
                    graphics.setColor(theme.muted());
                    graphics.setFont(new Font("Arial", Font.PLAIN, 12));
                    graphics.drawString("+" + (externals.size() - visibleExternals) + " additional externals", 858, 120 + visibleExternals * 78 + 18);
                }
                if (externals.isEmpty()) {
                    graphics.setColor(theme.muted());
                    graphics.setFont(new Font("Arial", Font.PLAIN, 13));
                    graphics.drawString("No external system confirmed", 862, centerY + 4);
                }
                // Draw the compact system label after the connection lines so a long
                // project name remains legible at the boundary edge.
                graphics.setColor(Color.WHITE);
                graphics.setFont(new Font("Arial", Font.BOLD, 18));
                drawCentered(graphics, ellipsize(system, 22), 600, centerY - 4);
                graphics.setFont(new Font("Arial", Font.PLAIN, 13));
                drawCentered(graphics, "system boundary", 600, centerY + 24);
            } else {
                if (kind == DiagramKind.ERD) {
                    graphics.setColor(theme.muted());
                    graphics.setStroke(new BasicStroke(2f));
                    for (JsonNode relationship : canonical.path("erdRelationships")) {
                        int from = diagramEntityIndex(canonical, relationship.path("from").asText(), items);
                        int to = diagramEntityIndex(canonical, relationship.path("to").asText(), items);
                        if (from < 0 || to < 0) continue;
                        int fromX = (from % 2 == 0 ? 70 : 635) + 248;
                        int fromY = 124 + (from / 2) * 120 + 43;
                        int toX = (to % 2 == 0 ? 70 : 635) + 248;
                        int toY = 124 + (to / 2) * 120 + 43;
                        graphics.drawLine(fromX, fromY, toX, toY);
                        graphics.setFont(new Font("Arial", Font.PLAIN, 12));
                        graphics.drawString(ellipsize(relationship.path("label").asText("related"), 22),
                                (fromX + toX) / 2 + 6, (fromY + toY) / 2 - 5);
                    }
                }
                for (int index = 0; index < items; index++) {
                    int column = index % 2;
                    int row = index / 2;
                    int x = column == 0 ? 70 : 635;
                    int y = 124 + row * 120;
                    graphics.setColor(theme.soft());
                    graphics.fillRoundRect(x, y, 495, 86, 10, 10);
                    graphics.setColor(theme.primary());
                    graphics.drawRoundRect(x, y, 495, 86, 10, 10);
                    graphics.setColor(theme.ink());
                    graphics.setFont(new Font("Arial", Font.BOLD, 17));
                    graphics.drawString(ellipsize(displayLabels.get(index), 39), x + 22, y + 34);
                    graphics.setColor(theme.muted());
                    graphics.setFont(new Font("Arial", Font.PLAIN, 13));
                    graphics.drawString(kind == DiagramKind.ERD ? "id  /  uuid  /  primary key" : "confirmed context boundary", x + 22, y + 61);
                }
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private void drawCentered(Graphics2D graphics, String text, int centerX, int baseline) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - width / 2, baseline);
    }

    private String useCaseSvg(JsonNode canonical, Theme theme, DocumentationExportTemplate template) {
        StringBuilder nodes = new StringBuilder();
        int y = 154;
        int count = 0;
        for (JsonNode requirement : canonical.path("requirements")) {
            if (!"FUNCTIONAL".equals(requirement.path("type").asText())) continue;
            String useCaseId = requirement.path("useCaseId").asText("");
            String title = meaningfulName(requirement.path("title").asText(""));
            String text = useCaseId + " · " + (title == null ? requirement.path("id").asText("Confirmed capability") : title);
            List<String> actors = new ArrayList<>();
            requirement.path("actors").forEach(actorNode -> addMeaningfulName(actors, actorNode.asText("")));
            String rowActor = actors.isEmpty() ? firstMeaningfulName(canonical.path("actors"), "Project user")
                    : String.join(", ", actors.stream().limit(2).toList());
            nodes.append("<rect x=\"56\" y=\"").append(y + 2).append("\" width=\"142\" height=\"44\" rx=\"8\" fill=\"#FFFFFF\" stroke=\"#")
                    .append(theme.mutedHex()).append("\"/>")
                    .append("<text x=\"127\" y=\"").append(y + 29).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"12\" fill=\"#")
                    .append(theme.inkHex()).append("\">").append(escapeXml(ellipsize(rowActor, 20))).append("</text>")
                    .append("<line x1=\"198\" y1=\"").append(y + 24).append("\" x2=\"422\" y2=\"").append(y + 24)
                    .append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"1.5\"/>")
                    .append("<rect x=\"422\" y=\"").append(y).append("\" width=\"590\" height=\"48\" rx=\"9\" fill=\"")
                    .append("#").append(theme.softHex()).append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"1.5\"/>")
                    .append("<text x=\"446\" y=\"").append(y + 30).append("\" font-family=\"Arial\" font-size=\"14\" font-weight=\"bold\" fill=\"")
                    .append("#").append(theme.inkHex()).append("\">").append(escapeXml(ellipsize(text.trim(), 56))).append("</text>");
            y += 76;
            count++;
        }
        if (count == 0) nodes.append("<text x=\"422\" y=\"175\" font-family=\"Arial\" font-size=\"14\" fill=\"#").append(theme.mutedHex()).append("\">No functional use cases were recorded.</text>");
        int height = Math.max(330, y + 46);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"" + height + "\" viewBox=\"0 0 1100 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"5\" fill=\"#" + theme.primaryHex() + "\"/>"
                + "<text x=\"56\" y=\"58\" font-family=\"Arial\" font-size=\"26\" font-weight=\"bold\" fill=\"#" + theme.inkHex() + "\">Use case map</text>"
                + "<text x=\"56\" y=\"84\" font-family=\"Arial\" font-size=\"14\" fill=\"#" + theme.mutedHex() + "\">Actor-to-capability links derived from reviewed functional requirements.</text>"
                + "<rect x=\"310\" y=\"118\" width=\"760\" height=\"" + Math.max(116, count * 76 + 36) + "\" rx=\"12\" fill=\"none\" stroke=\"#CBD5E1\" stroke-width=\"1.5\"/>"
                + "<text x=\"332\" y=\"142\" font-family=\"Arial\" font-size=\"11\" font-weight=\"bold\" fill=\"#" + theme.mutedHex() + "\">SYSTEM CAPABILITIES</text>"
                + nodes + "</svg>";
    }

    private String erdSvg(JsonNode canonical, Theme theme, DocumentationExportTemplate template) {
        StringBuilder boxes = new StringBuilder();
        int count = 0;
        for (JsonNode entity : canonical.path("entities")) {
            String entityName = meaningfulName(entity.path("name").asText(""));
            if (entityName == null) continue;
            int column = count % 2;
            int row = count / 2;
            int x = column == 0 ? 56 : 548;
            int y = 124 + row * 112;
            boxes.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"456\" height=\"82\" rx=\"10\" fill=\"")
                    .append("#").append(theme.softHex()).append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"2\"/>")
                    .append("<text x=\"").append(x + 22).append("\" y=\"").append(y + 33).append("\" font-family=\"Arial\" font-size=\"16\" font-weight=\"bold\" fill=\"")
                    .append("#").append(theme.inkHex()).append("\">").append(escapeXml(ellipsize(entityName, 34))).append("</text>")
                    .append("<text x=\"").append(x + 22).append("\" y=\"").append(y + 59).append("\" font-family=\"Arial\" font-size=\"12\" fill=\"#").append(theme.mutedHex()).append("\">id  /  uuid  /  primary key</text>");
            count++;
        }
        StringBuilder relations = new StringBuilder();
        for (JsonNode relationship : canonical.path("erdRelationships")) {
            int from = diagramEntityIndex(canonical, relationship.path("from").asText(), count);
            int to = diagramEntityIndex(canonical, relationship.path("to").asText(), count);
            if (from < 0 || to < 0) continue;
            int fromX = (from % 2 == 0 ? 56 : 548) + 228;
            int fromY = 124 + (from / 2) * 112 + 41;
            int toX = (to % 2 == 0 ? 56 : 548) + 228;
            int toY = 124 + (to / 2) * 112 + 41;
            relations.append("<line x1=\"").append(fromX).append("\" y1=\"").append(fromY)
                    .append("\" x2=\"").append(toX).append("\" y2=\"").append(toY)
                    .append("\" stroke=\"#").append(theme.mutedHex()).append("\" stroke-width=\"2\"/>")
                    .append("<text x=\"").append((fromX + toX) / 2 + 5).append("\" y=\"").append((fromY + toY) / 2 - 5)
                    .append("\" font-family=\"Arial\" font-size=\"11\" fill=\"#").append(theme.mutedHex()).append("\">")
                    .append(escapeXml(ellipsize(relationship.path("label").asText("related"), 20))).append("</text>");
        }
        if (count == 0) boxes.append("<text x=\"56\" y=\"154\" font-family=\"Arial\" font-size=\"14\" fill=\"#").append(theme.mutedHex()).append("\">No entities were recorded.</text>");
        int height = Math.max(300, 166 + ((count + 1) / 2) * 112);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1060\" height=\"" + height + "\" viewBox=\"0 0 1060 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"5\" fill=\"#" + theme.primaryHex() + "\"/>"
                + "<text x=\"56\" y=\"58\" font-family=\"Arial\" font-size=\"26\" font-weight=\"bold\" fill=\"#" + theme.inkHex() + "\">Entity model</text>"
                + "<text x=\"56\" y=\"84\" font-family=\"Arial\" font-size=\"14\" fill=\"#" + theme.mutedHex() + "\">Entities and relationships recorded in the canonical SRS.</text>" + relations + boxes + "</svg>";
    }

    private int diagramEntityIndex(JsonNode canonical, String token, int limit) {
        String normalized = token.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("(^_|_$)", "").toUpperCase(Locale.ROOT);
        int index = 0;
        for (JsonNode entity : canonical.path("entities")) {
            String candidate = entity.path("name").asText().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("(^_|_$)", "").toUpperCase(Locale.ROOT);
            if (candidate.equals(normalized)) return index < limit ? index : -1;
            index++;
        }
        return -1;
    }

    private String contextSvg(JsonNode canonical, Theme theme) {
        List<String> actors = new ArrayList<>();
        List<String> externals = new ArrayList<>();
        canonical.path("actors").forEach(item -> addMeaningfulName(actors, item.path("name").asText("")));
        canonical.path("integrations").forEach(item -> addMeaningfulName(externals, item.path("name").asText("")));
        if (actors.isEmpty()) actors.add("Actors require confirmation");
        String system = canonical.path("project").path("name").asText("Product boundary");
        int rows = Math.max(actors.size(), Math.max(1, externals.size()));
        int height = Math.max(390, 180 + rows * 86);
        StringBuilder nodes = new StringBuilder();
        for (int index = 0; index < actors.size(); index++) {
            int y = 136 + index * 86;
            nodes.append("<rect x=\"55\" y=\"").append(y).append("\" width=\"290\" height=\"54\" rx=\"9\" fill=\"#").append(theme.softHex()).append("\" stroke=\"#").append(theme.primaryHex()).append("\"/>")
                    .append("<text x=\"76\" y=\"").append(y + 33).append("\" font-family=\"Arial\" font-size=\"14\" fill=\"#").append(theme.inkHex()).append("\">").append(escapeXml(ellipsize(actors.get(index), 34))).append("</text>")
                    .append("<line x1=\"345\" y1=\"").append(y + 27).append("\" x2=\"445\" y2=\"").append(height / 2).append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"1.5\"/>");
        }
        for (int index = 0; index < externals.size(); index++) {
            int y = 136 + index * 86;
            nodes.append("<rect x=\"755\" y=\"").append(y).append("\" width=\"290\" height=\"54\" rx=\"9\" fill=\"#FFFFFF\" stroke=\"#94A3B8\"/>")
                    .append("<text x=\"776\" y=\"").append(y + 33).append("\" font-family=\"Arial\" font-size=\"14\" fill=\"#").append(theme.inkHex()).append("\">").append(escapeXml(ellipsize(externals.get(index), 34))).append("</text>")
                    .append("<line x1=\"655\" y1=\"").append(height / 2).append("\" x2=\"755\" y2=\"").append(y + 27).append("\" stroke=\"#64748B\" stroke-width=\"1.5\"/>");
        }
        if (externals.isEmpty()) nodes.append("<text x=\"780\" y=\"").append(height / 2).append("\" font-family=\"Arial\" font-size=\"13\" fill=\"#").append(theme.mutedHex()).append("\">No external system confirmed</text>");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"" + height + "\" viewBox=\"0 0 1100 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"5\" fill=\"#" + theme.primaryHex() + "\"/>"
                + "<text x=\"56\" y=\"58\" font-family=\"Arial\" font-size=\"26\" font-weight=\"bold\" fill=\"#" + theme.inkHex() + "\">C4 system context</text>"
                + "<text x=\"56\" y=\"84\" font-family=\"Arial\" font-size=\"14\" fill=\"#" + theme.mutedHex() + "\">Confirmed people and external systems only; direction denotes interaction, not an invented protocol.</text>"
                + "<rect x=\"445\" y=\"" + (height / 2 - 52) + "\" width=\"210\" height=\"104\" rx=\"12\" fill=\"#" + theme.primaryHex() + "\"/>"
                + "<text x=\"550\" y=\"" + (height / 2 - 4) + "\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"17\" font-weight=\"bold\" fill=\"#FFFFFF\">" + escapeXml(ellipsize(system, 25)) + "</text>"
                + "<text x=\"550\" y=\"" + (height / 2 + 22) + "\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"12\" fill=\"#FFFFFF\">system boundary</text>" + nodes + "</svg>";
    }

    private String workflowSvg(JsonNode canonical, Theme theme) {
        List<String> steps = new ArrayList<>();
        canonical.path("workflowSteps").forEach(step -> addMeaningfulName(steps, step.asText("")));
        if (steps.isEmpty()) steps.add("Workflow sequence requires confirmation");
        int count = Math.min(steps.size(), 12);
        int height = Math.max(330, 150 + count * 76 + (steps.size() > count ? 34 : 0));
        StringBuilder nodes = new StringBuilder();
        for (int index = 0; index < count; index++) {
            int y = 118 + index * 76;
            if (index > 0) nodes.append("<line x1=\"550\" y1=\"").append(y - 22).append("\" x2=\"550\" y2=\"").append(y).append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"2\"/>");
            nodes.append("<rect x=\"145\" y=\"").append(y).append("\" width=\"810\" height=\"54\" rx=\"9\" fill=\"#").append(theme.softHex()).append("\" stroke=\"#").append(theme.primaryHex()).append("\" stroke-width=\"1.5\"/>")
                    .append("<text x=\"174\" y=\"").append(y + 33).append("\" font-family=\"Arial\" font-size=\"14\" font-weight=\"bold\" fill=\"#").append(theme.inkHex()).append("\">").append(index + 1).append(". ").append(escapeXml(ellipsize(steps.get(index), 86))).append("</text>");
        }
        if (steps.size() > count) nodes.append("<text x=\"550\" y=\"").append(132 + count * 76)
                .append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"13\" fill=\"#")
                .append(theme.mutedHex()).append("\">+").append(steps.size() - count)
                .append(" additional steps; see workflow specification</text>");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"" + height + "\" viewBox=\"0 0 1100 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"5\" fill=\"#" + theme.primaryHex() + "\"/>"
                + "<text x=\"56\" y=\"58\" font-family=\"Arial\" font-size=\"26\" font-weight=\"bold\" fill=\"#" + theme.inkHex() + "\">Primary workflow</text>"
                + "<text x=\"56\" y=\"84\" font-family=\"Arial\" font-size=\"14\" fill=\"#" + theme.mutedHex() + "\">Ordered steps from the governed workflow; recovery details remain in the workflow specification.</text>" + nodes + "</svg>";
    }

    private void addMeaningfulName(List<String> labels, String candidate) {
        String value = meaningfulName(candidate);
        if (value != null && !labels.contains(value)) labels.add(value);
    }

    private String firstMeaningfulName(JsonNode nodes, String fallback) {
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String value = meaningfulName(node.path("name").asText(""));
                if (value != null) return value;
            }
        }
        return fallback;
    }

    private String meaningfulName(String candidate) {
        if (candidate == null) return null;
        String value = candidate.replaceAll("\\s+", " ").trim();
        if (value.isBlank() || Set.of("null", "none", "n/a", "unknown", "undefined").contains(value.toLowerCase(Locale.ROOT))) return null;
        return value;
    }

    private String openApiYaml(ArtifactSnapshot openApi) {
        try {
            JsonNode contract = new ObjectMapper().readTree(openApi.sourceContent());
            return new ObjectMapper(new YAMLFactory()).writeValueAsString(contract);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the OpenAPI YAML contract.", exception);
        }
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') level++;
        return level > 0 && level < line.length() && line.charAt(level) == ' ' ? level : 0;
    }

    private boolean isMarkdownTableStart(String[] lines, int index) {
        return index + 1 < lines.length && isMarkdownTableRow(lines[index]) && isMarkdownTableSeparator(lines[index + 1]);
    }

    private boolean isMarkdownTableRow(String line) {
        return line != null && line.trim().startsWith("|") && line.trim().endsWith("|");
    }

    private boolean isMarkdownTableSeparator(String line) {
        if (!isMarkdownTableRow(line)) return false;
        List<String> cells = parseMarkdownRow(line);
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.trim().matches(":?-{3,}:?"));
    }

    private List<String> parseMarkdownRow(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] cells = trimmed.split("\\|", -1);
        List<String> values = new ArrayList<>();
        for (String cell : cells) values.add(cleanMarkdownInline(cell.trim()));
        return values;
    }

    private String stripMarkdownQuote(String value) {
        if (value == null) return "";
        return value.replaceFirst("^\\s*>\\s?", "");
    }

    private String cleanMarkdownInline(String value) {
        if (value == null) return "";
        return stripMarkdownQuote(value)
                .replaceAll("\\[([^]\\r\\n]+)]\\((https?://[^)]+)\\)", "$1 ($2)")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private List<String> wrap(String text, int width) {
        if (text == null || text.isBlank()) return List.of(" ");
        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > width) {
            int cut = remaining.lastIndexOf(' ', width);
            if (cut < 1) cut = width;
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines;
    }

    private String ellipsize(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private String pdfText(String value) {
        if (value == null) return "";
        // PDF exports use PDFBox's Standard 14 Helvetica fonts. Transliterate common
        // editorial characters before the final ASCII fallback so user-provided titles
        // and generated documentation stay readable instead of gaining question marks.
        String normalized = value
                .replace('\u00A0', ' ')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace("\u2026", "...")
                .replace('\u2022', '*')
                .replace("\u2192", "->")
                .replace("\u2190", "<-");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private enum DiagramKind { USE_CASES, CONTEXT, WORKFLOW, ERD }

    private record ArtifactSnapshot(String content, String sourceContent) { }

    private record PackageSnapshot(String projectName, int versionNumber, int srsVersionNumber, JsonNode canonicalModel,
                                   JsonNode validation, Map<DocumentationArtifactType, ArtifactSnapshot> artifacts) {
        static PackageSnapshot from(DocumentationPackageEntity documentationPackage, Map<DocumentationArtifactType, DocumentationArtifactEntity> source) {
            Map<DocumentationArtifactType, ArtifactSnapshot> artifacts = new EnumMap<>(DocumentationArtifactType.class);
            source.forEach((type, artifact) -> artifacts.put(type,
                    new ArtifactSnapshot(artifact.getContent() == null ? "" : artifact.getContent(),
                            artifact.getSourceContent() == null ? "" : artifact.getSourceContent())));
            return new PackageSnapshot(documentationPackage.getProject().getName(), documentationPackage.getVersionNumber(),
                    documentationPackage.getSrsVersion().getVersionNumber(), documentationPackage.getCanonicalModel(), documentationPackage.getValidationOutcome(), artifacts);
        }

        ArtifactSnapshot artifact(DocumentationArtifactType type) { return artifacts.getOrDefault(type, new ArtifactSnapshot("", "")); }

        boolean hasArtifact(DocumentationArtifactType type) {
            ArtifactSnapshot artifact = artifacts.get(type);
            return artifact != null && (!artifact.content().isBlank() || !artifact.sourceContent().isBlank());
        }

        String omissionReason(DocumentationArtifactType type) {
            for (JsonNode item : canonicalModel.path("documentPlan")) {
                if (type.name().equals(item.path("artifactType").asText())) return item.path("reason").asText("No canonical evidence supports this artifact.");
            }
            return "No canonical evidence supports this artifact.";
        }

        String generationMode() {
            return canonicalModel.path("generationManifest").path("mode").asText("STANDARD").toUpperCase(Locale.ROOT);
        }

        String validationLabel() {
            if (!validation.path("valid").asBoolean(false)) return "FAILED — structural issues recorded";
            if (!validation.path("approvalEligible").asBoolean(false)) return "NEEDS REVIEW — material gaps recorded";
            return "APPROVAL ELIGIBLE";
        }

        String combinedMarkdown() {
            StringBuilder content = new StringBuilder();
            for (DocumentationArtifactType type : DOCUMENT_ORDER) {
                ArtifactSnapshot artifact = artifact(type);
                if (artifact == null || (artifact.content().isBlank() && artifact.sourceContent().isBlank())) continue;
                if (!content.isEmpty()) content.append("\n\n---\n\n");
                if (type == DocumentationArtifactType.OPENAPI) {
                    content.append("# OpenAPI contract\n\n```json\n").append(artifact.sourceContent()).append("\n```");
                } else {
                    content.append(artifact.content().isBlank() ? artifact.sourceContent() : artifact.content());
                }
            }
            return content.toString();
        }
    }

    private record TemplateSpec(String coverline, String callout) {
        static TemplateSpec from(DocumentationExportTemplate template) {
            return switch (template) {
                case EXECUTIVE -> new TemplateSpec("Executive-ready brief for review and decision", "Clear hierarchy, concise evidence, and visual hand-off.");
                case TECHNICAL -> new TemplateSpec("Technical hand-off for architecture and implementation", "Traceable source detail, diagrams, and implementation context.");
                case MINIMAL -> new TemplateSpec("Focused reference for clean reading and print", "Restrained, high-readability documentation without visual noise.");
            };
        }
    }

    private record Theme(String primaryHex, String softHex, String inkHex, String mutedHex, String canvasHex) {
        static Theme from(DocumentationExportTheme theme) {
            return switch (theme) {
                case SIGNAL -> new Theme("475569", "F3F5F7", "18212B", "64748B", "FFFFFF");
                case COMMAND -> new Theme("334155", "F1F5F9", "111827", "64748B", "FFFFFF");
                case OCEAN -> new Theme("3F6473", "F1F6F8", "172B33", "64748B", "FFFFFF");
                case VIOLET -> new Theme("5B6070", "F4F5F7", "1F2430", "6B7280", "FFFFFF");
                case EMERALD -> new Theme("47665E", "F2F6F4", "172820", "64748B", "FFFFFF");
                case MONOCHROME -> new Theme("475569", "F3F4F6", "111827", "6B7280", "FFFFFF");
            };
        }

        Color primary() { return Color.decode("#" + primaryHex); }
        Color soft() { return Color.decode("#" + softHex); }
        Color ink() { return Color.decode("#" + inkHex); }
        Color muted() { return Color.decode("#" + mutedHex); }
        Color canvas() { return Color.decode("#" + canvasHex); }
    }

    private final class PdfComposer {
        private final PDDocument document;
        private final PackageSnapshot snapshot;
        private final DocumentationExportStyle style;
        private final Theme theme;
        private final TemplateSpec template;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private PDPage contentsPage;
        private float y;
        private int pageNumber;
        private final List<TocEntry> tocEntries = new ArrayList<>();

        private PdfComposer(PDDocument document, PackageSnapshot snapshot, DocumentationExportStyle style) {
            this.document = document;
            this.snapshot = snapshot;
            this.style = style;
            this.theme = Theme.from(style.theme());
            this.template = TemplateSpec.from(style.template());
        }

        void cover() throws IOException {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            pageNumber = 1;
            try (PDPageContentStream cover = new PDPageContentStream(document, page)) {
                cover.setNonStrokingColor(theme.canvas());
                cover.addRect(0, 0, PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight());
                cover.fill();
                cover.setNonStrokingColor(theme.primary());
                cover.addRect(0, 774, PDRectangle.LETTER.getWidth(), 18);
                cover.fill();
                cover.setNonStrokingColor(theme.ink());
                writeAt(cover, "VELOCIRA  |  DOCUMENTATION MEMO", bold, 10, 54, 720);
                writeAt(cover, pdfText(snapshot.projectName()), bold, 22, 54, 664);
                writeAt(cover, "Software delivery documentation brief", regular, 13, 54, 638);
                String[][] rows = {
                        {"SUBJECT", "Documentation package v" + snapshot.versionNumber()},
                        {"CANONICAL SOURCE", "SRS v" + snapshot.srsVersionNumber()},
                        {"GENERATION MODE", snapshot.generationMode()},
                        {"VALIDATION", snapshot.validationLabel()},
                        {"FORMAT", "standard_business_brief / memo_masthead"}
                };
                float rowY = 570;
                for (String[] row : rows) {
                    cover.setNonStrokingColor(theme.soft());
                    cover.addRect(54, rowY - 28, 504, 26);
                    cover.fill();
                    cover.setNonStrokingColor(theme.muted());
                    writeAt(cover, row[0], bold, 8, 66, rowY - 18);
                    cover.setNonStrokingColor(theme.ink());
                    writeAt(cover, pdfText(ellipsize(row[1], 66)), regular, 10.5f, 190, rowY - 18);
                    rowY -= 31;
                }
                writeAt(cover, "PURPOSE", bold, 9, 54, 376);
                writeAt(cover, "Provide one reviewable hand-off whose emitted artifacts, omissions, diagrams,", regular, 11, 54, 352);
                writeAt(cover, "contracts, and trace links resolve to the same canonical SRS snapshot.", regular, 11, 54, 334);
            }
        }

        void contents() throws IOException {
            newPage("Contents");
            contentsPage = document.getPage(document.getNumberOfPages() - 1);
            y = 0;
        }

        void section(String title, String markdown) throws IOException {
            startSection(title, 155);
            text(title, bold, 16, theme.primary(), 24);
            sectionRule();
            boolean code = false;
            boolean skippedArtifactTitle = false;
            String[] lines = markdown.split("\\r?\\n");
            for (int index = 0; index < lines.length; index++) {
                String raw = lines[index];
                if (raw.startsWith("```")) {
                    code = !code;
                    continue;
                }
                if (!code && isMarkdownTableStart(lines, index)) {
                    List<List<String>> rows = new ArrayList<>();
                    rows.add(parseMarkdownRow(raw));
                    index += 2;
                    while (index < lines.length && isMarkdownTableRow(lines[index])) {
                        rows.add(parseMarkdownRow(lines[index]));
                        index++;
                    }
                    index--;
                    markdownTable(rows, title);
                    continue;
                }
                int level = headingLevel(raw);
                String body = cleanMarkdownInline(level > 0 ? raw.substring(level + 1).trim() : raw);
                if (body.isBlank()) continue;
                if (level == 1 && !skippedArtifactTitle) {
                    skippedArtifactTitle = true;
                    continue;
                }
                if (level > 0) {
                    float headingSize = level == 1 ? 16 : level == 2 ? 13 : 12;
                    float headingLeading = level == 1 ? 22 : 18;
                    int headingWidth = level == 1 ? 50 : level == 2 ? 62 : 68;
                    for (String headingLine : wrap(body, headingWidth)) {
                        ensure(headingLeading + 8, title);
                        text(headingLine, bold, headingSize, level == 1 ? theme.primary() : theme.ink(), headingLeading);
                    }
                    continue;
                }
                boolean bullet = raw.startsWith("- ") || raw.startsWith("  - ");
                for (String line : wrap(bullet ? "- " + cleanMarkdownInline(raw.replaceFirst("^\\s*-\\s+", "")) : body, code ? 82 : 88)) {
                    ensure(code ? 14 : 17, title);
                    text(line, regular, code ? 10 : 10.5f, code ? theme.muted() : theme.ink(), code ? 12 : 15);
                }
            }
        }

        private void markdownTable(List<List<String>> rows, String section) throws IOException {
            if (rows.isEmpty() || rows.get(0).isEmpty()) return;
            int columns = Math.min(6, rows.stream().mapToInt(List::size).max().orElse(1));
            float tableWidth = 504;
            float[] columnWidths = pdfColumnWidths(rows, columns, tableWidth);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<List<String>> wrappedCells = new ArrayList<>();
                int lineCount = 1;
                for (int column = 0; column < columns; column++) {
                    String value = column < rows.get(rowIndex).size() ? rows.get(rowIndex).get(column) : "";
                    int charactersPerLine = Math.max(5, (int) ((columnWidths[column] - 10) / 5.2f));
                    List<String> wrapped = wrap(cleanMarkdownInline(value), charactersPerLine);
                    wrappedCells.add(wrapped);
                    lineCount = Math.max(lineCount, wrapped.size());
                }
                float rowHeight = Math.max(28, 10 + lineCount * 12);
                ensure(rowHeight + 5, section);
                stream.setNonStrokingColor(rowIndex == 0 ? new Color(231, 235, 239) : (rowIndex % 2 == 0 ? theme.soft() : theme.canvas()));
                stream.addRect(54, y - rowHeight, tableWidth, rowHeight);
                stream.fill();
                stream.setStrokingColor(new Color(214, 218, 225));
                stream.setLineWidth(0.5f);
                stream.addRect(54, y - rowHeight, tableWidth, rowHeight);
                float boundary = 54;
                for (int column = 1; column < columns; column++) {
                    boundary += columnWidths[column - 1];
                    stream.moveTo(boundary, y);
                    stream.lineTo(boundary, y - rowHeight);
                }
                stream.stroke();
                stream.setNonStrokingColor(rowIndex == 0 ? theme.muted() : theme.ink());
                float columnX = 59;
                for (int column = 0; column < columns; column++) {
                    List<String> wrapped = wrappedCells.get(column);
                    for (int lineIndex = 0; lineIndex < wrapped.size(); lineIndex++) {
                        int charactersPerLine = Math.max(5, (int) ((columnWidths[column] - 10) / 5.2f));
                        writeAt(stream, pdfText(ellipsize(wrapped.get(lineIndex), charactersPerLine)), rowIndex == 0 ? bold : regular,
                                10, columnX, y - 17 - lineIndex * 12);
                    }
                    columnX += columnWidths[column];
                }
                y -= rowHeight;
            }
            y -= 8;
        }

        private float[] pdfColumnWidths(List<List<String>> rows, int columns, float totalWidth) {
            double[] weights = new double[columns];
            for (int column = 0; column < columns; column++) {
                int longest = 8;
                for (List<String> row : rows) if (column < row.size()) longest = Math.max(longest, Math.min(72, cleanMarkdownInline(row.get(column)).length()));
                weights[column] = Math.sqrt(longest);
            }
            float minimum = columns <= 6 ? 42 : 30;
            float distributable = Math.max(0, totalWidth - minimum * columns);
            double weightTotal = java.util.Arrays.stream(weights).sum();
            float[] widths = new float[columns];
            float used = 0;
            for (int column = 0; column < columns; column++) {
                widths[column] = column == columns - 1 ? totalWidth - used
                        : minimum + (float) (distributable * weights[column] / Math.max(1.0, weightTotal));
                used += widths[column];
            }
            return widths;
        }

        void traceability(JsonNode canonical) throws IOException {
            startSection("Traceability", 155);
            text("Traceability matrix", bold, 16, theme.ink(), 24);
            text("Requirement-to-delivery links from the reviewed package snapshot.", regular, 10.5f, theme.muted(), 20);
            for (JsonNode requirement : canonical.path("requirements")) {
                traceCard(requirement);
            }
            if (canonical.path("requirements").isEmpty()) {
                text("No traceability records were generated for this package.", regular, 10.5f, theme.muted(), 16);
            }
        }

        void openApi(String source) throws IOException {
            // OpenAPI is a compact standalone deliverable. Starting it on a
            // fresh page keeps all endpoint cards together instead of leaving
            // one card on a nearly empty trailing page.
            newPage("OpenAPI contract");
            PDPage page = document.getPage(document.getNumberOfPages() - 1);
            tocEntries.add(new TocEntry("OpenAPI contract", pageNumber, page));
            text("OpenAPI contract", bold, 16, theme.ink(), 24);
            try {
                JsonNode contract = new ObjectMapper().readTree(source);
                JsonNode info = contract.path("info");
                text(info.path("title").asText("Generated API"), regular, 10, theme.muted(), 16);
                apiOverview(contract, info);
                text("Endpoints", bold, 12, theme.ink(), 22);
                contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
                    try {
                        endpointCard(operation.getKey().toUpperCase(Locale.ROOT), path.getKey(), operation.getValue());
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
                JsonNode schemas = contract.path("components").path("schemas");
                if (schemas.isObject() && !schemas.isEmpty()) {
                    ensure(42, "OpenAPI contract");
                    text("Schemas", bold, 12, theme.ink(), 20);
                    schemas.properties().forEach(schema -> {
                        try {
                            schemaRow(schema.getKey(), schema.getValue());
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
                }
            } catch (IllegalStateException exception) {
                if (exception.getCause() instanceof IOException ioException) throw ioException;
                throw exception;
            } catch (IOException exception) {
                text("The structured contract preview could not be generated. The included OpenAPI source remains authoritative.", regular, 10.5f, theme.muted(), 16);
            }
        }

        private void apiOverview(JsonNode contract, JsonNode info) throws IOException {
            ensure(66, "OpenAPI contract");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 48, 504, 42);
            stream.fill();
            stream.setNonStrokingColor(theme.muted());
            writeAt(stream, "OPENAPI " + contract.path("openapi").asText("Not declared"), bold, 10, 66, y - 19);
            writeAt(stream, "VERSION " + info.path("version").asText("Not declared"), bold, 10, 214, y - 19);
            writeAt(stream, "ENDPOINTS " + contract.path("paths").size(), bold, 10, 358, y - 19);
            writeAt(stream, contract.path("components").path("securitySchemes").isMissingNode() ? "AUTH: NOT DECLARED" : "AUTH: DECLARED", regular, 10, 66, y - 37);
            y -= 62;
        }

        private void traceCard(JsonNode requirement) throws IOException {
            ensure(112, "Traceability");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 98, 504, 92);
            stream.fill();
            stream.setNonStrokingColor(theme.ink());
            writeAt(stream, pdfText(requirement.path("id").asText("Requirement")), bold, 10, 66, y - 20);
            stream.setNonStrokingColor(theme.muted());
            writeAt(stream, "USE CASE  " + pdfText(valueOrDash(requirement.path("useCaseId").asText())), regular, 10, 66, y - 42);
            writeAt(stream, "ENTITY  " + pdfText(valueOrDash(requirement.path("entityId").asText())), regular, 10, 296, y - 42);
            writeAt(stream, "API  " + pdfText(ellipsize(valueOrDash(requirement.path("apiOperationId").asText()), 31)), regular, 10, 66, y - 63);
            writeAt(stream, "ACCEPTANCE  " + pdfText(ellipsize(firstAcceptanceId(requirement), 28)), regular, 10, 296, y - 63);
            writeAt(stream, "SOURCE  " + pdfText(valueOrDash(requirement.path("sourceKind").asText())), regular, 10, 66, y - 84);
            y -= 112;
        }

        private void endpointCard(String method, String path, JsonNode operation) throws IOException {
            ensure(116, "OpenAPI contract");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 104, 504, 98);
            stream.fill();
            stream.setNonStrokingColor(theme.ink());
            writeAt(stream, method + "  " + pdfText(path), bold, 10, 66, y - 20);
            stream.setNonStrokingColor(theme.muted());
            List<String> summary = wrap(pdfText(operation.path("summary").asText("No summary provided.")), 72);
            writeAt(stream, summary.get(0), regular, 10, 66, y - 42);
            if (summary.size() > 1) writeAt(stream, summary.get(1), regular, 10, 66, y - 57);
            writeAt(stream, "OPERATION  " + pdfText(ellipsize(operation.path("operationId").asText("Not declared"), 33)), regular, 10, 66, y - 79);
            writeAt(stream, "REQUIREMENT  " + pdfText(ellipsize(operation.path("x-velocira-requirement-id").asText("Not declared"), 25)), regular, 10, 298, y - 79);
            y -= 116;
        }

        private void schemaRow(String name, JsonNode schema) throws IOException {
            ensure(40, "OpenAPI contract");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 30, 504, 25);
            stream.fill();
            stream.setNonStrokingColor(theme.ink());
            writeAt(stream, pdfText(name), bold, 10, 66, y - 20);
            stream.setNonStrokingColor(theme.muted());
            writeAt(stream, pdfText(schema.path("type").asText("object")), regular, 10, 226, y - 20);
            writeAt(stream, pdfText(ellipsize(jsonValues(schema.path("required")), 38)), regular, 10, 320, y - 20);
            y -= 36;
        }

        void diagram(String title, byte[] png) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png, slug(title) + ".png");
            float width = 504;
            float height = width * image.getHeight() / image.getWidth();
            float maximumHeight = 430;
            if (height > maximumHeight) {
                height = maximumHeight;
                width = height * image.getWidth() / image.getHeight();
            }
            startSection(title, Math.min(610, height + 110));
            text(title, bold, 16, theme.primary(), 24);
            sectionRule();
            text("Rendered from the canonical package model; editable source is included when emitted.", regular, 10.5f, theme.muted(), 18);
            ensure(height + 12, title);
            stream.drawImage(image, 54 + (504 - width) / 2, y - height, width, height);
            y -= height + 18;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
            renderContentsAndOutline();
        }

        private void startSection(String title, float minimumSpace) throws IOException {
            if (stream == null || y - minimumSpace < 56) newPage(title);
            else y -= 18;
            PDPage page = document.getPage(document.getNumberOfPages() - 1);
            tocEntries.add(new TocEntry(title, pageNumber, page));
        }

        private void renderContentsAndOutline() throws IOException {
            if (contentsPage == null) return;
            stream = new PDPageContentStream(document, contentsPage, PDPageContentStream.AppendMode.APPEND, true, true);
            y = 720;
            text("Contents", bold, 20, theme.primary(), 28);
            text("Page references and PDF bookmarks resolve to the generated section starts.", regular, 10.5f, theme.muted(), 24);
            for (int index = 0; index < tocEntries.size(); index++) {
                TocEntry entry = tocEntries.get(index);
                tocItem(String.format("%02d", index + 1), entry.title(), entry.pageNumber());
            }
            callout("Package validation", snapshot.validationLabel());
            stream.close();
            stream = null;

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            for (TocEntry entry : tocEntries) {
                PDOutlineItem item = new PDOutlineItem();
                item.setTitle(pdfText(entry.title()));
                PDPageFitWidthDestination destination = new PDPageFitWidthDestination();
                destination.setPage(entry.page());
                item.setDestination(destination);
                outline.addLast(item);
            }
            outline.openNode();
        }

        private void newPage(String section) throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            pageNumber++;
            stream = new PDPageContentStream(document, page);
            stream.setNonStrokingColor(theme.canvas());
            stream.addRect(0, 0, PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight());
            stream.fill();
            stream.setNonStrokingColor(theme.primary());
            stream.addRect(0, 774, PDRectangle.LETTER.getWidth(), 3);
            stream.fill();
            stream.setNonStrokingColor(theme.muted());
            writeAt(stream, "VELOCIRA  |  " + pdfText(snapshot.projectName()), regular, 8, 54, 755);
            String compactSection = section.replace(" (continued)", " (cont.)");
            writeRightAligned(stream, pdfText(ellipsize(compactSection, 36)), regular, 8, PDRectangle.LETTER.getWidth() - 54, 755);
            writeRightAligned(stream, "Page " + pageNumber, regular, 8, PDRectangle.LETTER.getWidth() - 54, 34);
            y = 720;
        }

        private void callout(String label, String body) throws IOException {
            ensure(62, "Contents");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 54, 504, 48);
            stream.fill();
            y -= 16;
            text(label.toUpperCase(Locale.ROOT), bold, 8, theme.primary(), 13);
            text(body, regular, 10.5f, theme.ink(), 16);
            y -= 7;
        }

        private void tocItem(String number, String title, int destinationPage) throws IOException {
            ensure(34, "Contents");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 24, 504, 22);
            stream.fill();
            stream.setNonStrokingColor(theme.primary());
            stream.addRect(54, y - 24, 42, 22);
            stream.fill();
            stream.setNonStrokingColor(Color.WHITE);
            writeAt(stream, number, bold, 8, 66, y - 15);
            stream.setNonStrokingColor(theme.ink());
            writeAt(stream, pdfText(ellipsize(title, 52)), bold, 10, 112, y - 15);
            writeRightAligned(stream, String.valueOf(destinationPage), bold, 10, 542, y - 15);
            y -= 29;
        }

        private void sectionRule() throws IOException {
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y + 5, 504, 3);
            stream.fill();
            stream.setNonStrokingColor(theme.primary());
            stream.addRect(54, y + 5, 96, 3);
            stream.fill();
            y -= 9;
        }

        private void text(String value, PDType1Font font, float size, Color color, float leading) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, size);
            stream.newLineAtOffset(54, y);
            stream.showText(pdfText(value == null ? "" : value));
            stream.endText();
            y -= leading;
        }

        private void ensure(float needed, String section) throws IOException {
            if (y - needed < 56) newPage(section + " (continued)");
        }

        private void writeAt(PDPageContentStream target, String value, PDType1Font font, float size, float x, float targetY) throws IOException {
            target.beginText();
            target.setFont(font, size);
            target.newLineAtOffset(x, targetY);
            target.showText(pdfText(value));
            target.endText();
        }

        private void writeRightAligned(PDPageContentStream target, String value, PDType1Font font, float size, float rightX, float targetY) throws IOException {
            float width = font.getStringWidth(pdfText(value)) / 1000f * size;
            writeAt(target, value, font, size, rightX - width, targetY);
        }

        private record TocEntry(String title, int pageNumber, PDPage page) { }
    }

    public record RenderedExport(String filename, String contentType, byte[] content) { }
}
