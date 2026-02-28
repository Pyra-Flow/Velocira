package com.velocira.backend.document.service;

import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.document.dto.CreateDocumentRequest;
import com.velocira.backend.document.dto.DocumentResponse;
import com.velocira.backend.document.dto.UpdateDocumentRequest;
import com.velocira.backend.document.exceptions.DocumentAlreadyExistsException;
import com.velocira.backend.document.exceptions.DocumentNotFoundException;
import com.velocira.backend.document.model.DocumentEntity;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentService}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Tests")
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DocumentService documentService;

    private UserEntity owner;
    private UUID ownerId;
    private ProjectEntity testProject;
    private UUID projectId;
    private DocumentEntity testDocument;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = UserEntity.builder()
                .fullName("John Doe")
                .email("john@velocira.com")
                .password("$2a$12$hashed")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();
        owner.setId(ownerId);

        projectId = UUID.randomUUID();
        testProject = ProjectEntity.builder()
                .owner(owner)
                .name("Test Project")
                .description("A test project description long enough for validation purposes.")
                .type(ProjectType.WEB_APP)
                .status(ProjectStatus.DRAFT)
                .build();
        testProject.setId(projectId);
        testProject.setCreatedAt(Instant.now());
        testProject.setDocuments(new ArrayList<>());

        documentId = UUID.randomUUID();
        testDocument = DocumentEntity.builder()
                .project(testProject)
                .type(DocumentType.SRS)
                .status(DocumentStatus.COMPLETED)
                .title("Software Requirements Specification")
                .content("# SRS Document\n\nThis is the content.")
                .version(1)
                .wordCount(8)
                .build();
        testDocument.setId(documentId);
        testDocument.setCreatedAt(Instant.now());
        testDocument.setUpdatedAt(Instant.now());
    }

    // ── Create Document ─────────────────────────────────────────

    @Nested
    @DisplayName("Create Document")
    class CreateDocument {

        @Test
        @DisplayName("Should create document placeholder successfully")
        void shouldCreateDocument() {
            CreateDocumentRequest request = CreateDocumentRequest.builder()
                    .type(DocumentType.BRD)
                    .title("Business Requirements Document")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.existsByProjectIdAndType(projectId, DocumentType.BRD)).thenReturn(false);
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
                DocumentEntity doc = invocation.getArgument(0);
                doc.setId(UUID.randomUUID());
                doc.setCreatedAt(Instant.now());
                doc.setUpdatedAt(Instant.now());
                return doc;
            });

            DocumentResponse result = documentService.createDocument(projectId, ownerId, request);

            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(DocumentType.BRD);
            assertThat(result.getStatus()).isEqualTo(DocumentStatus.PENDING);
            verify(auditService).record(eq(ownerId), eq("john@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should throw DocumentAlreadyExistsException for duplicate type")
        void shouldThrowOnDuplicateType() {
            CreateDocumentRequest request = CreateDocumentRequest.builder()
                    .type(DocumentType.SRS)
                    .title("SRS")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.existsByProjectIdAndType(projectId, DocumentType.SRS)).thenReturn(true);

            assertThatThrownBy(() -> documentService.createDocument(projectId, ownerId, request))
                    .isInstanceOf(DocumentAlreadyExistsException.class);

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ProjectAccessDeniedException for non-owner")
        void shouldThrowOnNonOwner() {
            UUID otherUserId = UUID.randomUUID();
            CreateDocumentRequest request = CreateDocumentRequest.builder()
                    .type(DocumentType.API_SPECIFICATION)
                    .title("API Spec")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            assertThatThrownBy(() -> documentService.createDocument(projectId, otherUserId, request))
                    .isInstanceOf(ProjectAccessDeniedException.class);
        }
    }

    // ── Get Document ────────────────────────────────────────────

    @Nested
    @DisplayName("Get Document")
    class GetDocument {

        @Test
        @DisplayName("Should return document with content")
        void shouldReturnDocument() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

            DocumentResponse result = documentService.getDocument(projectId, documentId, ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Software Requirements Specification");
            assertThat(result.getContent()).contains("SRS Document");
        }

        @Test
        @DisplayName("Should throw DocumentNotFoundException for unknown document")
        void shouldThrowOnUnknownDocument() {
            UUID unknownId = UUID.randomUUID();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.getDocument(projectId, unknownId, ownerId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }

    // ── Update Document ─────────────────────────────────────────

    @Nested
    @DisplayName("Update Document")
    class UpdateDocument {

        @Test
        @DisplayName("Should update content and increment version")
        void shouldUpdateContentAndVersion() {
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .content("# Updated SRS\n\nNew content with more words in it.")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

            DocumentResponse result = documentService.updateDocument(projectId, documentId, ownerId, request);

            assertThat(result.getVersion()).isEqualTo(2);
            assertThat(result.getStatus()).isEqualTo(DocumentStatus.EDITED);
            assertThat(result.getContent()).contains("Updated SRS");
        }

        @Test
        @DisplayName("Should update title only without version bump")
        void shouldUpdateTitleWithoutVersionBump() {
            testDocument.setStatus(DocumentStatus.PENDING);
            UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                    .title("Revised SRS Title")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
            when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

            DocumentResponse result = documentService.updateDocument(projectId, documentId, ownerId, request);

            assertThat(result.getTitle()).isEqualTo("Revised SRS Title");
            assertThat(result.getVersion()).isEqualTo(1); // unchanged
        }
    }

    // ── Delete Document ─────────────────────────────────────────

    @Nested
    @DisplayName("Delete Document")
    class DeleteDocument {

        @Test
        @DisplayName("Should delete document successfully")
        void shouldDeleteDocument() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

            documentService.deleteDocument(projectId, documentId, ownerId);

            verify(documentRepository).delete(testDocument);
            verify(auditService).record(eq(ownerId), eq("john@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should throw ProjectNotFoundException for unknown project")
        void shouldThrowOnUnknownProject() {
            UUID unknownProjectId = UUID.randomUUID();
            when(projectRepository.findById(unknownProjectId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.deleteDocument(unknownProjectId, documentId, ownerId))
                    .isInstanceOf(ProjectNotFoundException.class);
        }
    }
}
