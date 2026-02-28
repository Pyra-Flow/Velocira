package com.velocira.backend.document.service;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.document.dto.*;
import com.velocira.backend.document.exceptions.DocumentAlreadyExistsException;
import com.velocira.backend.document.exceptions.DocumentNotFoundException;
import com.velocira.backend.document.model.DocumentEntity;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for document CRUD operations within a project.
 *
 * <p>
 * All operations enforce project ownership — users can only manipulate
 * documents within their own projects. AI content generation is handled
 * separately by the ML module.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    /**
     * Lists documents for a project with optional filters.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     * @param status    optional status filter
     * @param type      optional type filter
     * @param pageable  pagination parameters
     * @return a page of document responses (without content)
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(UUID projectId, UUID userId,
            DocumentStatus status, DocumentType type,
            Pageable pageable) {
        log.debug("Listing documents for project [{}] user [{}]", projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);
        return documentRepository.findByProjectFiltered(project.getId(), status, type, pageable)
                .map(DocumentMapper::toSummaryResponse);
    }

    /**
     * Gets all documents for a project as a flat list.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     * @return list of document summary responses
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listAllDocuments(UUID projectId, UUID userId) {
        log.debug("Listing all documents for project [{}] user [{}]", projectId, userId);
        findProjectWithOwnershipCheck(projectId, userId);
        return documentRepository.findByProjectIdOrderByTypeAsc(projectId)
                .stream()
                .map(DocumentMapper::toSummaryResponse)
                .toList();
    }

    /**
     * Creates a new document placeholder within a project.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     * @param request   the creation request
     * @return the created document response
     * @throws DocumentAlreadyExistsException if a document of the same type already
     *                                        exists
     */
    @Transactional
    public DocumentResponse createDocument(UUID projectId, UUID userId, CreateDocumentRequest request) {
        log.info("Creating document type=[{}] in project [{}] for user [{}]",
                request.getType(), projectId, userId);

        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);

        // Prevent duplicate document types within a project
        if (documentRepository.existsByProjectIdAndType(projectId, request.getType())) {
            throw new DocumentAlreadyExistsException(project.getName(), request.getType().name());
        }

        DocumentEntity document = DocumentEntity.builder()
                .project(project)
                .type(request.getType())
                .status(DocumentStatus.PENDING)
                .title(request.getTitle().trim())
                .build();

        document = documentRepository.save(document);
        log.info("Document created: id=[{}] type=[{}] project=[{}]",
                document.getId(), document.getType(), project.getName());

        auditService.record(userId, project.getOwner().getEmail(), AuditAction.DOCUMENT_CREATED,
                "Created " + request.getType() + " document in project: " + project.getName());

        return DocumentMapper.toResponse(document);
    }

    /**
     * Retrieves a single document with full content.
     *
     * @param projectId  the project UUID
     * @param documentId the document UUID
     * @param userId     the requesting user's UUID
     * @return the document response with content
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID projectId, UUID documentId, UUID userId) {
        log.debug("Fetching document [{}] in project [{}] for user [{}]", documentId, projectId, userId);
        findProjectWithOwnershipCheck(projectId, userId);

        DocumentEntity document = documentRepository.findById(documentId)
                .filter(d -> d.getProject().getId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(documentId.toString()));

        return DocumentMapper.toResponse(document);
    }

    /**
     * Updates a document's title and/or content.
     *
     * <p>
     * When content is updated, the word count is recalculated and the
     * version number is incremented. The status is set to {@code EDITED}
     * if the document was previously {@code COMPLETED}.
     * </p>
     *
     * @param projectId  the project UUID
     * @param documentId the document UUID
     * @param userId     the requesting user's UUID
     * @param request    the update request
     * @return the updated document response
     */
    @Transactional
    public DocumentResponse updateDocument(UUID projectId, UUID documentId, UUID userId,
            UpdateDocumentRequest request) {
        log.info("Updating document [{}] in project [{}] for user [{}]", documentId, projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);

        DocumentEntity document = documentRepository.findById(documentId)
                .filter(d -> d.getProject().getId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(documentId.toString()));

        if (request.getTitle() != null) {
            document.setTitle(request.getTitle().trim());
        }

        if (request.getContent() != null) {
            document.setContent(request.getContent());
            document.setWordCount(countWords(request.getContent()));
            document.setVersion(document.getVersion() + 1);

            // Mark as EDITED if it was previously AI-generated
            if (document.getStatus() == DocumentStatus.COMPLETED) {
                document.setStatus(DocumentStatus.EDITED);
            }
        }

        document = documentRepository.save(document);
        log.info("Document updated: id=[{}] version=[{}]", document.getId(), document.getVersion());

        auditService.record(userId, project.getOwner().getEmail(), AuditAction.DOCUMENT_UPDATED,
                "Updated " + document.getType() + " document in project: " + project.getName());

        return DocumentMapper.toResponse(document);
    }

    /**
     * Deletes a document from a project.
     *
     * @param projectId  the project UUID
     * @param documentId the document UUID
     * @param userId     the requesting user's UUID
     */
    @Transactional
    public void deleteDocument(UUID projectId, UUID documentId, UUID userId) {
        log.info("Deleting document [{}] in project [{}] for user [{}]", documentId, projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);

        DocumentEntity document = documentRepository.findById(documentId)
                .filter(d -> d.getProject().getId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(documentId.toString()));

        documentRepository.delete(document);
        log.info("Document deleted: id=[{}] type=[{}]", documentId, document.getType());

        auditService.record(userId, project.getOwner().getEmail(), AuditAction.DOCUMENT_DELETED,
                "Deleted " + document.getType() + " document from project: " + project.getName());
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ProjectEntity findProjectWithOwnershipCheck(UUID projectId, UUID userId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));

        if (!project.getOwner().getId().equals(userId)) {
            throw new ProjectAccessDeniedException(projectId.toString());
        }

        return project;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank())
            return 0;
        return text.trim().split("\\s+").length;
    }
}
