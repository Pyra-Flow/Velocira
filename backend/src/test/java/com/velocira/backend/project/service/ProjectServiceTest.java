package com.velocira.backend.project.service;

import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.project.dto.CreateProjectRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.dto.UpdateProjectRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectService}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService Tests")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProjectService projectService;

    private UserEntity owner;
    private UUID ownerId;
    private ProjectEntity testProject;
    private UUID projectId;

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
        owner.setCreatedAt(Instant.now());

        projectId = UUID.randomUUID();
        testProject = ProjectEntity.builder()
                .owner(owner)
                .name("Test Project")
                .description("A comprehensive test project description that is at least fifty chars long.")
                .type(ProjectType.WEB_APP)
                .status(ProjectStatus.DRAFT)
                .techStack("React, Node.js")
                .industry("E-Commerce")
                .build();
        testProject.setId(projectId);
        testProject.setCreatedAt(Instant.now());
        testProject.setUpdatedAt(Instant.now());
        testProject.setDocuments(new ArrayList<>());
    }

    // ── List Projects ───────────────────────────────────────────

    @Nested
    @DisplayName("List Projects")
    class ListProjects {

        @Test
        @DisplayName("Should return paginated projects for the user")
        void shouldReturnPaginatedProjects() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProjectEntity> page = new PageImpl<>(List.of(testProject), pageable, 1);
            when(projectRepository.findByOwnerFiltered(eq(ownerId), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            Page<ProjectResponse> result = projectService.listUserProjects(ownerId, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Test Project");
        }
    }

    // ── Create Project ──────────────────────────────────────────

    @Nested
    @DisplayName("Create Project")
    class CreateProject {

        @Test
        @DisplayName("Should create project successfully")
        void shouldCreateProject() {
            CreateProjectRequest request = CreateProjectRequest.builder()
                    .name("New Project")
                    .description(
                            "A brand new project idea description that is at least fifty chars long to pass validation.")
                    .type(ProjectType.MOBILE_APP)
                    .techStack("Flutter, Firebase")
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> {
                ProjectEntity project = invocation.getArgument(0);
                project.setId(UUID.randomUUID());
                project.setCreatedAt(Instant.now());
                project.setUpdatedAt(Instant.now());
                project.setDocuments(new ArrayList<>());
                return project;
            });

            ProjectResponse result = projectService.createProject(ownerId, request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("New Project");
            assertThat(result.getType()).isEqualTo(ProjectType.MOBILE_APP);
            assertThat(result.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            verify(auditService).record(eq(ownerId), eq("john@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for unknown owner")
        void shouldThrowOnUnknownOwner() {
            UUID unknownId = UUID.randomUUID();
            CreateProjectRequest request = CreateProjectRequest.builder()
                    .name("Test")
                    .description("A test description long enough to pass the fifty character minimum validation.")
                    .type(ProjectType.WEB_APP)
                    .build();

            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.createProject(unknownId, request))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ── Get Project ─────────────────────────────────────────────

    @Nested
    @DisplayName("Get Project")
    class GetProject {

        @Test
        @DisplayName("Should get project successfully when user is owner")
        void shouldGetProject() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            ProjectResponse result = projectService.getProject(projectId, ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test Project");
        }

        @Test
        @DisplayName("Should throw ProjectNotFoundException for unknown project")
        void shouldThrowOnUnknownProject() {
            UUID unknownId = UUID.randomUUID();
            when(projectRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProject(unknownId, ownerId))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw ProjectAccessDeniedException when user is not owner")
        void shouldThrowOnAccessDenied() {
            UUID otherUserId = UUID.randomUUID();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            assertThatThrownBy(() -> projectService.getProject(projectId, otherUserId))
                    .isInstanceOf(ProjectAccessDeniedException.class);
        }
    }

    // ── Update Project ──────────────────────────────────────────

    @Nested
    @DisplayName("Update Project")
    class UpdateProject {

        @Test
        @DisplayName("Should update project fields selectively")
        void shouldUpdateFieldsSelectively() {
            UpdateProjectRequest request = UpdateProjectRequest.builder()
                    .name("Updated Name")
                    .techStack("Python, Django")
                    .build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(i -> {
                ProjectEntity entity = i.getArgument(0);
                entity.setUpdatedAt(Instant.now());
                return entity;
            });

            ProjectResponse result = projectService.updateProject(projectId, ownerId, request);

            assertThat(result.getName()).isEqualTo("Updated Name");
            assertThat(result.getTechStack()).isEqualTo("Python, Django");
            assertThat(result.getIndustry()).isEqualTo("E-Commerce"); // unchanged
        }

        @Test
        @DisplayName("Should deny update for non-owner")
        void shouldDenyUpdateForNonOwner() {
            UUID otherUserId = UUID.randomUUID();
            UpdateProjectRequest request = UpdateProjectRequest.builder().name("Hack").build();

            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            assertThatThrownBy(() -> projectService.updateProject(projectId, otherUserId, request))
                    .isInstanceOf(ProjectAccessDeniedException.class);

            verify(projectRepository, never()).save(any());
        }
    }

    // ── Delete Project ──────────────────────────────────────────

    @Nested
    @DisplayName("Delete Project")
    class DeleteProject {

        @Test
        @DisplayName("Should delete project successfully")
        void shouldDeleteProject() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            projectService.deleteProject(projectId, ownerId);

            verify(projectRepository).delete(testProject);
            verify(auditService).record(eq(ownerId), isNull(), any(), anyString());
        }

        @Test
        @DisplayName("Should deny deletion for non-owner")
        void shouldDenyDeletionForNonOwner() {
            UUID otherUserId = UUID.randomUUID();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

            assertThatThrownBy(() -> projectService.deleteProject(projectId, otherUserId))
                    .isInstanceOf(ProjectAccessDeniedException.class);

            verify(projectRepository, never()).delete(any());
        }
    }

    // ── Duplicate Project ───────────────────────────────────────

    @Nested
    @DisplayName("Duplicate Project")
    class DuplicateProject {

        @Test
        @DisplayName("Should duplicate project in DRAFT status without documents")
        void shouldDuplicateProject() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> {
                ProjectEntity project = invocation.getArgument(0);
                project.setId(UUID.randomUUID());
                project.setCreatedAt(Instant.now());
                project.setUpdatedAt(Instant.now());
                project.setDocuments(new ArrayList<>());
                return project;
            });

            ProjectResponse result = projectService.duplicateProject(projectId, ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).startsWith("Test Project (Copy)");
            assertThat(result.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(result.getProgress()).isEqualTo(0);
        }
    }
}
