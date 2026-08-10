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
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBackground;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Renders every immutable export from the same package snapshot. Styled document formats
 * share a small document model so PDF, DOCX, ZIP and visual diagram assets remain aligned.
 */
@Component
public class DocumentationExportRenderer {

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
                        "application/yaml; charset=utf-8", openApiYaml(snapshot.artifact(DocumentationArtifactType.OPENAPI)).getBytes(StandardCharsets.UTF_8));
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
                    useCaseSvg(snapshot.canonicalModel(), theme, style.template()).getBytes(StandardCharsets.UTF_8));
            case ERD -> new RenderedExport("entity-relationship-diagram.svg", "image/svg+xml; charset=utf-8",
                    erdSvg(snapshot.canonicalModel(), theme, style.template()).getBytes(StandardCharsets.UTF_8));
            default -> new RenderedExport(artifactType.name().toLowerCase(Locale.ROOT) + ".md", "text/markdown; charset=utf-8",
                    snapshot.artifact(artifactType).sourceContent().getBytes(StandardCharsets.UTF_8));
        };
    }

    private RenderedExport sourceExport(String projectSlug, PackageSnapshot snapshot, DocumentationArtifactType type,
                                        String extension, String contentType) {
        return new RenderedExport(projectSlug + "-v" + snapshot.versionNumber() + extension, contentType,
                snapshot.artifact(type).sourceContent().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] zip(PackageSnapshot snapshot, DocumentationExportStyle style) throws IOException {
        Theme theme = Theme.from(style.theme());
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "README.md", readme(snapshot, style));
            put(zip, "srs.md", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent());
            put(zip, "use-cases.md", snapshot.artifact(DocumentationArtifactType.USE_CASES).content());
            put(zip, "diagrams/use-cases.puml", snapshot.artifact(DocumentationArtifactType.USE_CASES).sourceContent());
            put(zip, "diagrams/use-cases.svg", useCaseSvg(snapshot.canonicalModel(), theme, style.template()));
            put(zip, "erd.md", snapshot.artifact(DocumentationArtifactType.ERD).content());
            put(zip, "diagrams/erd.mmd", snapshot.artifact(DocumentationArtifactType.ERD).sourceContent());
            put(zip, "diagrams/erd.svg", erdSvg(snapshot.canonicalModel(), theme, style.template()));
            put(zip, "openapi.json", snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent());
            put(zip, "openapi.yaml", openApiYaml(snapshot.artifact(DocumentationArtifactType.OPENAPI)));
            put(zip, "traceability.md", snapshot.artifact(DocumentationArtifactType.TRACEABILITY).sourceContent());
            put(zip, "canonical-model.json", snapshot.canonicalModel().toPrettyString());
            put(zip, "validation.json", snapshot.validation().toPrettyString());
            put(zip, "documentation-package.docx", docx(snapshot, style));
            put(zip, "documentation-package.pdf", pdf(snapshot, style));
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private String readme(PackageSnapshot snapshot, DocumentationExportStyle style) {
        return "# " + snapshot.projectName() + " documentation package\n\n"
                + "Package v" + snapshot.versionNumber() + " is generated from SRS v" + snapshot.srsVersionNumber() + ".\n\n"
                + "## Presentation\n\n"
                + "- Template: " + style.template() + "\n"
                + "- Theme: " + style.theme() + "\n"
                + "- Layout: " + style.layout() + "\n\n"
                + "## Included files\n\n"
                + "- `documentation-package.pdf` - styled, print-ready package with visual diagrams.\n"
                + "- `documentation-package.docx` - editable Word package with the same visual system.\n"
                + "- `diagrams/*.svg` - themed visual diagrams; `.mmd` and `.puml` remain faithful source files.\n"
                + "- `openapi.*`, `srs.md`, `use-cases.md`, and `traceability.md` - source deliverables.\n";
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
            PdfComposer composer = new PdfComposer(document, snapshot, style);
            composer.cover();
            composer.contents();
            composer.section("System requirements", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent());
            composer.diagram("Use case map", diagramPng(snapshot.canonicalModel(), DiagramKind.USE_CASES, style));
            composer.section("Use cases", snapshot.artifact(DocumentationArtifactType.USE_CASES).content());
            composer.diagram("Entity relationship diagram", diagramPng(snapshot.canonicalModel(), DiagramKind.ERD, style));
            composer.section("Traceability", snapshot.artifact(DocumentationArtifactType.TRACEABILITY).sourceContent());
            composer.section("OpenAPI contract", "```json\n" + snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent() + "\n```");
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
            configureDocxFurniture(document, snapshot, style, theme);
            addDocxCover(document, snapshot, style, theme, template);
            addDocxContents(document, snapshot, theme);
            addDocxSection(document, "System requirements", snapshot.artifact(DocumentationArtifactType.SRS).sourceContent(), style, theme);
            addDocxDiagram(document, "Use case map", diagramPng(snapshot.canonicalModel(), DiagramKind.USE_CASES, style), theme);
            addDocxSection(document, "Use cases", snapshot.artifact(DocumentationArtifactType.USE_CASES).content(), style, theme);
            addDocxDiagram(document, "Entity relationship diagram", diagramPng(snapshot.canonicalModel(), DiagramKind.ERD, style), theme);
            addDocxSection(document, "Traceability", snapshot.artifact(DocumentationArtifactType.TRACEABILITY).sourceContent(), style, theme);
            addDocxSection(document, "OpenAPI contract", "```json\n" + snapshot.artifact(DocumentationArtifactType.OPENAPI).sourceContent() + "\n```", style, theme);
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
        margins.setTop(BigInteger.valueOf(1_080));
        margins.setRight(BigInteger.valueOf(1_080));
        margins.setBottom(BigInteger.valueOf(1_080));
        margins.setLeft(BigInteger.valueOf(1_080));
        margins.setHeader(BigInteger.valueOf(720));
        margins.setFooter(BigInteger.valueOf(720));
        margins.setGutter(BigInteger.ZERO);
    }

    private void configureDocxFurniture(XWPFDocument document, PackageSnapshot snapshot, DocumentationExportStyle style, Theme theme) {
        if (style.theme() == DocumentationExportTheme.COMMAND) {
            CTBackground background = document.getDocument().isSetBackground()
                    ? document.getDocument().getBackground()
                    : document.getDocument().addNewBackground();
            background.setColor(theme.canvasHex());
        }
        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph headerParagraph = header.createParagraph();
        headerParagraph.setSpacingAfter(0);
        XWPFRun headerRun = headerParagraph.createRun();
        headerRun.setText("VELOCIRA  |  " + snapshot.projectName() + "  |  " + style.template().name());
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
        int spacing = style.layout() == DocumentationExportLayout.PRESENTATION ? 1200 : style.layout() == DocumentationExportLayout.COMPACT ? 240 : 640;
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(spacing);
        addDocxBand(document, "VELOCIRA  |  DOCUMENTATION PACKAGE", theme.primaryHex());

        XWPFParagraph title = document.createParagraph();
        title.setSpacingAfter(160);
        addDocxText(title, snapshot.projectName(), style.layout() == DocumentationExportLayout.PRESENTATION ? 30 : 26, theme.inkHex(), true);
        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setSpacingAfter(380);
        addDocxText(subtitle, template.coverline(), 14, theme.mutedHex(), false);

        XWPFTable metadata = document.createTable(4, 2);
        metadata.setWidth("9360");
        addMetadataRow(metadata.getRow(0), "Package", "v" + snapshot.versionNumber(), theme);
        addMetadataRow(metadata.getRow(1), "Source SRS", "v" + snapshot.srsVersionNumber(), theme);
        addMetadataRow(metadata.getRow(2), "Presentation", style.template() + " / " + style.theme() + " / " + style.layout(), theme);
        addMetadataRow(metadata.getRow(3), "Purpose", template.callout(), theme);

        XWPFTable callout = document.createTable(1, 1);
        callout.setWidth("9360");
        callout.getRow(0).getCell(0).setColor(theme.softHex());
        setDocxCell(callout.getRow(0).getCell(0), "REVIEW PACKAGE  |  This package keeps requirements, diagrams, APIs, and traceability connected to the same reviewed project snapshot.", 10, theme.inkHex(), false);
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
        paragraph.setSpacingAfter(40);
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
        addDocxText(heading, "Contents", 17, theme.primaryHex(), true);
        String[] sections = {"01  System requirements", "02  Use case map", "03  Use cases", "04  Entity relationship diagram", "05  Traceability", "06  OpenAPI contract"};
        for (String section : sections) {
            XWPFParagraph item = document.createParagraph();
            item.setSpacingAfter(70);
            addDocxText(item, section, 10, theme.inkHex(), false);
        }
        XWPFParagraph callout = document.createParagraph();
        callout.setSpacingBefore(160);
        addDocxText(callout, "Review signal: " + snapshot.validation().path("valid").asBoolean(false), 10, theme.primaryHex(), true);
    }

    private void addDocxSection(XWPFDocument document, String title, String markdown, DocumentationExportStyle style, Theme theme) {
        addDocxPageBreak(document);
        addDocxBand(document, title.toUpperCase(Locale.ROOT), theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(style.layout() == DocumentationExportLayout.PRESENTATION ? 280 : 180);
        heading.setSpacingAfter(140);
        addDocxText(heading, title, 17, theme.primaryHex(), true);

        boolean code = false;
        for (String raw : markdown.split("\\r?\\n")) {
            String line = raw == null ? "" : raw;
            if (line.startsWith("```")) {
                code = !code;
                continue;
            }
            int level = headingLevel(line);
            String text = level > 0 ? line.substring(level + 1).trim() : line;
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(code ? 20 : style.layout() == DocumentationExportLayout.COMPACT ? 50 : 80);
            if (level > 0) {
                addDocxText(paragraph, text, level == 1 ? 14 : level == 2 ? 12 : 11, level == 1 ? theme.primaryHex() : theme.inkHex(), true);
            } else if (line.startsWith("- ") || line.startsWith("  - ")) {
                addDocxText(paragraph, "- " + line.replaceFirst("^\\s*-\\s+", ""), 10, theme.inkHex(), false);
            } else if (code) {
                addDocxText(paragraph, text, 8, theme.mutedHex(), false);
            } else {
                addDocxText(paragraph, text.isBlank() ? " " : text, 10, theme.inkHex(), false);
            }
        }
    }

    private void addDocxDiagram(XWPFDocument document, String title, byte[] png, Theme theme) throws IOException {
        addDocxPageBreak(document);
        addDocxBand(document, title.toUpperCase(Locale.ROOT), theme.primaryHex());
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(240);
        heading.setSpacingAfter(100);
        addDocxText(heading, title, 17, theme.primaryHex(), true);
        XWPFParagraph diagram = document.createParagraph();
        diagram.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            diagram.createRun().addPicture(input, Document.PICTURE_TYPE_PNG, slug(title) + ".png", Units.toEMU(6.25), Units.toEMU(3.05));
        } catch (InvalidFormatException exception) {
            throw new IOException("Unable to embed rendered diagram in DOCX.", exception);
        }
        XWPFParagraph caption = document.createParagraph();
        caption.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        caption.setSpacingAfter(120);
        addDocxText(caption, "Themed visual representation. Original Mermaid and PlantUML source are included in the ZIP package.", 8, theme.mutedHex(), false);
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

    private void addDocxBand(XWPFDocument document, String label, String fill) {
        XWPFTable band = document.createTable(1, 1);
        band.setWidth("9360");
        XWPFTableCell cell = band.getRow(0).getCell(0);
        cell.setColor(fill);
        setDocxCell(cell, label, 8, "FFFFFF", true);
    }

    private byte[] diagramPng(JsonNode canonical, DiagramKind kind, DocumentationExportStyle style) throws IOException {
        Theme theme = Theme.from(style.theme());
        int width = 1100;
        List<String> labels = new ArrayList<>();
        if (kind == DiagramKind.ERD) {
            for (JsonNode entity : canonical.path("entities")) labels.add(entity.path("name").asText("Entity"));
        } else {
            for (JsonNode requirement : canonical.path("requirements")) {
                if ("FUNCTIONAL".equals(requirement.path("type").asText())) labels.add(requirement.path("useCaseId").asText(requirement.path("id").asText("Use case")));
            }
        }
        if (labels.isEmpty()) labels.add(kind == DiagramKind.ERD ? "No entities recorded" : "No functional use cases recorded");
        int rows = Math.min(labels.size(), 6);
        int height = Math.max(420, 140 + rows * 100);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(theme.canvas());
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(theme.primary());
            graphics.fillRect(0, 0, width, 18);
            graphics.setColor(theme.ink());
            graphics.setFont(new Font("Arial", Font.BOLD, 28));
            graphics.drawString(kind == DiagramKind.ERD ? "Entity relationship diagram" : "Use case map", 56, 66);
            graphics.setFont(new Font("Arial", Font.PLAIN, 15));
            graphics.setColor(theme.muted());
            graphics.drawString("Visual export generated from the reviewed package snapshot", 56, 94);
            graphics.setStroke(new BasicStroke(3f));
            for (int index = 0; index < rows; index++) {
                int y = 132 + index * 90;
                graphics.setColor(theme.soft());
                if (kind == DiagramKind.ERD) {
                    graphics.fillRoundRect(74, y, 330, 56, 12, 12);
                    graphics.setColor(theme.primary());
                    graphics.drawRoundRect(74, y, 330, 56, 12, 12);
                } else {
                    graphics.fillOval(74, y, 410, 56);
                    graphics.setColor(theme.primary());
                    graphics.drawOval(74, y, 410, 56);
                }
                graphics.setColor(theme.ink());
                graphics.setFont(new Font("Arial", Font.BOLD, 16));
                graphics.drawString(ellipsize(labels.get(index), 34), 96, y + 34);
                graphics.setColor(theme.primary());
                graphics.drawLine(kind == DiagramKind.ERD ? 432 : 512, y + 28, 730, y + 28);
                graphics.drawLine(730, y + 28, 710, y + 18);
                graphics.drawLine(730, y + 28, 710, y + 38);
                graphics.setColor(theme.soft());
                graphics.fillRoundRect(758, y, 260, 56, 12, 12);
                graphics.setColor(theme.primary());
                graphics.drawRoundRect(758, y, 260, 56, 12, 12);
                graphics.setColor(theme.ink());
                graphics.setFont(new Font("Arial", Font.PLAIN, 14));
                graphics.drawString(kind == DiagramKind.ERD ? "Traceable schema" : "Requirement trace", 786, y + 34);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private String useCaseSvg(JsonNode canonical, Theme theme, DocumentationExportTemplate template) {
        StringBuilder lines = new StringBuilder();
        int y = 112;
        int count = 0;
        for (JsonNode requirement : canonical.path("requirements")) {
            if (!"FUNCTIONAL".equals(requirement.path("type").asText())) continue;
            String text = requirement.path("useCaseId").asText("") + " " + requirement.path("id").asText("");
            lines.append("<ellipse cx=\"370\" cy=\"").append(y).append("\" rx=\"260\" ry=\"31\" fill=\"")
                    .append(theme.softHex()).append("\" stroke=\"").append(theme.primaryHex()).append("\" stroke-width=\"2\"/>")
                    .append("<text x=\"132\" y=\"").append(y + 6).append("\" font-family=\"Arial\" font-size=\"14\" fill=\"")
                    .append(theme.inkHex()).append("\">").append(escapeXml(text.trim())).append("</text>");
            y += 84;
            count++;
        }
        if (count == 0) lines.append("<text x=\"90\" y=\"132\" font-family=\"Arial\" font-size=\"14\" fill=\"").append(theme.mutedHex()).append("\">No functional use cases were recorded.</text>");
        int height = Math.max(210, y + 52);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"780\" height=\"" + height + "\" viewBox=\"0 0 780 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"18\" fill=\"" + theme.primaryHex() + "\"/>"
                + "<text x=\"42\" y=\"62\" font-family=\"Arial\" font-size=\"24\" font-weight=\"bold\" fill=\"" + theme.inkHex() + "\">Use case map</text>"
                + "<text x=\"42\" y=\"87\" font-family=\"Arial\" font-size=\"13\" fill=\"" + theme.mutedHex() + "\">" + escapeXml(template.name().toLowerCase(Locale.ROOT)) + " documentation presentation</text>" + lines + "</svg>";
    }

    private String erdSvg(JsonNode canonical, Theme theme, DocumentationExportTemplate template) {
        StringBuilder boxes = new StringBuilder();
        int y = 112;
        int count = 0;
        for (JsonNode entity : canonical.path("entities")) {
            boxes.append("<rect x=\"52\" y=\"").append(y).append("\" width=\"360\" height=\"68\" rx=\"10\" fill=\"")
                    .append(theme.softHex()).append("\" stroke=\"").append(theme.primaryHex()).append("\" stroke-width=\"2\"/>")
                    .append("<text x=\"72\" y=\"").append(y + 29).append("\" font-family=\"Arial\" font-size=\"16\" font-weight=\"bold\" fill=\"")
                    .append(theme.inkHex()).append("\">").append(escapeXml(entity.path("name").asText("Entity"))).append("</text>")
                    .append("<text x=\"72\" y=\"").append(y + 51).append("\" font-family=\"Arial\" font-size=\"12\" fill=\"").append(theme.mutedHex()).append("\">id: uuid (PK)</text>");
            y += 92;
            count++;
        }
        if (count == 0) boxes.append("<text x=\"52\" y=\"130\" font-family=\"Arial\" font-size=\"14\" fill=\"").append(theme.mutedHex()).append("\">No entities were recorded.</text>");
        int height = Math.max(210, y + 42);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"500\" height=\"" + height + "\" viewBox=\"0 0 500 " + height + "\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#" + theme.canvasHex() + "\"/><rect width=\"100%\" height=\"18\" fill=\"" + theme.primaryHex() + "\"/>"
                + "<text x=\"52\" y=\"62\" font-family=\"Arial\" font-size=\"24\" font-weight=\"bold\" fill=\"" + theme.inkHex() + "\">Entity relationship diagram</text>"
                + "<text x=\"52\" y=\"87\" font-family=\"Arial\" font-size=\"13\" fill=\"" + theme.mutedHex() + "\">" + escapeXml(template.name().toLowerCase(Locale.ROOT)) + " documentation presentation</text>" + boxes + "</svg>";
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
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private enum DiagramKind { USE_CASES, ERD }

    private record ArtifactSnapshot(String content, String sourceContent) { }

    private record PackageSnapshot(String projectName, int versionNumber, int srsVersionNumber, JsonNode canonicalModel,
                                   JsonNode validation, Map<DocumentationArtifactType, ArtifactSnapshot> artifacts) {
        static PackageSnapshot from(DocumentationPackageEntity documentationPackage, Map<DocumentationArtifactType, DocumentationArtifactEntity> source) {
            Map<DocumentationArtifactType, ArtifactSnapshot> artifacts = new EnumMap<>(DocumentationArtifactType.class);
            for (DocumentationArtifactType type : DocumentationArtifactType.values()) {
                DocumentationArtifactEntity artifact = source.get(type);
                artifacts.put(type, new ArtifactSnapshot(artifact == null ? "" : artifact.getContent(), artifact == null ? "" : artifact.getSourceContent()));
            }
            return new PackageSnapshot(documentationPackage.getProject().getName(), documentationPackage.getVersionNumber(),
                    documentationPackage.getSrsVersion().getVersionNumber(), documentationPackage.getCanonicalModel(), documentationPackage.getValidationOutcome(), artifacts);
        }

        ArtifactSnapshot artifact(DocumentationArtifactType type) { return artifacts.get(type); }

        String combinedMarkdown() {
            return artifact(DocumentationArtifactType.SRS).sourceContent() + "\n\n"
                    + artifact(DocumentationArtifactType.USE_CASES).content() + "\n\n"
                    + artifact(DocumentationArtifactType.ERD).content() + "\n\n# OpenAPI contract\n\n```json\n"
                    + artifact(DocumentationArtifactType.OPENAPI).sourceContent() + "\n```\n\n"
                    + artifact(DocumentationArtifactType.TRACEABILITY).sourceContent();
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
                case SIGNAL -> new Theme("5B4FE9", "EEEAFE", "1E1B4B", "5F5B78", "FFFFFF");
                case COMMAND -> new Theme("4EC7B6", "112238", "E7F1F5", "8FA4B5", "07101B");
                case OCEAN -> new Theme("0E7490", "E0F2FE", "0C4A6E", "4B6470", "FFFFFF");
                case VIOLET -> new Theme("7C3AED", "F3E8FF", "3B0764", "665078", "FFFFFF");
                case EMERALD -> new Theme("047857", "D1FAE5", "064E3B", "4B635D", "FFFFFF");
                case MONOCHROME -> new Theme("475569", "F1F5F9", "1E293B", "64748B", "FFFFFF");
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
        private float y;
        private int pageNumber;

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
                writeAt(cover, "VELOCIRA", bold, 12, 54, 720);
                writeAt(cover, "DOCUMENTATION PACKAGE", bold, 10, 54, 696);
                writeAt(cover, pdfText(snapshot.projectName()), bold, style.layout() == DocumentationExportLayout.PRESENTATION ? 31 : 27, 54, 620);
                writeAt(cover, pdfText(template.coverline()), regular, 14, 54, 588);
                writeAt(cover, "Package v" + snapshot.versionNumber() + "  |  Source SRS v" + snapshot.srsVersionNumber(), regular, 11, 54, 530);
                writeAt(cover, "Template: " + style.template() + "  |  Theme: " + style.theme() + "  |  Layout: " + style.layout(), regular, 10, 54, 506);
                cover.setNonStrokingColor(theme.soft());
                cover.addRect(54, 165, 504, 118);
                cover.fill();
                cover.setNonStrokingColor(theme.ink());
                writeAt(cover, pdfText(template.callout()), bold, 16, 76, 238);
                writeAt(cover, "Requirements, visual diagrams, contracts, and traceability remain connected", regular, 11, 76, 212);
                writeAt(cover, "to the same reviewed package snapshot.", regular, 11, 76, 194);
            }
        }

        void contents() throws IOException {
            newPage("Contents");
            text("Contents", bold, 20, theme.primary(), 26);
            text("A structured route through the reviewed package snapshot.", regular, 9.4f, theme.muted(), 24);
            tocItem("01", "System requirements");
            tocItem("02", "Use case map");
            tocItem("03", "Use cases");
            tocItem("04", "Entity relationship diagram");
            tocItem("05", "Traceability");
            tocItem("06", "OpenAPI contract");
            callout("Review signal", snapshot.validation().path("valid").asBoolean(false) ? "Validation passed for the generated package." : "Review the recorded package validation findings before release.");
        }

        void section(String title, String markdown) throws IOException {
            newPage(title);
            text(title, bold, 19, theme.primary(), 28);
            sectionRule();
            boolean code = false;
            for (String raw : markdown.split("\\r?\\n")) {
                if (raw.startsWith("```")) {
                    code = !code;
                    continue;
                }
                int level = headingLevel(raw);
                String body = level > 0 ? raw.substring(level + 1).trim() : raw;
                if (level > 0) {
                    ensure(24, title);
                    text(body, bold, level == 1 ? 14 : level == 2 ? 12 : 11, level == 1 ? theme.primary() : theme.ink(), level == 1 ? 21 : 17);
                    continue;
                }
                boolean bullet = raw.startsWith("- ") || raw.startsWith("  - ");
                for (String line : wrap(bullet ? "- " + raw.replaceFirst("^\\s*-\\s+", "") : body, code ? 88 : 96)) {
                    ensure(code ? 13 : 15, title);
                    text(line, code ? regular : regular, code ? 7.5f : 9.4f, code ? theme.muted() : theme.ink(), code ? 10 : 14);
                }
            }
        }

        void diagram(String title, byte[] png) throws IOException {
            newPage(title);
            text(title, bold, 19, theme.primary(), 28);
            sectionRule();
            text("Visual export generated from the reviewed package snapshot. The original editable source remains in the ZIP package.", regular, 9.4f, theme.muted(), 18);
            ensure(250, title);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png, slug(title) + ".png");
            float width = 504;
            float height = width * image.getHeight() / image.getWidth();
            stream.drawImage(image, 54, y - height, width, height);
            y -= height + 18;
        }

        void close() throws IOException {
            if (stream != null) stream.close();
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
            writeAt(stream, pdfText(section), regular, 8, 420, 755);
            writeAt(stream, "Page " + pageNumber, regular, 8, 514, 34);
            y = 720;
        }

        private void callout(String label, String body) throws IOException {
            ensure(62, "Contents");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 54, 504, 48);
            stream.fill();
            y -= 16;
            text(label.toUpperCase(Locale.ROOT), bold, 8, theme.primary(), 13);
            text(body, regular, 9.2f, theme.ink(), 15);
            y -= 7;
        }

        private void tocItem(String number, String title) throws IOException {
            ensure(38, "Contents");
            stream.setNonStrokingColor(theme.soft());
            stream.addRect(54, y - 27, 504, 25);
            stream.fill();
            stream.setNonStrokingColor(theme.primary());
            stream.addRect(54, y - 27, 42, 25);
            stream.fill();
            stream.setNonStrokingColor(Color.WHITE);
            writeAt(stream, number, bold, 8, 66, y - 17);
            stream.setNonStrokingColor(theme.ink());
            writeAt(stream, pdfText(title), bold, 10, 112, y - 17);
            y -= 33;
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
    }

    public record RenderedExport(String filename, String contentType, byte[] content) { }
}
