package com.velocira.backend.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.knowledge.client.KnowledgeAiClient;
import com.velocira.backend.knowledge.client.KnowledgeAiException;
import com.velocira.backend.knowledge.dto.KnowledgeDtos;
import com.velocira.backend.knowledge.exceptions.KnowledgeStateException;
import com.velocira.backend.knowledge.model.*;
import com.velocira.backend.knowledge.repository.*;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** SRS orchestration: trusted brief + approved, tenant-filtered retrieval + strict persistence. */
@Service
@RequiredArgsConstructor
public class SrsService {
    private static final Pattern REQUIREMENT_ID = Pattern.compile("^SRS-(FR|NFR)-[0-9]{3,}$");
    /** A stable, internal-only anchor used when the owner has not uploaded extra evidence. */
    private static final UUID PROJECT_BRIEF_SOURCE_ID = new UUID(0L, 1L);
    private static final UUID PROJECT_BRIEF_CHUNK_ID = new UUID(0L, 2L);

    private final ProjectRepository projectRepository;
    private final InterviewService interviewService;
    private final com.velocira.backend.interview.repository.InterviewSessionRepository interviewSessionRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final StandardsProfileRepository profileRepository;
    private final SrsVersionRepository versionRepository;
    private final SrsRequirementRepository requirementRepository;
    private final RequirementTraceLinkRepository traceRepository;
    private final KnowledgeAiClient knowledgeAiClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.StandardsProfileResponse> profiles() {
        return profileRepository.findByActiveTrueOrderByProfileKeyAsc().stream().map(this::profileResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.SrsVersionResponse> list(UUID projectId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        return versionRepository.findByProjectIdAndOwnerIdOrderByVersionNumberDesc(projectId, ownerId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.SrsVersionResponse get(UUID projectId, UUID versionId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        return response(requiredVersion(projectId, versionId, ownerId));
    }

    @Transactional
    public KnowledgeDtos.SrsVersionResponse generate(UUID projectId, UUID ownerId, String profileKey) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        interviewService.assertGenerationReady(projectId, ownerId);
        JsonNode brief = interviewSessionRepository.findByProjectIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new KnowledgeStateException("Finish the discovery questions before generating an SRS.", HttpStatus.CONFLICT))
                .getCanonicalBrief().deepCopy();
        StandardsProfileEntity profile = profileRepository.findByProfileKeyAndActiveTrue(profileKey.trim().toUpperCase())
                .orElseThrow(() -> new KnowledgeStateException("The selected standards profile is unavailable.", HttpStatus.NOT_FOUND));
        List<KnowledgeSourceEntity> approvedSources = sourceRepository.findCurrentByProjectOwnerAndStatus(
                projectId, ownerId, KnowledgeSourceStatus.APPROVED, Instant.now());
        List<KnowledgeAiClient.RetrievedEvidence> evidence;
        if (approvedSources.isEmpty()) {
            // The completed interview is already owner-provided evidence. Extra
            // uploads improve citations, but must never be a click barrier to
            // producing the first useful document.
            evidence = List.of(projectBriefEvidence(brief));
        } else {
            String query = brief.toString();
            if (query.length() > 12_000) query = query.substring(0, 12_000);
            try { evidence = knowledgeAiClient.retrieve(projectId, ownerId, query, 12); }
            catch (KnowledgeAiException ex) { throw new KnowledgeStateException(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE); }
            if (evidence.isEmpty()) {
                evidence = List.of(projectBriefEvidence(brief));
            } else {
                assertRetrievedEvidenceIsApproved(evidence, approvedSources, projectId, ownerId);
            }
        }

        KnowledgeAiClient.SrsGenerationResult generated;
        try {
            generated = knowledgeAiClient.generate(new KnowledgeAiClient.SrsGenerationRequest(
                    projectId, project.getName(), project.getDescription(), project.getType().name(), brief,
                    profile.getProfileKey(), profile.getName(), profile.getControls(), evidence));
        } catch (KnowledgeAiException ex) {
            throw new KnowledgeStateException(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
        validateProviderDraft(generated, evidence);
        int versionNumber = versionRepository.nextVersionBase(projectId) + 1;
        double coverage = generated.validation().path("citation_coverage").asDouble(-1);
        SrsVersionEntity version = SrsVersionEntity.builder()
                .project(project).owner(project.getOwner()).profile(profile).versionNumber(versionNumber)
                .status(SrsVersionStatus.NEEDS_REVIEW).briefSnapshot(brief).srsContent(generated.artifact().deepCopy())
                .validationOutcome(generated.validation().deepCopy()).citationCoverage(BigDecimal.valueOf(coverage))
                .provider(generated.provider()).model(generated.model()).promptVersion(generated.promptVersion())
                .generatedAt(Instant.now()).build();
        version = versionRepository.save(version);
        persistRequirements(version, generated.artifact(), evidence);
        project.setStatus(ProjectStatus.NEEDS_REVIEW);
        project.setProgress(0);
        projectRepository.save(project);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.SRS_GENERATED,
                "Generated SRS version " + versionNumber + " using " + profile.getProfileKey() + " controls.");
        return response(version);
    }

    @Transactional
    public KnowledgeDtos.SrsVersionResponse approve(UUID projectId, UUID versionId, UUID ownerId) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        SrsVersionEntity version = requiredVersion(projectId, versionId, ownerId);
        if (version.getStatus() == SrsVersionStatus.APPROVED) return response(version);
        version.setStatus(SrsVersionStatus.APPROVED);
        version.setApprovedAt(Instant.now());
        version.setChangeRequest(null);
        versionRepository.save(version);
        project.setStatus(ProjectStatus.APPROVED);
        projectRepository.save(project);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.SRS_APPROVED,
                "Approved SRS version " + version.getVersionNumber() + ".");
        return response(version);
    }

    @Transactional
    public KnowledgeDtos.SrsVersionResponse requestChanges(UUID projectId, UUID versionId, UUID ownerId, String message) {
        ownedProject(projectId, ownerId);
        SrsVersionEntity version = requiredVersion(projectId, versionId, ownerId);
        version.setStatus(SrsVersionStatus.CHANGES_REQUESTED);
        version.setChangeRequest(message.trim());
        version.setApprovedAt(null);
        versionRepository.save(version);
        auditService.record(ownerId, version.getOwner().getEmail(), AuditAction.SRS_CHANGE_REQUESTED,
                "Requested changes to SRS version " + version.getVersionNumber() + ".");
        return response(version);
    }

    private void assertRetrievedEvidenceIsApproved(List<KnowledgeAiClient.RetrievedEvidence> evidence,
                                                    List<KnowledgeSourceEntity> sources, UUID projectId, UUID ownerId) {
        Set<UUID> allowedSources = new HashSet<>();
        sources.forEach(source -> allowedSources.add(source.getId()));
        for (KnowledgeAiClient.RetrievedEvidence hit : evidence) {
            KnowledgeChunkEntity chunk = chunkRepository.findByIdAndProjectIdAndOwnerId(hit.chunkId(), projectId, ownerId)
                    .orElseThrow(() -> new KnowledgeStateException("The retrieval service returned unknown evidence.", HttpStatus.SERVICE_UNAVAILABLE));
            if (!allowedSources.contains(hit.sourceId()) || !chunk.getSource().getId().equals(hit.sourceId()) ||
                    !chunk.getContent().equals(hit.content())) {
                throw new KnowledgeStateException("The retrieval service returned evidence outside this project.", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
    }

    private KnowledgeAiClient.RetrievedEvidence projectBriefEvidence(JsonNode brief) {
        String content = brief.toString();
        if (content.length() > 12_000) content = content.substring(0, 12_000);
        return new KnowledgeAiClient.RetrievedEvidence(
                PROJECT_BRIEF_SOURCE_ID, PROJECT_BRIEF_CHUNK_ID, "Confirmed project brief", content, 1.0);
    }

    private void validateProviderDraft(KnowledgeAiClient.SrsGenerationResult generated, List<KnowledgeAiClient.RetrievedEvidence> evidence) {
        if (!generated.validation().path("valid").asBoolean(false)) {
            throw new KnowledgeStateException("The draft did not meet the SRS quality checks and was not saved.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        JsonNode requirements = generated.artifact().path("requirements");
        if (!requirements.isArray() || requirements.isEmpty()) {
            throw new KnowledgeStateException("The draft did not contain any validated requirements.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<String> allowed = new HashSet<>();
        evidence.forEach(hit -> allowed.add(hit.sourceId() + ":" + hit.chunkId()));
        Set<String> IDs = new HashSet<>();
        for (JsonNode requirement : requirements) {
            String id = requirement.path("id").asText();
            if (!REQUIREMENT_ID.matcher(id).matches() || !IDs.add(id) || blank(requirement, "statement") ||
                    blank(requirement, "rationale") || blank(requirement, "source_detail") || blank(requirement, "verification_method") ||
                    !requirement.path("acceptance_criteria").isArray() || requirement.path("acceptance_criteria").isEmpty()) {
                throw new KnowledgeStateException("The draft failed a required requirement-quality check.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            boolean citation = "CITATION".equals(requirement.path("source_kind").asText());
            if (citation && requirement.path("citations").isEmpty()) {
                throw new KnowledgeStateException("A cited requirement is missing its evidence anchor.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            for (JsonNode anchor : requirement.path("citations")) {
                String key = anchor.path("source_id").asText() + ":" + anchor.path("chunk_id").asText();
                if (!allowed.contains(key)) throw new KnowledgeStateException("A requirement cited unapproved evidence.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    private void persistRequirements(SrsVersionEntity version, JsonNode artifact, List<KnowledgeAiClient.RetrievedEvidence> evidence) {
        Map<UUID, KnowledgeAiClient.RetrievedEvidence> evidenceByChunk = new HashMap<>();
        evidence.forEach(hit -> evidenceByChunk.put(hit.chunkId(), hit));
        for (JsonNode node : artifact.path("requirements")) {
            ObjectNode quality = objectMapper.createObjectNode();
            String statement = node.path("statement").asText();
            quality.put("idValid", REQUIREMENT_ID.matcher(node.path("id").asText()).matches());
            quality.put("atomic", !statement.matches("(?is).*\\b(and|or)\\b.*"));
            quality.put("testable", statement.toLowerCase().contains(" shall "));
            quality.put("acceptanceCriteriaCount", node.path("acceptance_criteria").size());
            SrsRequirementEntity requirement = SrsRequirementEntity.builder()
                    .srsVersion(version).requirementId(node.path("id").asText()).requirementType(node.path("type").asText())
                    .priority(node.path("priority").asText()).statement(statement).rationale(node.path("rationale").asText())
                    .acceptanceCriteria(joinText(node.path("acceptance_criteria"))).sourceKind(node.path("source_kind").asText())
                    .sourceDetail(node.path("source_detail").asText()).verificationMethod(node.path("verification_method").asText())
                    .qualityOutcome(quality).build();
            requirement = requirementRepository.save(requirement);
            for (JsonNode citation : node.path("citations")) {
                UUID chunkId = UUID.fromString(citation.path("chunk_id").asText());
                KnowledgeAiClient.RetrievedEvidence hit = evidenceByChunk.get(chunkId);
                boolean projectBriefCitation = PROJECT_BRIEF_SOURCE_ID.equals(hit.sourceId())
                        && PROJECT_BRIEF_CHUNK_ID.equals(hit.chunkId());
                // The interview brief is authoritative project evidence, but it is
                // not persisted as a knowledge source/chunk. Do not store its
                // internal anchor UUIDs in foreign-key columns.
                traceRepository.save(RequirementTraceLinkEntity.builder().srsRequirement(requirement)
                        .knowledgeSourceId(projectBriefCitation ? null : hit.sourceId())
                        .knowledgeChunkId(projectBriefCitation ? null : chunkId)
                        .linkType(projectBriefCitation ? "PROJECT_BRIEF" : "EVIDENCE").build());
            }
        }
    }

    private KnowledgeDtos.SrsVersionResponse response(SrsVersionEntity version) {
        List<KnowledgeDtos.RequirementResponse> requirements = requirementRepository.findBySrsVersionIdOrderByRequirementIdAsc(version.getId()).stream()
                .map(requirement -> new KnowledgeDtos.RequirementResponse(requirement.getId(), requirement.getRequirementId(),
                        requirement.getRequirementType(), requirement.getPriority(), requirement.getStatement(), requirement.getRationale(),
                        requirement.getAcceptanceCriteria(), requirement.getSourceKind(), requirement.getSourceDetail(), requirement.getVerificationMethod(),
                        requirement.getQualityOutcome(), traceRepository.findBySrsRequirementId(requirement.getId()).stream()
                                .map(link -> new KnowledgeDtos.TraceLinkResponse(link.getKnowledgeSourceId(), link.getKnowledgeChunkId(), link.getLinkType())).toList()))
                .toList();
        return new KnowledgeDtos.SrsVersionResponse(version.getId(), version.getVersionNumber(), version.getStatus().name(),
                version.getProfile().getProfileKey(), version.getSrsContent(), version.getValidationOutcome(), version.getCitationCoverage().doubleValue(),
                version.getProvider(), version.getModel(), version.getPromptVersion(), version.getGeneratedAt(), version.getApprovedAt(),
                version.getChangeRequest(), requirements);
    }

    private KnowledgeDtos.StandardsProfileResponse profileResponse(StandardsProfileEntity profile) {
        List<String> controls = new ArrayList<>();
        profile.getControls().forEach(node -> controls.add(node.asText()));
        return new KnowledgeDtos.StandardsProfileResponse(profile.getProfileKey(), profile.getName(), profile.getDescription(), controls,
                profile.getSourceLicense(), profile.getOwnerName(), profile.getEffectiveDate());
    }

    private SrsVersionEntity requiredVersion(UUID projectId, UUID versionId, UUID ownerId) {
        return versionRepository.findByIdAndProjectIdAndOwnerId(versionId, projectId, ownerId)
                .orElseThrow(() -> new KnowledgeStateException("SRS version not found.", HttpStatus.NOT_FOUND));
    }

    private ProjectEntity ownedProject(UUID projectId, UUID ownerId) {
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        if (!project.getOwner().getId().equals(ownerId)) throw new ProjectAccessDeniedException();
        return project;
    }

    private boolean blank(JsonNode node, String field) { return node.path(field).asText().isBlank(); }
    private String joinText(JsonNode values) { List<String> result = new ArrayList<>(); values.forEach(value -> result.add(value.asText())); return String.join("\n", result); }
}
