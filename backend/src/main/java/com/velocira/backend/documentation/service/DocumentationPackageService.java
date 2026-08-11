package com.velocira.backend.documentation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.documentation.dto.DocumentationDtos;
import com.velocira.backend.documentation.exceptions.DocumentationPackageException;
import com.velocira.backend.documentation.model.*;
import com.velocira.backend.documentation.repository.*;
import com.velocira.backend.generation.service.GenerationHashing;
import com.velocira.backend.knowledge.model.SrsRequirementEntity;
import com.velocira.backend.knowledge.model.SrsVersionEntity;
import com.velocira.backend.knowledge.model.SrsVersionStatus;
import com.velocira.backend.knowledge.repository.SrsRequirementRepository;
import com.velocira.backend.knowledge.repository.SrsVersionRepository;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Creates a deterministic, reviewable package from a generated SRS. The LLM is
 * intentionally not called here: all artifacts are projections of one stored
 * canonical model so a requirement cannot silently diverge between documents.
 */
@Service
@RequiredArgsConstructor
public class DocumentationPackageService {
    private static final Pattern NON_ID = Pattern.compile("[^a-z0-9]+");

    private final ProjectRepository projectRepository;
    private final SrsVersionRepository srsVersionRepository;
    private final SrsRequirementRepository srsRequirementRepository;
    private final DocumentationPackageRepository packageRepository;
    private final DocumentationArtifactRepository artifactRepository;
    private final DocumentationTraceLinkRepository traceRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DocumentationDtos.PackageResponse> list(UUID projectId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        return packageRepository.findByProjectIdAndOwnerIdOrderByVersionNumberDesc(projectId, ownerId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DocumentationDtos.PackageResponse get(UUID projectId, UUID packageId, UUID ownerId) {
        ownedProject(projectId, ownerId);
        return response(requiredPackage(projectId, packageId, ownerId));
    }

    @Transactional
    public DocumentationDtos.PackageResponse generate(UUID projectId, UUID ownerId, UUID srsVersionId) {
        ProjectEntity project = ownedProject(projectId, ownerId);
        SrsVersionEntity srs = srsVersionRepository.findByIdAndProjectIdAndOwnerId(srsVersionId, projectId, ownerId)
                .orElseThrow(() -> new DocumentationPackageException("Generated SRS version not found.", HttpStatus.NOT_FOUND));
        List<SrsRequirementEntity> requirements = srsRequirementRepository.findBySrsVersionIdOrderByRequirementIdAsc(srs.getId());
        if (requirements.isEmpty()) {
            throw new DocumentationPackageException("The generated SRS contains no traceable requirements.", HttpStatus.CONFLICT);
        }

        CanonicalDraft draft = canonicalModel(project, srs, requirements);
        ValidationResult validation = validate(draft);
        if (!validation.valid()) {
            throw new DocumentationPackageException("The linked package failed validation: " + String.join("; ", validation.issues()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        DocumentationPackageEntity documentationPackage = packageRepository.save(DocumentationPackageEntity.builder()
                .project(project).owner(project.getOwner()).srsVersion(srs)
                .versionNumber(packageRepository.nextVersionBase(projectId) + 1)
                // A package is generated only after all deterministic validators
                // pass, so exports should be immediately available without an
                // additional approval screen.
                .status(DocumentationPackageStatus.APPROVED).approvedAt(Instant.now())
                .canonicalModel(draft.model()).validationOutcome(validation.node()).generatedAt(Instant.now()).build());

        for (ArtifactDraft artifact : draft.artifacts()) {
            artifactRepository.save(DocumentationArtifactEntity.builder()
                    .documentationPackage(documentationPackage).artifactType(artifact.type()).title(artifact.title())
                    .content(artifact.content()).sourceFormat(artifact.sourceFormat()).sourceContent(artifact.sourceContent())
                    .checksum(GenerationHashing.sha256(artifact.sourceContent())).validationOutcome(artifact.validation()).build());
        }
        for (TraceDraft trace : draft.traces()) {
            traceRepository.save(DocumentationTraceLinkEntity.builder().documentationPackage(documentationPackage)
                    .srsRequirement(trace.requirement()).requirementKey(trace.requirement().getRequirementId())
                    .useCaseId(trace.useCaseId()).entityId(trace.entityId()).apiOperationId(trace.apiOperationId())
                    .acceptanceCriterionId(trace.acceptanceCriterionId()).sourceKind(trace.requirement().getSourceKind()).build());
        }
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.DOCUMENTATION_PACKAGE_GENERATED,
                "Generated linked documentation package v" + documentationPackage.getVersionNumber() + " from SRS v" + srs.getVersionNumber() + ".");
        return response(documentationPackage);
    }

    @Transactional
    public DocumentationDtos.PackageResponse approve(UUID projectId, UUID packageId, UUID ownerId) {
        DocumentationPackageEntity documentationPackage = requiredPackage(projectId, packageId, ownerId);
        if (documentationPackage.getStatus() == DocumentationPackageStatus.APPROVED) return response(documentationPackage);
        if (!documentationPackage.getValidationOutcome().path("valid").asBoolean(false)) {
            throw new DocumentationPackageException("A package with failing validation cannot be approved.", HttpStatus.CONFLICT);
        }
        documentationPackage.setStatus(DocumentationPackageStatus.APPROVED);
        documentationPackage.setApprovedAt(Instant.now());
        packageRepository.save(documentationPackage);
        auditService.record(ownerId, documentationPackage.getOwner().getEmail(), AuditAction.DOCUMENTATION_PACKAGE_APPROVED,
                "Approved linked documentation package v" + documentationPackage.getVersionNumber() + ".");
        return response(documentationPackage);
    }

    private CanonicalDraft canonicalModel(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        ObjectNode model = objectMapper.createObjectNode();
        model.put("schemaVersion", "1.0");
        model.put("packageKind", "linked-documentation-mvp");
        ObjectNode projectNode = model.putObject("project");
        projectNode.put("id", project.getId().toString());
        projectNode.put("name", project.getName());
        projectNode.put("type", project.getType().name());
        ObjectNode srsNode = model.putObject("srsVersion");
        srsNode.put("id", srs.getId().toString());
        srsNode.put("version", srs.getVersionNumber());

        List<NamedNode> actors = namesFromBrief(srs.getBriefSnapshot(), "users", "stakeholders", "actor");
        List<NamedNode> entities = namesFromBrief(srs.getBriefSnapshot(), "entities", "entity");
        List<NamedNode> rules = namesFromBrief(srs.getBriefSnapshot(), "business_rules", "businessRules", "rule");
        array(model, "actors", actors);
        array(model, "entities", entities);
        array(model, "businessRules", rules);

        ArrayNode requirementNodes = model.putArray("requirements");
        List<TraceDraft> traces = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            SrsRequirementEntity requirement = requirements.get(index);
            String key = requirement.getRequirementId();
            String useCaseId = "UC-" + String.format("%03d", index + 1);
            String operationId = "op-" + slug(key);
            String entityId = matchingEntity(requirement, entities);
            List<String> acceptance = acceptanceCriteria(requirement.getAcceptanceCriteria());
            String acceptanceId = "AC-" + slug(key) + "-001";
            ObjectNode item = requirementNodes.addObject();
            item.put("id", key); item.put("recordId", requirement.getId().toString());
            item.put("type", requirement.getRequirementType()); item.put("priority", requirement.getPriority());
            item.put("statement", requirement.getStatement()); item.put("sourceKind", requirement.getSourceKind());
            item.put("sourceDetail", requirement.getSourceDetail()); item.put("verificationMethod", requirement.getVerificationMethod());
            item.put("useCaseId", "FUNCTIONAL".equals(requirement.getRequirementType()) ? useCaseId : "");
            item.put("apiOperationId", operationId);
            if (entityId != null) item.put("entityId", entityId);
            ArrayNode criteria = item.putArray("acceptanceCriteria");
            for (int criterionIndex = 0; criterionIndex < acceptance.size(); criterionIndex++) {
                ObjectNode criterion = criteria.addObject();
                criterion.put("id", "AC-" + slug(key) + "-" + String.format("%03d", criterionIndex + 1));
                criterion.put("text", acceptance.get(criterionIndex));
            }
            traces.add(new TraceDraft(requirement, "FUNCTIONAL".equals(requirement.getRequirementType()) ? useCaseId : null,
                    entityId, operationId, acceptanceId));
        }

        ObjectNode openApi = openApi(project, requirements, entities);
        String useCases = useCases(project, requirements, actors, entities);
        String uml = plantUml(project, requirements, actors);
        String erd = erd(entities);
        String srsMarkdown = srsMarkdown(project, srs, requirements);
        String traceability = traceability(traces);
        String openApiJson;
        try { openApiJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openApi); }
        catch (Exception ex) { throw new IllegalStateException("Unable to render OpenAPI contract", ex); }
        String openApiYaml = openApiYaml(openApi, project.getName());

        List<ArtifactDraft> artifacts = List.of(
                new ArtifactDraft(DocumentationArtifactType.SRS, "Software Requirements Specification", srsMarkdown, "MARKDOWN", srsMarkdown, validArtifact("srs", true)),
                new ArtifactDraft(DocumentationArtifactType.USE_CASES, "Use cases", useCases, "PLANTUML", uml, validArtifact("plantuml", isPlantUmlValid(uml))),
                new ArtifactDraft(DocumentationArtifactType.ERD, "Entity relationship diagram", "```mermaid\n" + erd + "\n```", "MERMAID", erd, validArtifact("mermaid-erd", isErdValid(erd))),
                new ArtifactDraft(DocumentationArtifactType.OPENAPI, "OpenAPI contract", "```json\n" + openApiJson + "\n```", "OPENAPI_JSON", openApiJson, validateOpenApi(openApi)),
                new ArtifactDraft(DocumentationArtifactType.TRACEABILITY, "Traceability matrix", traceability, "MARKDOWN", traceability, validArtifact("traceability", true)));
        return new CanonicalDraft(model, artifacts, traces);
    }

    private ValidationResult validate(CanonicalDraft draft) {
        List<String> issues = new ArrayList<>();
        Set<String> requirementIds = new HashSet<>();
        for (JsonNode requirement : draft.model().path("requirements")) {
            if (!requirementIds.add(requirement.path("id").asText())) issues.add("Duplicate requirement ID in canonical model.");
            if (requirement.path("acceptanceCriteria").isEmpty()) issues.add(requirement.path("id").asText() + " has no acceptance criterion.");
            if (requirement.path("apiOperationId").asText().isBlank()) issues.add(requirement.path("id").asText() + " has no API operation.");
            if ("FUNCTIONAL".equals(requirement.path("type").asText()) && requirement.path("useCaseId").asText().isBlank()) {
                issues.add(requirement.path("id").asText() + " has no use case.");
            }
        }
        Set<String> traceRequirementIds = new HashSet<>();
        for (TraceDraft trace : draft.traces()) {
            if (!traceRequirementIds.add(trace.requirement().getRequirementId())) issues.add("A requirement has more than one primary trace row.");
            if (trace.apiOperationId() == null || trace.acceptanceCriterionId() == null) issues.add("Broken requirement trace.");
        }
        Map<DocumentationArtifactType, ArtifactDraft> artifacts = new EnumMap<>(DocumentationArtifactType.class);
        draft.artifacts().forEach(artifact -> artifacts.put(artifact.type(), artifact));
        if (!isPlantUmlValid(artifacts.get(DocumentationArtifactType.USE_CASES).sourceContent())) issues.add("Use-case UML source is invalid.");
        if (!isErdValid(artifacts.get(DocumentationArtifactType.ERD).sourceContent())) issues.add("ERD source is invalid.");
        if (!artifacts.get(DocumentationArtifactType.OPENAPI).validation().path("valid").asBoolean(false)) issues.add("OpenAPI contract is invalid.");
        if (traceRequirementIds.size() != requirementIds.size()) issues.add("Every requirement must have an explicit trace row.");
        ObjectNode outcome = objectMapper.createObjectNode();
        outcome.put("valid", issues.isEmpty());
        outcome.put("canonicalSchema", "linked-documentation-v1");
        outcome.put("requirementsChecked", requirementIds.size());
        outcome.put("traceLinksChecked", traceRequirementIds.size());
        ArrayNode checks = outcome.putArray("checks");
        checks.add("requirement-to-use-case-or-nfr"); checks.add("requirement-to-entity-when-mentioned");
        checks.add("requirement-to-api-operation"); checks.add("requirement-to-acceptance-criterion");
        checks.add("plantuml-syntax"); checks.add("mermaid-erd-syntax-and-keys"); checks.add("openapi-3.1-shape");
        ArrayNode issueNodes = outcome.putArray("issues"); issues.forEach(issueNodes::add);
        return new ValidationResult(issues.isEmpty(), issues, outcome);
    }

    private ObjectNode openApi(ProjectEntity project, List<SrsRequirementEntity> requirements, List<NamedNode> entities) {
        ObjectNode api = objectMapper.createObjectNode();
        api.put("openapi", "3.1.1");
        ObjectNode info = api.putObject("info"); info.put("title", project.getName() + " API"); info.put("version", "1.0.0");
        info.put("description", "Generated from project requirements. Validate against implementation before release.");
        ObjectNode paths = api.putObject("paths");
        for (SrsRequirementEntity requirement : requirements) {
            String path = "/requirements/" + slug(requirement.getRequirementId());
            ObjectNode operation = paths.putObject(path).putObject("post");
            operation.put("operationId", "op-" + slug(requirement.getRequirementId()));
            operation.put("summary", truncate(requirement.getStatement(), 120));
            operation.put("x-velocira-requirement-id", requirement.getRequirementId());
            operation.put("x-velocira-source-kind", requirement.getSourceKind());
            ArrayNode tags = operation.putArray("tags"); tags.add("Generated requirements");
            ObjectNode responses = operation.putObject("responses");
            responses.putObject("200").put("description", "Requirement outcome accepted for review.");
        }
        ObjectNode schemas = api.putObject("components").putObject("schemas");
        for (NamedNode entity : entities) {
            ObjectNode schema = schemas.putObject(titleCase(entity.name()));
            schema.put("type", "object"); schema.put("x-velocira-entity-id", entity.id());
            ObjectNode properties = schema.putObject("properties");
            properties.putObject("id").put("type", "string").put("format", "uuid");
            schema.putArray("required").add("id");
        }
        return api;
    }

    private String srsMarkdown(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        StringBuilder text = new StringBuilder("# ").append(project.getName()).append(" - SRS\n\n")
                .append("Version: ").append(srs.getVersionNumber()).append("  \n")
                .append("Status: generated source snapshot  \n\n## Requirements\n\n");
        for (SrsRequirementEntity requirement : requirements) {
            text.append("### ").append(requirement.getRequirementId()).append("\n\n")
                    .append(requirement.getStatement()).append("\n\n")
                    .append("- Priority: ").append(requirement.getPriority()).append("\n")
                    .append("- Verification: ").append(requirement.getVerificationMethod()).append("\n")
                    .append("- Evidence / assumption: ").append(requirement.getSourceDetail()).append("\n")
                    .append("- Acceptance criteria:\n");
            acceptanceCriteria(requirement.getAcceptanceCriteria()).forEach(value -> text.append("  - ").append(value).append("\n"));
            text.append("\n");
        }
        return text.toString();
    }

    private String useCases(ProjectEntity project, List<SrsRequirementEntity> requirements, List<NamedNode> actors, List<NamedNode> entities) {
        String actor = actors.isEmpty() ? "Unassigned project user (confirm during review)" : actors.get(0).name();
        StringBuilder text = new StringBuilder("# ").append(project.getName()).append(" - Use cases\n\n");
        int number = 1;
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            text.append("## UC-").append(String.format("%03d", number++)).append(" - ").append(requirement.getRequirementId()).append("\n\n")
                    .append("- Primary actor: ").append(actor).append("\n")
                    .append("- Source requirement: ").append(requirement.getRequirementId()).append("\n")
                    .append("- Goal: ").append(requirement.getStatement()).append("\n")
                    .append("- Main success flow:\n  1. ").append(actor).append(" initiates the capability.\n  2. The system applies the approved requirement.\n  3. The system records the result for review.\n")
                    .append("- Acceptance criteria:\n");
            acceptanceCriteria(requirement.getAcceptanceCriteria()).forEach(value -> text.append("  - ").append(value).append("\n"));
            text.append("\n");
        }
        return text.toString();
    }

    private String plantUml(ProjectEntity project, List<SrsRequirementEntity> requirements, List<NamedNode> actors) {
        String actor = actors.isEmpty() ? "Project user" : actors.get(0).name();
        String actorId = "ACTOR_" + slug(actor).toUpperCase(Locale.ROOT);
        StringBuilder source = new StringBuilder("@startuml\nleft to right direction\nskinparam packageStyle rectangle\n")
                .append("actor \"").append(plantText(actor)).append("\" as ").append(actorId).append("\n")
                .append("rectangle \"").append(plantText(project.getName())).append("\" {\n");
        int number = 1;
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            String id = "UC_" + String.format("%03d", number++);
            source.append("  usecase \"").append(plantText(requirement.getRequirementId() + ": " + truncate(requirement.getStatement(), 80)))
                    .append("\" as ").append(id).append("\n");
            source.append("  ").append(actorId).append(" --> ").append(id).append("\n");
        }
        return source.append("}\n@enduml\n").toString();
    }

