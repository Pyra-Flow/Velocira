package com.velocira.backend.documentation.model;

import java.util.Objects;

/**
 * Immutable visual settings persisted with an export so rendered documents can be reproduced.
 *
 * <p>Style is meaningful for document-bearing formats only. Direct source exports accept a
 * format-only/default request, remain byte-faithful to their source artifacts, and persist no
 * visual-style metadata.</p>
 */
public record DocumentationExportStyle(
        DocumentationExportTemplate template,
        DocumentationExportTheme theme,
        DocumentationExportLayout layout) {

    public static final DocumentationExportTemplate DEFAULT_TEMPLATE = DocumentationExportTemplate.TECHNICAL;
    public static final DocumentationExportTheme DEFAULT_THEME = DocumentationExportTheme.COMMAND;
    public static final DocumentationExportLayout DEFAULT_LAYOUT = DocumentationExportLayout.STANDARD;

    public DocumentationExportStyle {
        template = template == null ? DEFAULT_TEMPLATE : template;
        theme = theme == null ? DEFAULT_THEME : theme;
        layout = layout == null ? DEFAULT_LAYOUT : layout;
    }

    public static DocumentationExportStyle defaults() {
        return new DocumentationExportStyle(DEFAULT_TEMPLATE, DEFAULT_THEME, DEFAULT_LAYOUT);
    }

    public boolean isDefault() {
        return template == DEFAULT_TEMPLATE && theme == DEFAULT_THEME && layout == DEFAULT_LAYOUT;
    }

    /** Returns whether this style may be used for the specified export format. */
    public boolean isSupportedBy(DocumentationExportFormat format) {
        Objects.requireNonNull(format, "format must not be null");
        return format.supportsDocumentStyling() || isDefault();
    }
}
