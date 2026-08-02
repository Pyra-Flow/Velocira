package com.velocira.backend.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.knowledge.client.KnowledgeAiClient;
import com.velocira.backend.knowledge.client.KnowledgeAiException;
import com.velocira.backend.knowledge.dto.KnowledgeDtos;
import com.velocira.backend.knowledge.exceptions.KnowledgeStateException;
import com.velocira.backend.knowledge.model.KnowledgeChunkEntity;
import com.velocira.backend.knowledge.model.KnowledgeSourceEntity;
import com.velocira.backend.knowledge.model.KnowledgeSourceStatus;
import com.velocira.backend.knowledge.repository.KnowledgeChunkRepository;
import com.velocira.backend.knowledge.repository.KnowledgeSourceRepository;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Text-only MVP evidence pipeline with explicit owner review before vector indexing. */
@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {
    private static final long MAX_UPLOAD_BYTES = 1_000_000;
    private static final int CHUNK_SIZE = 1_600;
    private static final int CHUNK_OVERLAP = 240;
    private static final Pattern CREDENTIAL = Pattern.compile("(?i)(?:-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----|\\b(?:api[_-]?key|password|secret)\\s*[:=]\\s*\\S{12,}|\\bAKIA[0-9A-Z]{16}\\b)");
    private static final Pattern INSTRUCTION_OVERRIDE = Pattern.compile("(?i)\\b(ignore|override|discard|reveal)\\b.{0,80}\\b(previous|system|instruction|prompt|secret|credential)s?\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b");

    private final ProjectRepository projectRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeAiClient knowledgeAiClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.SourceResponse> list(UUID projectId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        return sourceRepository.findByProjectIdAndOwnerIdAndStatusNotOrderByCreatedAtDesc(projectId, ownerId, KnowledgeSourceStatus.DELETED)
                .stream().map(this::response).toList();
    }

    @Transactional
    public KnowledgeDtos.SourceResponse upload(UUID projectId, UUID ownerId, String requestedTitle, MultipartFile file) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new KnowledgeStateException("Restore the project before adding evidence.", HttpStatus.CONFLICT);
        }
        if (file == null || file.isEmpty()) throw new KnowledgeStateException("Choose a non-empty text or Markdown file.", HttpStatus.BAD_REQUEST);
        if (file.getSize() > MAX_UPLOAD_BYTES) throw new KnowledgeStateException("Evidence uploads are limited to 1 MB in this MVP.", HttpStatus.PAYLOAD_TOO_LARGE);
        String contentType = file.getContentType() == null ? "text/plain" : file.getContentType().toLowerCase();
        String filename = safeFilename(file.getOriginalFilename());
        if (!(contentType.startsWith("text/") || contentType.equals("application/json") || filename.endsWith(".md") || filename.endsWith(".txt") || filename.endsWith(".json"))) {
            throw new KnowledgeStateException("Only UTF-8 text, Markdown, or JSON evidence is supported in this MVP.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        String text;
        try { text = new String(file.getBytes(), StandardCharsets.UTF_8).trim(); }
        catch (IOException ex) { throw new KnowledgeStateException("The evidence file could not be read.", HttpStatus.BAD_REQUEST); }
        if (text.isBlank()) throw new KnowledgeStateException("The evidence file has no readable text.", HttpStatus.BAD_REQUEST);
        if (text.length() > MAX_UPLOAD_BYTES) throw new KnowledgeStateException("The extracted evidence exceeds the 1 MB limit.", HttpStatus.PAYLOAD_TOO_LARGE);

        boolean credentialDetected = CREDENTIAL.matcher(text).find();
        boolean injectionDetected = INSTRUCTION_OVERRIDE.matcher(text).find();
        ObjectNode scan = objectMapper.createObjectNode();
        scan.put("scannerVersion", "governed-evidence-v1");
        scan.put("credentialDetected", credentialDetected);
        scan.put("promptInjectionDetected", injectionDetected);
        scan.put("possiblePiiDetected", EMAIL.matcher(text).find());
        scan.put("textOnlyMvp", true);
        KnowledgeSourceEntity source = KnowledgeSourceEntity.builder()
                .project(project).owner(project.getOwner())
                .title(requestedTitle == null || requestedTitle.isBlank() ? filename : requestedTitle.trim())
                .originalFilename(filename).mediaType(contentType).extractedText(text).contentSha256(sha256(text))
                .scanMetadata(scan)
                .status(credentialDetected || injectionDetected ? KnowledgeSourceStatus.QUARANTINED : KnowledgeSourceStatus.PENDING_REVIEW)
                .build();
        source = sourceRepository.save(source);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.KNOWLEDGE_SOURCE_UPLOADED,
                "Uploaded project evidence: " + source.getTitle());
        return response(source);
    }

    @Transactional
    public KnowledgeDtos.SourceResponse approve(UUID projectId, UUID sourceId, UUID ownerId) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        KnowledgeSourceEntity source = requiredSource(projectId, sourceId, ownerId);
        if (source.getStatus() != KnowledgeSourceStatus.PENDING_REVIEW) {
            throw new KnowledgeStateException("Only evidence awaiting review can be approved.", HttpStatus.CONFLICT);
        }
        if (source.getScanMetadata().path("credentialDetected").asBoolean() || source.getScanMetadata().path("promptInjectionDetected").asBoolean()) {
            throw new KnowledgeStateException("This source is quarantined by the safety scan and cannot be approved.", HttpStatus.CONFLICT);
        }
        List<KnowledgeChunkEntity> chunks = chunk(source);
        chunks = chunkRepository.saveAll(chunks);
        chunks.forEach(chunk -> chunk.setQdrantPointId(chunk.getId()));
        chunks = chunkRepository.saveAll(chunks);
        List<KnowledgeChunkEntity> indexed = new ArrayList<>();
        try {
            for (KnowledgeChunkEntity chunk : chunks) {
                knowledgeAiClient.index(new KnowledgeAiClient.IndexChunk(projectId, ownerId, source.getId(), chunk.getId(), source.getTitle(), chunk.getContent()));
                indexed.add(chunk);
            }
        } catch (KnowledgeAiException ex) {
            indexed.forEach(chunk -> { try { knowledgeAiClient.delete(projectId, ownerId, chunk.getId()); } catch (KnowledgeAiException ignored) { } });
            throw new KnowledgeStateException(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
        source.setStatus(KnowledgeSourceStatus.APPROVED);
        source.setApprovedAt(Instant.now());
        sourceRepository.save(source);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.KNOWLEDGE_SOURCE_APPROVED,
                "Approved and indexed project evidence: " + source.getTitle());
        return response(source);
    }

    @Transactional
    public KnowledgeDtos.SourceResponse reject(UUID projectId, UUID sourceId, UUID ownerId) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        KnowledgeSourceEntity source = requiredSource(projectId, sourceId, ownerId);
        if (source.getStatus() == KnowledgeSourceStatus.APPROVED) throw new KnowledgeStateException("Delete an approved source instead of rejecting it.", HttpStatus.CONFLICT);
        source.setStatus(KnowledgeSourceStatus.REJECTED);
        sourceRepository.save(source);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.KNOWLEDGE_SOURCE_REJECTED,
                "Rejected project evidence: " + source.getTitle());
        return response(source);
    }

    @Transactional
    public void delete(UUID projectId, UUID sourceId, UUID ownerId) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        KnowledgeSourceEntity source = requiredSource(projectId, sourceId, ownerId);
        List<KnowledgeChunkEntity> chunks = chunkRepository.findBySourceIdOrderByChunkOrdinalAsc(sourceId);
        try {
            for (KnowledgeChunkEntity chunk : chunks) knowledgeAiClient.delete(projectId, ownerId, chunk.getId());
        } catch (KnowledgeAiException ex) {
            throw new KnowledgeStateException(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }
        chunkRepository.deleteAll(chunks);
        source.setStatus(KnowledgeSourceStatus.DELETED);
        source.setDeletedAt(Instant.now());
        sourceRepository.save(source);
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.KNOWLEDGE_SOURCE_DELETED,
                "Removed project evidence from retrieval: " + source.getTitle());
    }

    private List<KnowledgeChunkEntity> chunk(KnowledgeSourceEntity source) {
        List<KnowledgeChunkEntity> chunks = new ArrayList<>();
        String content = source.getExtractedText();
        int start = 0;
        int ordinal = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + CHUNK_SIZE);
            if (end < content.length()) {
                int breakAt = content.lastIndexOf(' ', end);
                if (breakAt > start + (CHUNK_SIZE / 2)) end = breakAt;
            }
            String excerpt = content.substring(start, end).trim();
            if (!excerpt.isBlank()) chunks.add(KnowledgeChunkEntity.builder()
                    .source(source).project(source.getProject()).owner(source.getOwner()).chunkOrdinal(ordinal++)
                    .content(excerpt).contentSha256(sha256(excerpt)).qdrantPointId(UUID.randomUUID()).build());
            if (end >= content.length()) break;
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private KnowledgeDtos.SourceResponse response(KnowledgeSourceEntity source) {
        return new KnowledgeDtos.SourceResponse(source.getId(), source.getTitle(), source.getOriginalFilename(), source.getMediaType(),
                source.getClassification(), source.getStatus().name(), source.getScanMetadata(),
                chunkRepository.findBySourceIdOrderByChunkOrdinalAsc(source.getId()).size(), source.getApprovedAt(), source.getCreatedAt());
    }

    private KnowledgeSourceEntity requiredSource(UUID projectId, UUID sourceId, UUID ownerId) {
        return sourceRepository.findByIdAndProjectIdAndOwnerId(sourceId, projectId, ownerId)
                .orElseThrow(() -> new KnowledgeStateException("Evidence source not found.", HttpStatus.NOT_FOUND));
    }

    private ProjectEntity ownedProject(UUID projectId, UUID ownerId) {
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        if (!project.getOwner().getId().equals(ownerId)) throw new ProjectAccessDeniedException();
        return project;
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "evidence.txt" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        return value.isBlank() ? "evidence.txt" : value.substring(0, Math.min(value.length(), 255));
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
}
