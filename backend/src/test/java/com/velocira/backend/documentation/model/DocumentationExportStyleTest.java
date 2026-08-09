package com.velocira.backend.documentation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationExportStyleTest {

    @Test
    void missingStyleValuesResolveToTheStableDefaults() {
        DocumentationExportStyle style = new DocumentationExportStyle(null, null, null);

        assertThat(style).isEqualTo(DocumentationExportStyle.defaults());
        assertThat(style.isDefault()).isTrue();
    }

    @Test
    void sourceFormatsOnlyAllowTheDefaultStyleMetadata() {
        DocumentationExportStyle custom = new DocumentationExportStyle(
                DocumentationExportTemplate.TECHNICAL,
                DocumentationExportTheme.OCEAN,
                DocumentationExportLayout.COMPACT);

        assertThat(DocumentationExportStyle.defaults().isSupportedBy(DocumentationExportFormat.OPENAPI_JSON)).isTrue();
        assertThat(custom.isSupportedBy(DocumentationExportFormat.OPENAPI_JSON)).isFalse();
        assertThat(custom.isSupportedBy(DocumentationExportFormat.ZIP)).isTrue();
        assertThat(custom.isSupportedBy(DocumentationExportFormat.PDF)).isTrue();
        assertThat(custom.isSupportedBy(DocumentationExportFormat.DOCX)).isTrue();
    }
}
