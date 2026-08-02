package com.velocira.backend.documentation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.velocira.backend.documentation.model.DocumentationArtifactEntity;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import com.velocira.backend.documentation.model.DocumentationPackageEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic export renderer. Every format is derived from the same immutable package snapshot. */
@Component
public class DocumentationExportRenderer {
    public RenderedExport render(DocumentationExportFormat format, DocumentationPackageEntity documentationPackage,
                                 List<DocumentationArtifactEntity> artifacts) {
        Map<DocumentationArtifactType, DocumentationArtifactEntity> byType = new EnumMap<>(DocumentationArtifactType.class);
        artifacts.forEach(artifact -> byType.put(artifact.getArtifactType(), artifact));
        String projectSlug = slug(documentationPackage.getProject().getName());
        try {
            return switch (format) {
                case ZIP -> new RenderedExport(projectSlug + "-documentation-package-v" + documentationPackage.getVersionNumber() + ".zip",
                        "application/zip", zip(documentationPackage, byType));
                case MARKDOWN -> new RenderedExport(projectSlug + "-documentation-package-v" + documentationPackage.getVersionNumber() + ".md",
                        "text/markdown; charset=utf-8", markdown(byType).getBytes(StandardCharsets.UTF_8));
                case PDF -> new RenderedExport(projectSlug + "-documentation-package-v" + documentationPackage.getVersionNumber() + ".pdf",
                        "application/pdf", pdf(markdown(byType)));
                case DOCX -> new RenderedExport(projectSlug + "-documentation-package-v" + documentationPackage.getVersionNumber() + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(documentationPackage, byType));
                case OPENAPI_JSON -> sourceExport(projectSlug, documentationPackage, byType.get(DocumentationArtifactType.OPENAPI), ".openapi.json", "application/vnd.oai.openapi+json;version=3.1");
                case OPENAPI_YAML -> new RenderedExport(projectSlug + "-v" + documentationPackage.getVersionNumber() + ".openapi.yaml",
                        "application/yaml; charset=utf-8", openApiYaml(byType.get(DocumentationArtifactType.OPENAPI)).getBytes(StandardCharsets.UTF_8));
                case UML_SOURCE -> sourceExport(projectSlug, documentationPackage, byType.get(DocumentationArtifactType.USE_CASES), ".use-cases.puml", "text/plain; charset=utf-8");
                case ERD_SOURCE -> sourceExport(projectSlug, documentationPackage, byType.get(DocumentationArtifactType.ERD), ".erd.mmd", "text/plain; charset=utf-8");
            };
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the documentation export.", exception);
        }
    }

    private RenderedExport sourceExport(String projectSlug, DocumentationPackageEntity documentationPackage, DocumentationArtifactEntity artifact,
                                        String extension, String contentType) {
        return new RenderedExport(projectSlug + "-v" + documentationPackage.getVersionNumber() + extension, contentType,
                artifact.getSourceContent().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] zip(DocumentationPackageEntity documentationPackage, Map<DocumentationArtifactType, DocumentationArtifactEntity> artifacts) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "README.md", "# " + documentationPackage.getProject().getName() + " documentation package\n\n" +
                    "Package v" + documentationPackage.getVersionNumber() + " is generated from SRS v" + documentationPackage.getSrsVersion().getVersionNumber() + ".\n");
            put(zip, "srs.md", artifacts.get(DocumentationArtifactType.SRS).getSourceContent());
            put(zip, "use-cases.md", artifacts.get(DocumentationArtifactType.USE_CASES).getContent());
            put(zip, "diagrams/use-cases.puml", artifacts.get(DocumentationArtifactType.USE_CASES).getSourceContent());
            put(zip, "diagrams/use-cases.svg", useCaseSvg(documentationPackage.getCanonicalModel()));
            put(zip, "erd.md", artifacts.get(DocumentationArtifactType.ERD).getContent());
            put(zip, "diagrams/erd.mmd", artifacts.get(DocumentationArtifactType.ERD).getSourceContent());
            put(zip, "diagrams/erd.svg", erdSvg(documentationPackage.getCanonicalModel()));
            put(zip, "openapi.json", artifacts.get(DocumentationArtifactType.OPENAPI).getSourceContent());
            put(zip, "openapi.yaml", openApiYaml(artifacts.get(DocumentationArtifactType.OPENAPI)));
            put(zip, "traceability.md", artifacts.get(DocumentationArtifactType.TRACEABILITY).getSourceContent());
            put(zip, "canonical-model.json", documentationPackage.getCanonicalModel().toPrettyString());
            put(zip, "validation.json", documentationPackage.getValidationOutcome().toPrettyString());
            put(zip, "documentation-package.docx", docx(documentationPackage, artifacts));
            put(zip, "documentation-package.pdf", pdf(markdown(artifacts)));
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException { put(zip, name, content.getBytes(StandardCharsets.UTF_8)); }
    private void put(ZipOutputStream zip, String name, byte[] content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry(); }

