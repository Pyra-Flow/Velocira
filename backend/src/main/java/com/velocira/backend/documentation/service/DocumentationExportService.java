package com.velocira.backend.documentation.service;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.documentation.dto.DocumentationDtos;
import com.velocira.backend.documentation.exceptions.DocumentationPackageException;
import com.velocira.backend.documentation.model.*;
import com.velocira.backend.documentation.repository.*;
import com.velocira.backend.generation.service.GenerationHashing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stores exports as immutable bytes so a later package revision cannot change a download. */
@Service
@RequiredArgsConstructor
public class DocumentationExportService {
    private final DocumentationPackageRepository packageRepository;
    private final DocumentationArtifactRepository artifactRepository;
    private final DocumentationExportJobRepository exportRepository;
    private final DocumentationExportRenderer renderer;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DocumentationDtos.ExportResponse> list(UUID projectId, UUID packageId, UUID ownerId) {
        requiredPackage(projectId, packageId, ownerId);
        return exportRepository.findByDocumentationPackageIdAndOwnerIdOrderByCreatedAtDesc(packageId, ownerId).stream().map(this::response).toList();
    }

    @Transactional
    public DocumentationDtos.ExportResponse create(UUID projectId, UUID packageId, UUID ownerId, DocumentationExportFormat format) {
        return create(projectId, packageId, ownerId, format, DocumentationExportStyle.defaults());
    }

    /** Accepts the API-level style fields while retaining the legacy format-only overload above. */
    @Transactional
    public DocumentationDtos.ExportResponse create(UUID projectId, UUID packageId, UUID ownerId, DocumentationExportFormat format,
                                                    DocumentationExportTemplate template, DocumentationExportTheme theme,
                                                    DocumentationExportLayout layout) {
        return create(projectId, packageId, ownerId, format, new DocumentationExportStyle(template, theme, layout));
    }

    @Transactional
    public DocumentationDtos.ExportResponse create(UUID projectId, UUID packageId, UUID ownerId, DocumentationExportFormat format,
                                                    DocumentationExportStyle requestedStyle) {
        DocumentationPackageEntity documentationPackage = requiredPackage(projectId, packageId, ownerId);
        if (documentationPackage.getStatus() != DocumentationPackageStatus.APPROVED) {
            throw new DocumentationPackageException("Approve the documentation package before exporting it.", HttpStatus.CONFLICT);
        }
        DocumentationExportStyle style = requestedStyle == null ? DocumentationExportStyle.defaults() : requestedStyle;
        if (!style.isSupportedBy(format)) {
            throw new DocumentationPackageException("Template, theme, and layout are available only for ZIP, PDF, and DOCX exports.",
                    HttpStatus.BAD_REQUEST);
        }
        DocumentationExportRenderer.RenderedExport rendered = renderer.render(format, documentationPackage,
                artifactRepository.findByDocumentationPackageIdOrderByArtifactTypeAsc(packageId), style);
        boolean styledDocument = format.supportsDocumentStyling();
        DocumentationExportJobEntity job = exportRepository.save(DocumentationExportJobEntity.builder()
                .documentationPackage(documentationPackage).owner(documentationPackage.getOwner()).format(format)
                .template(styledDocument ? style.template() : null).theme(styledDocument ? style.theme() : null).layout(styledDocument ? style.layout() : null)
                .status(DocumentationExportStatus.READY).filename(rendered.filename()).contentType(rendered.contentType())
                .byteSize(rendered.content().length).contentSha256(GenerationHashing.sha256(rendered.content()))
                .content(rendered.content()).completedAt(Instant.now()).build());
        auditService.record(ownerId, documentationPackage.getOwner().getEmail(), AuditAction.DOCUMENTATION_PACKAGE_EXPORTED,
                "Exported linked documentation package v" + documentationPackage.getVersionNumber() + " as " + format
                        + (styledDocument ? " using " + style.template() + "/" + style.theme() + "/" + style.layout() : " as a faithful source artifact") + ".");
        return response(job);
    }

    @Transactional(readOnly = true)
    public Download download(UUID projectId, UUID packageId, UUID exportId, UUID ownerId) {
        requiredPackage(projectId, packageId, ownerId);
        DocumentationExportJobEntity job = exportRepository.findByIdAndDocumentationPackageIdAndOwnerId(exportId, packageId, ownerId)
                .orElseThrow(() -> new DocumentationPackageException("Documentation export not found.", HttpStatus.NOT_FOUND));
        if (job.getStatus() != DocumentationExportStatus.READY) {
            throw new DocumentationPackageException("This export is not available.", HttpStatus.CONFLICT);
        }
        return new Download(job.getFilename(), job.getContentType(), job.getContent());
    }

    @Transactional(readOnly = true)
    public Download preview(UUID projectId, UUID packageId, UUID ownerId, DocumentationArtifactType artifactType) {
        DocumentationPackageEntity documentationPackage = requiredPackage(projectId, packageId, ownerId);
        DocumentationExportRenderer.RenderedExport rendered = renderer.renderPreview(artifactType, documentationPackage,
                artifactRepository.findByDocumentationPackageIdOrderByArtifactTypeAsc(packageId), DocumentationExportStyle.defaults());
        return new Download(rendered.filename(), rendered.contentType(), rendered.content());
    }

    private DocumentationPackageEntity requiredPackage(UUID projectId, UUID packageId, UUID ownerId) {
        return packageRepository.findByIdAndProjectIdAndOwnerId(packageId, projectId, ownerId)
                .orElseThrow(() -> new DocumentationPackageException("Documentation package not found.", HttpStatus.NOT_FOUND));
    }
    private DocumentationDtos.ExportResponse response(DocumentationExportJobEntity job) {
        return new DocumentationDtos.ExportResponse(job.getId(), job.getFormat().name(), enumName(job.getTemplate()), enumName(job.getTheme()),
                enumName(job.getLayout()), job.getStatus().name(), job.getFilename(),
                job.getContentType(), job.getByteSize(), job.getContentSha256(), job.getCompletedAt(), job.getCreatedAt());
    }

    private String enumName(Enum<?> value) { return value == null ? null : value.name(); }
    public record Download(String filename, String contentType, byte[] bytes) { }
}