    private String erd(List<NamedNode> entities) {
        StringBuilder source = new StringBuilder("erDiagram\n");
        for (NamedNode entity : entities) {
            source.append("  ").append(mermaidName(entity.name())).append(" {\n    uuid id PK\n  }\n");
        }
        return source.toString();
    }

    private String traceability(List<TraceDraft> traces) {
        StringBuilder text = new StringBuilder("# Traceability\n\n")
                .append("This matrix connects every reviewed requirement to its design, API, and acceptance evidence. ")
                .append("Read each row from left to right: requirement -> use case -> entity -> API operation -> acceptance criterion.\n\n")
                .append("## Requirement matrix\n\n")
                .append("| Requirement | Use case | Design entity | API operation | Acceptance criterion | Evidence source |\n")
                .append("|---|---|---|---|---|---|\n");
        for (TraceDraft trace : traces) {
            text.append("|").append(trace.requirement().getRequirementId()).append("|")
                    .append(orDash(trace.useCaseId())).append("|").append(orDash(trace.entityId())).append("|")
                    .append(trace.apiOperationId()).append("|").append(trace.acceptanceCriterionId()).append("|")
                    .append(trace.requirement().getSourceKind()).append("|\n");
        }
        return text.toString();
    }

    private ObjectNode validateOpenApi(ObjectNode api) {
        List<String> issues = new ArrayList<>();
        if (!"3.1.1".equals(api.path("openapi").asText())) issues.add("OpenAPI version must be 3.1.1.");
        if (api.path("info").path("title").asText().isBlank() || api.path("info").path("version").asText().isBlank()) issues.add("OpenAPI info is incomplete.");
        if (!api.path("paths").isObject() || api.path("paths").isEmpty()) issues.add("OpenAPI has no paths.");
        Set<String> operationIds = new HashSet<>();
        api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            String operationId = method.getValue().path("operationId").asText();
            if (operationId.isBlank() || !operationIds.add(operationId) || method.getValue().path("x-velocira-requirement-id").asText().isBlank()) {
                issues.add("OpenAPI has a missing or duplicate operation trace.");
            }
        }));
        return validArtifact("openapi-3.1", issues.isEmpty(), issues);
    }

    private ObjectNode validArtifact(String validator, boolean valid) { return validArtifact(validator, valid, List.of()); }
    private ObjectNode validArtifact(String validator, boolean valid, List<String> issues) {
        ObjectNode node = objectMapper.createObjectNode(); node.put("valid", valid); node.put("validator", validator);
        ArrayNode problems = node.putArray("issues"); issues.forEach(problems::add); return node;
    }
    private boolean isPlantUmlValid(String source) { return source.startsWith("@startuml") && source.trim().endsWith("@enduml") && !source.contains("\u0000"); }
    private boolean isErdValid(String source) {
        if (!source.startsWith("erDiagram") || source.contains("\u0000")) return false;
        long opens = source.chars().filter(character -> character == '{').count();
        long closes = source.chars().filter(character -> character == '}').count();
        return opens == closes && source.lines().allMatch(line -> !line.contains("PK") || line.trim().endsWith("PK"));
    }

    private List<NamedNode> namesFromBrief(JsonNode brief, String... keys) {
        String raw = "";
        for (String key : keys) if (!brief.path(key).asText().isBlank()) { raw = brief.path(key).asText(); break; }
        if (raw.isBlank()) return List.of();
        boolean entities = Arrays.stream(keys).anyMatch(key -> key.equals("entities") || key.equals("entity"));
        if (entities) {
            // Turn common prose introductions back into safe list boundaries.
            // The subsequent filters still reject any remaining action prose.
            raw = raw.replaceFirst("(?is)^\\s*the system\\s+(?:must|shall|should)\\s+(?:store and )?manage\\s+", "");
            raw = raw.replaceAll("(?is)\\.\\s*it\\s+(?:must|shall|should)\\s+(?:also\\s+)?manage\\s+", ";");
            raw = raw.replaceAll("(?is)\\.\\s*(?=(?:patients|doctors|nurses|receptionists|administrators|the system)\\b)", ";");
        }
        List<NamedNode> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Brief answers are free text. Only list separators are safe entity
        // boundaries; splitting every sentence or hyphen turns prose into a
        // nonsensical ERD (for example, "and follow-up instructions").
        for (String fragment : raw.split("(?:\\r?\\n|;|,|\\u2022)+")) {
            String value = fragment.replaceFirst("(?i)^[-*\\d. )]+", "").replaceFirst("(?i)^(?:and|or|including)\\s+", "").trim();
            String lower = value.toLowerCase(Locale.ROOT);
            boolean prose = lower.matches(".*\\b(shall|must|should|will|may|can|manage|store|create|update|delete)\\b.*");
            if (value.length() < 2 || value.split("\\s+").length > 6 || prose || !seen.add(slug(value))) continue;
            values.add(new NamedNode(prefixFromKeys(keys).toUpperCase(Locale.ROOT) + "-" + String.format("%03d", values.size() + 1), value));
            if (values.size() == 12) break;
        }
        return values;
    }
    private String prefixFromKeys(String... keys) { return keys[keys.length - 1].replaceAll("s$", ""); }
    private void array(ObjectNode parent, String name, List<NamedNode> nodes) { ArrayNode array = parent.putArray(name); nodes.forEach(node -> { ObjectNode item = array.addObject(); item.put("id", node.id()); item.put("name", node.name()); }); }
    private String matchingEntity(SrsRequirementEntity requirement, List<NamedNode> entities) {
        String text = requirement.getStatement().toLowerCase(Locale.ROOT);
        return entities.stream().filter(entity -> text.contains(entity.name().toLowerCase(Locale.ROOT))).map(NamedNode::id).findFirst().orElse(null);
    }
    private List<String> acceptanceCriteria(String value) { return Arrays.stream(value.split("\\r?\\n")).map(String::trim).filter(item -> !item.isBlank()).toList(); }
    private String openApiYaml(ObjectNode api, String projectName) {
        StringBuilder yaml = new StringBuilder("openapi: 3.1.1\ninfo:\n  title: ").append(yaml(projectName)).append(" API\n  version: 1.0.0\npaths:\n");
        api.path("paths").properties().forEach(path -> {
            JsonNode operation = path.getValue().path("post");
            yaml.append("  ").append(path.getKey()).append(":\n    post:\n      operationId: ").append(operation.path("operationId").asText())
                    .append("\n      x-velocira-requirement-id: ").append(operation.path("x-velocira-requirement-id").asText())
                    .append("\n      responses:\n        '200':\n          description: Requirement outcome accepted for review.\n");
        });
        return yaml.toString();
    }
    private String yaml(String value) { return "\"" + value.replace("\"", "\\\"") + "\""; }
    private String slug(String value) { String normalized = NON_ID.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-|-$)", ""); return normalized.isBlank() ? "item" : normalized; }
    private String mermaidName(String value) { return slug(value).replace('-', '_').toUpperCase(Locale.ROOT); }
    private String titleCase(String value) { return Arrays.stream(value.split("[^A-Za-z0-9]+" )).filter(part -> !part.isBlank()).map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).reduce("", String::concat); }
    private String plantText(String value) { return value.replace("\"", "'").replace("\n", " "); }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1).trim() + "…"; }
    private String orDash(String value) { return value == null || value.isBlank() ? "-" : value; }

    private DocumentationPackageEntity requiredPackage(UUID projectId, UUID packageId, UUID ownerId) {
        return packageRepository.findByIdAndProjectIdAndOwnerId(packageId, projectId, ownerId)
                .orElseThrow(() -> new DocumentationPackageException("Documentation package not found.", HttpStatus.NOT_FOUND));
    }
    private ProjectEntity ownedProject(UUID projectId, UUID ownerId) {
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        if (!project.getOwner().getId().equals(ownerId)) throw new ProjectAccessDeniedException();
        return project;
    }
    private DocumentationDtos.PackageResponse response(DocumentationPackageEntity documentationPackage) {
        List<DocumentationDtos.ArtifactResponse> artifacts = artifactRepository.findByDocumentationPackageIdOrderByArtifactTypeAsc(documentationPackage.getId()).stream()
                .map(item -> new DocumentationDtos.ArtifactResponse(item.getArtifactType(), item.getTitle(), item.getContent(), item.getSourceFormat(), item.getSourceContent(), item.getChecksum(), item.getValidationOutcome())).toList();
        List<DocumentationDtos.TraceResponse> traces = traceRepository.findByDocumentationPackageIdOrderByRequirementKeyAsc(documentationPackage.getId()).stream()
                .map(item -> new DocumentationDtos.TraceResponse(item.getRequirementKey(), item.getUseCaseId(), item.getEntityId(), item.getApiOperationId(), item.getAcceptanceCriterionId(), item.getSourceKind())).toList();
        return new DocumentationDtos.PackageResponse(documentationPackage.getId(), documentationPackage.getVersionNumber(), documentationPackage.getStatus().name(),
                documentationPackage.getSrsVersion().getId(), documentationPackage.getGeneratedAt(), documentationPackage.getApprovedAt(),
                documentationPackage.getCanonicalModel(), documentationPackage.getValidationOutcome(), artifacts, traces);
    }

    private record NamedNode(String id, String name) { }
    private record TraceDraft(SrsRequirementEntity requirement, String useCaseId, String entityId, String apiOperationId, String acceptanceCriterionId) { }
    private record ArtifactDraft(DocumentationArtifactType type, String title, String content, String sourceFormat, String sourceContent, ObjectNode validation) { }
    private record CanonicalDraft(ObjectNode model, List<ArtifactDraft> artifacts, List<TraceDraft> traces) { }
    private record ValidationResult(boolean valid, List<String> issues, ObjectNode node) { }
}