    private String markdown(Map<DocumentationArtifactType, DocumentationArtifactEntity> artifacts) {
        return artifacts.get(DocumentationArtifactType.SRS).getSourceContent() + "\n\n" +
                artifacts.get(DocumentationArtifactType.USE_CASES).getContent() + "\n\n" +
                artifacts.get(DocumentationArtifactType.ERD).getContent() + "\n\n# OpenAPI contract\n\n```json\n" +
                artifacts.get(DocumentationArtifactType.OPENAPI).getSourceContent() + "\n```\n\n" +
                artifacts.get(DocumentationArtifactType.TRACEABILITY).getSourceContent();
    }

    private byte[] docx(DocumentationPackageEntity documentationPackage, Map<DocumentationArtifactType, DocumentationArtifactEntity> artifacts) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            XWPFRun titleRun = title.createRun(); titleRun.setText(documentationPackage.getProject().getName() + " documentation package"); titleRun.setBold(true); titleRun.setFontSize(22);
            XWPFParagraph subtitle = document.createParagraph();
            subtitle.createRun().setText("Package v" + documentationPackage.getVersionNumber() + " | Generated SRS v" + documentationPackage.getSrsVersion().getVersionNumber());
            addMarkdown(document, artifacts.get(DocumentationArtifactType.SRS).getSourceContent());
            addMarkdown(document, artifacts.get(DocumentationArtifactType.USE_CASES).getContent());
            addMarkdown(document, "# ERD source\n\n" + artifacts.get(DocumentationArtifactType.ERD).getSourceContent());
            addMarkdown(document, "# Traceability\n\n" + artifacts.get(DocumentationArtifactType.TRACEABILITY).getSourceContent());
            document.write(output);
            return output.toByteArray();
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(12_240)); // US Letter, twentieths of a point
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

    private void addMarkdown(XWPFDocument document, String markdown) {
        for (String line : markdown.split("\\r?\\n")) {
            XWPFParagraph paragraph = document.createParagraph();
            if (line.startsWith("### ")) { paragraph.setStyle("Heading3"); paragraph.createRun().setText(line.substring(4)); }
            else if (line.startsWith("## ")) { paragraph.setStyle("Heading2"); paragraph.createRun().setText(line.substring(3)); }
            else if (line.startsWith("# ")) { paragraph.setStyle("Heading1"); paragraph.createRun().setText(line.substring(2)); }
            else if (line.startsWith("- ") || line.startsWith("  - ")) { paragraph.setStyle("ListBullet"); paragraph.createRun().setText(line.replaceFirst("^\\s*-\\s+", "")); }
            else if (line.matches("\\d+\\..*")) { paragraph.setStyle("ListNumber"); paragraph.createRun().setText(line.replaceFirst("^\\d+\\.\\s*", "")); }
            else { paragraph.createRun().setText(line); }
        }
    }

    private byte[] pdf(String markdown) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER); document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = 740f;
            stream.beginText(); stream.setFont(font, 9); stream.newLineAtOffset(52, y);
            for (String rawLine : markdown.replace("```json", "").replace("```mermaid", "").replace("```", "").split("\\r?\\n")) {
                boolean heading = rawLine.startsWith("#");
                String line = rawLine.replaceFirst("^#+\\s*", "");
                for (String wrapped : wrap(line, 100)) {
                    if (y < 55) {
                        stream.endText(); stream.close();
                        page = new PDPage(PDRectangle.LETTER); document.addPage(page);
                        stream = new PDPageContentStream(document, page); y = 740f;
                        stream.beginText(); stream.setFont(font, 9); stream.newLineAtOffset(52, y);
                    }
                    stream.setFont(heading ? bold : font, heading ? 12 : 9);
                    stream.showText(pdfText(wrapped)); stream.newLineAtOffset(0, heading ? -17 : -12); y -= heading ? 17 : 12;
                }
            }
            stream.endText(); stream.close(); document.save(output); return output.toByteArray();
        }
    }

    private List<String> wrap(String text, int width) {
        if (text.isBlank()) return List.of(" ");
        List<String> lines = new ArrayList<>(); String remaining = text;
        while (remaining.length() > width) { int cut = remaining.lastIndexOf(' ', width); if (cut < 1) cut = width; lines.add(remaining.substring(0, cut)); remaining = remaining.substring(cut).trim(); }
        lines.add(remaining); return lines;
    }
    private String pdfText(String value) { return value.replaceAll("[^\\x20-\\x7E]", "?"); }

    private String openApiYaml(DocumentationArtifactEntity openApi) {
        try {
            JsonNode contract = new ObjectMapper().readTree(openApi.getSourceContent());
            return new ObjectMapper(new YAMLFactory()).writeValueAsString(contract);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the OpenAPI YAML contract.", exception);
        }
    }

    private String useCaseSvg(JsonNode canonical) {
        StringBuilder lines = new StringBuilder(); int y = 88;
        for (JsonNode req : canonical.path("requirements")) {
            if (!"FUNCTIONAL".equals(req.path("type").asText())) continue;
            lines.append("<ellipse cx=\"360\" cy=\"").append(y).append("\" rx=\"220\" ry=\"28\" fill=\"#eef2ff\" stroke=\"#4338ca\"/><text x=\"170\" y=\"").append(y + 5).append("\" font-size=\"12\">")
                    .append(escapeXml(req.path("useCaseId").asText() + " " + req.path("id").asText())).append("</text>"); y += 72;
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"760\" height=\"" + Math.max(160, y + 40) + "\" viewBox=\"0 0 760 " + Math.max(160, y + 40) + "\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"28\" y=\"34\" font-family=\"Arial\" font-size=\"20\" font-weight=\"bold\">Use cases</text>" + lines + "</svg>";
    }

    private String erdSvg(JsonNode canonical) {
        StringBuilder boxes = new StringBuilder(); int y = 70;
        for (JsonNode entity : canonical.path("entities")) { boxes.append("<rect x=\"40\" y=\"").append(y).append("\" width=\"260\" height=\"64\" rx=\"6\" fill=\"#ecfeff\" stroke=\"#0891b2\"/><text x=\"56\" y=\"").append(y + 25).append("\" font-size=\"14\" font-weight=\"bold\">").append(escapeXml(entity.path("name").asText())).append("</text><text x=\"56\" y=\"").append(y + 47).append("\" font-size=\"12\">id: uuid (PK)</text>"); y += 88; }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"380\" height=\"" + Math.max(150, y + 20) + "\" viewBox=\"0 0 380 " + Math.max(150, y + 20) + "\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"40\" y=\"36\" font-family=\"Arial\" font-size=\"20\" font-weight=\"bold\">Entity relationship diagram</text>" + boxes + "</svg>";
    }
    private String slug(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String escapeXml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    public record RenderedExport(String filename, String contentType, byte[] content) { }
}
