package com.velocira.backend.documentation.model;

/** Downloadable immutable representations of a documentation package. */
public enum DocumentationExportFormat {
    ZIP, MARKDOWN, PDF, DOCX, OPENAPI_JSON, OPENAPI_YAML, UML_SOURCE, ERD_SOURCE;

    /** Whether template, theme, and layout affect the generated export bytes. */
    public boolean supportsDocumentStyling() {
        return this == ZIP || this == PDF || this == DOCX;
    }
}
