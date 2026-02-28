package com.velocira.backend.project.service;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.project.dto.*;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for project CRUD operations.
 *
 * <p>
 * Enforces ownership checks — users can only access their own projects
 * unless they have ADMIN privileges.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Lists the authenticated user's projects with optional filters.
     *
     * @param ownerId  the authenticated user's UUID
     * @param status   optional status filter
     * @param search   optional name search term
     * @param pageable pagination parameters
     * @return a page of project responses
     */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> listUserProjects(UUID ownerId, ProjectStatus status,
            String search, Pageable pageable) {
        log.debug("Listing projects for user [{}] status=[{}] search=[{}]", ownerId, status, search);
        String statusStr = status != null ? status.name() : null;
        // Native query has ORDER BY built in; strip Sort to avoid column-name mismatch
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return projectRepository.findByOwnerFiltered(ownerId, statusStr, search, unsorted)
                .map(ProjectMapper::toResponse);
    }

    /**
     * Creates a new project owned by the authenticated user.
     *
     * @param ownerId the authenticated user's UUID
     * @param request the creation request
     * @return the created project response
     */
    @Transactional
    public ProjectResponse createProject(UUID ownerId, CreateProjectRequest request) {
        log.info("Creating project '{}' for user [{}]", request.getName(), ownerId);

        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException(ownerId.toString()));

        ProjectEntity project = ProjectEntity.builder()
                .owner(owner)
                .name(request.getName().trim())
                .description(request.getDescription().trim())
                .type(request.getType())
                .status(ProjectStatus.DRAFT)
                .techStack(request.getTechStack())
                .industry(request.getIndustry())
                .targetAudience(request.getTargetAudience())
                .teamSize(request.getTeamSize())
                .build();

        project = projectRepository.save(project);
        log.info("Project created: id=[{}] name=[{}]", project.getId(), project.getName());

        auditService.record(ownerId, owner.getEmail(), AuditAction.PROJECT_CREATED,
                "Created project: " + project.getName());

        return ProjectMapper.toResponse(project);
    }

    /**
     * Retrieves a project by ID, enforcing ownership.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     * @return the project response
     * @throws ProjectNotFoundException     if project does not exist
     * @throws ProjectAccessDeniedException if user doesn't own the project
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UUID userId) {
        log.debug("Fetching project [{}] for user [{}]", projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);
        return ProjectMapper.toResponse(project);
    }

    /**
     * Updates a project's fields. Only non-null request fields are applied.
     * Only DRAFT and FAILED projects can be updated.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     * @param request   the update request
     * @return the updated project response
     */
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UUID userId, UpdateProjectRequest request) {
        log.info("Updating project [{}] for user [{}]", projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);

        if (request.getName() != null) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription().trim());
        }
        if (request.getType() != null) {
            project.setType(request.getType());
        }
        if (request.getTechStack() != null) {
            project.setTechStack(request.getTechStack());
        }
        if (request.getIndustry() != null) {
            project.setIndustry(request.getIndustry());
        }
        if (request.getTargetAudience() != null) {
            project.setTargetAudience(request.getTargetAudience());
        }
        if (request.getTeamSize() != null) {
            project.setTeamSize(request.getTeamSize());
        }

        project = projectRepository.save(project);
        log.info("Project updated: id=[{}]", project.getId());

        auditService.record(userId, null, AuditAction.PROJECT_UPDATED,
                "Updated project: " + project.getName());

        return ProjectMapper.toResponse(project);
    }

    /**
     * Permanently deletes a project and all associated documents.
     *
     * @param projectId the project UUID
     * @param userId    the requesting user's UUID
     */
    @Transactional
    public void deleteProject(UUID projectId, UUID userId) {
        log.warn("Deleting project [{}] for user [{}]", projectId, userId);
        ProjectEntity project = findProjectWithOwnershipCheck(projectId, userId);

        String projectName = project.getName();
        projectRepository.delete(project);

        auditService.record(userId, null, AuditAction.PROJECT_DELETED,
                "Deleted project: " + projectName);

        log.warn("Project deleted: id=[{}] name=[{}]", projectId, projectName);
    }

    /**
     * Duplicates an existing project (creates a copy in DRAFT status).
     *
     * @param projectId the source project UUID
     * @param userId    the requesting user's UUID
     * @return the duplicated project response
     */
    @Transactional
    public ProjectResponse duplicateProject(UUID projectId, UUID userId) {
        log.info("Duplicating project [{}] for user [{}]", projectId, userId);
        ProjectEntity source = findProjectWithOwnershipCheck(projectId, userId);

        ProjectEntity duplicate = ProjectEntity.builder()
                .owner(source.getOwner())
                .name(source.getName() + " (Copy)")
                .description(source.getDescription())
                .type(source.getType())
                .status(ProjectStatus.DRAFT)
                .techStack(source.getTechStack())
                .industry(source.getIndustry())
                .targetAudience(source.getTargetAudience())
                .teamSize(source.getTeamSize())
                .build();

        duplicate = projectRepository.save(duplicate);
        log.info("Project duplicated: original=[{}] duplicate=[{}]", projectId, duplicate.getId());

        auditService.record(userId, null, AuditAction.PROJECT_DUPLICATED,
                "Duplicated project: " + source.getName() + " → " + duplicate.getName());

        return ProjectMapper.toResponse(duplicate);
    }

    /**
     * Returns the total number of projects for a specific user.
     */
    @Transactional(readOnly = true)
    public long countByOwner(UUID ownerId) {
        return projectRepository.countByOwnerId(ownerId);
    }

    private ProjectEntity findProjectWithOwnershipCheck(UUID projectId, UUID userId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));

        if (!project.getOwner().getId().equals(userId)) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }
}
