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
    private static final Pattern APPLICABILITY_SIGNALS = Pattern.compile("(?i)applicable context signals?\\s*:\\s*([^.;]+)");
    private static final Pattern ERD_RELATIONSHIP = Pattern.compile("(?m)^\\s*[A-Za-z0-9_]+\\s+[|o}{.]+--[|o}{.]+\\s+[A-Za-z0-9_]+(?:\\s*:\\s*.+)?\\s*$");
    private static final Pattern ERD_RELATIONSHIP_CAPTURE = Pattern.compile("(?m)^\\s*([A-Za-z0-9_]+)\\s+[|o}{.]+--[|o}{.]+\\s+([A-Za-z0-9_]+)(?:\\s*:\\s*(.+))?\\s*$");
    private static final Pattern ERD_ENTITY = Pattern.compile("(?m)^\\s*[A-Za-z0-9_]+\\s*\\{\\s*$");
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

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
        boolean exhaustive = "EXHAUSTIVE".equalsIgnoreCase(srs.getSrsContent().path("generation_manifest").path("mode").asText());
        if (exhaustive && "deterministic-srs-fallback".equalsIgnoreCase(srs.getModel())) {
            throw new DocumentationPackageException(
                    "The exhaustive SRS was produced by a reduced emergency fallback and is not eligible for document packaging. Regenerate it with the configured document model first.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        CanonicalDraft draft = canonicalModel(project, srs, requirements);
        ValidationResult validation = validate(draft);
        if (!validation.valid()) {
            throw new DocumentationPackageException("The linked package failed validation: " + String.join("; ", validation.issues()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        boolean approvalEligible = validation.node().path("approvalEligible").asBoolean(false);
        Instant generatedAt = Instant.now();
        DocumentationPackageEntity documentationPackage = packageRepository.save(DocumentationPackageEntity.builder()
                .project(project).owner(project.getOwner()).srsVersion(srs)
                .versionNumber(packageRepository.nextVersionBase(projectId) + 1)
                .status(approvalEligible ? DocumentationPackageStatus.APPROVED : DocumentationPackageStatus.NEEDS_REVIEW)
                .approvedAt(approvalEligible ? generatedAt : null)
                .canonicalModel(draft.model()).validationOutcome(validation.node()).generatedAt(generatedAt).build());

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
        if (!documentationPackage.getValidationOutcome().path("valid").asBoolean(false)
                || !documentationPackage.getValidationOutcome().path("approvalEligible").asBoolean(false)) {
            throw new DocumentationPackageException("This package is not approval-eligible. Resolve the recorded material gaps and regenerate it from the canonical SRS.", HttpStatus.CONFLICT);
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
        model.put("schemaVersion", "2.0");
        model.put("packageKind", "standards-informed-documentation-compiler");
        ObjectNode projectNode = model.putObject("project");
        projectNode.put("id", project.getId().toString());
        projectNode.put("name", project.getName());
        projectNode.put("type", project.getType().name());
        ObjectNode srsNode = model.putObject("srsVersion");
        srsNode.put("id", srs.getId().toString());
        srsNode.put("version", srs.getVersionNumber());
        srsNode.put("status", srs.getStatus().name());
        srsNode.put("provider", srs.getProvider());
        srsNode.put("model", srs.getModel());
        srsNode.put("contentSha256", GenerationHashing.sha256(srs.getSrsContent().toString()));

        List<NamedNode> actors = namesFromBrief(srs.getBriefSnapshot(), "users", "stakeholders", "actor");
        List<NamedNode> entities = canonicalEntities(srs);
        List<NamedNode> rules = namesFromBrief(srs.getBriefSnapshot(), "business_rules", "businessRules", "rule");
        List<NamedNode> integrations = namesFromBrief(srs.getBriefSnapshot(), "integrations", "integration");
        array(model, "actors", actors);
        array(model, "entities", entities);
        array(model, "businessRules", rules);
        array(model, "integrations", integrations);
        model.set("srs", srs.getSrsContent().deepCopy());
        model.set("standardsApplied", applicableStandards(srs));
        model.set("qualityScenarios", srs.getSrsContent().path("quality_scenarios").deepCopy());
        model.set("decisions", srs.getSrsContent().path("decisions").deepCopy());
        model.set("generationManifest", srs.getSrsContent().path("generation_manifest").deepCopy());
        model.set("sourceRegistry", srs.getSrsContent().path("source_registry").deepCopy());
        model.set("terminologyRegistry", srs.getSrsContent().path("definitions").deepCopy());
        JsonNode compiledWorkflow = srs.getSrsContent().path("workflows");
        model.set("workflows", compiledWorkflow.deepCopy());
        ArrayNode workflowSteps = model.putArray("workflowSteps");
        if (compiledWorkflow.isArray() && !compiledWorkflow.isEmpty()) compiledWorkflow.forEach(workflow ->
                workflow.path("main_flow").forEach(step -> workflowSteps.add(step.asText())));
        else splitBriefSteps(srs.getBriefSnapshot().path("workflows").asText()).forEach(workflowSteps::add);

        ArrayNode requirementNodes = model.putArray("requirements");
        List<TraceDraft> traces = new ArrayList<>();
        Map<String, ApiOperation> apiOperations = new LinkedHashMap<>();
        Set<String> apiEndpointKeys = new HashSet<>();
        Set<String> apiOperationIds = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            SrsRequirementEntity requirement = requirements.get(index);
            String key = requirement.getRequirementId();
            String useCaseId = "UC-" + String.format("%03d", index + 1);
            JsonNode detail = srsRequirementDetail(srs, key);
            ApiOperation operation = "API".equals(requirement.getRequirementType()) ? confirmedApiOperation(detail) : null;
            if (operation != null && !apiOperationMatchesLocalSource(detail, requirement, operation)) {
                operation = null;
            }
            if (operation != null) {
                String endpointKey = operation.method() + " " + operation.path();
                if (apiEndpointKeys.contains(endpointKey) || apiOperationIds.contains(operation.operationId())) {
                    operation = null;
                } else {
                    apiEndpointKeys.add(endpointKey);
                    apiOperationIds.add(operation.operationId());
                }
            }
            String operationId = operation == null ? null : operation.operationId();
            if (operation != null) apiOperations.put(key, operation);
            String entityId = matchingEntity(requirement, entities);
            List<String> acceptance = acceptanceCriteria(requirement.getAcceptanceCriteria());
            String acceptanceId = "AC-" + slug(key) + "-001";
            ObjectNode item = detail.isObject() ? (ObjectNode) detail.deepCopy() : objectMapper.createObjectNode();
            requirementNodes.add(item);
            item.put("id", key); item.put("recordId", requirement.getId().toString());
            item.put("title", detail.path("title").asText(requirementTitle(requirement)));
            item.put("type", requirement.getRequirementType()); item.put("priority", requirement.getPriority());
            item.put("statement", requirement.getStatement()); item.put("sourceKind", requirement.getSourceKind());
            item.put("sourceDetail", requirement.getSourceDetail()); item.put("verificationMethod", requirement.getVerificationMethod());
            item.put("useCaseId", "FUNCTIONAL".equals(requirement.getRequirementType()) ? useCaseId : "");
            if (operationId != null) item.put("apiOperationId", operationId);
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

        ObjectNode openApi = openApi(project, requirements, apiOperations);
        String useCases = useCases(project, srs, requirements, actors, entities);
        String uml = plantUml(project, srs, requirements, actors);
        String erd = diagramSource(srs, "ERD", "");
        model.set("erdRelationships", erdRelationships(erd));
        String srsMarkdown = srsMarkdown(project, srs, requirements);
        String brd = businessRequirements(project, srs, requirements, actors, rules);
        String architecture = architecture(project, srs, requirements, actors, entities, integrations);
        String c4Context = diagramSource(srs, "C4_CONTEXT", actors.isEmpty() && integrations.isEmpty() ? "" : fallbackContextDiagram(project, actors, integrations));
        String workflows = workflows(project, srs, requirements, actors);
        String fallbackWorkflow = workflowSteps.size() >= 2 ? fallbackWorkflowDiagram(srs) : "";
        String workflowDiagram = diagramSource(srs, "WORKFLOW", fallbackWorkflow);
        String dataDictionary = dataDictionary(project, srs, requirements, entities);
        String security = securityAndPrivacy(project, srs, requirements);
        String testPlan = testPlan(project, srs, requirements);
        String deployment = deployment(project, srs, requirements);
        String operations = operations(project, srs, requirements);
        String userManual = userManual(project, srs, requirements, actors);
        String risks = riskRegister(project, srs);
        String traceability = traceability(project, srs, traces);
        String openApiJson;
        try { openApiJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openApi); }
        catch (Exception ex) { throw new IllegalStateException("Unable to render OpenAPI contract", ex); }

        List<ArtifactDraft> artifacts = new ArrayList<>();
        Map<DocumentationArtifactType, ArtifactDisposition> dispositions = new EnumMap<>(DocumentationArtifactType.class);
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.SRS, "Software Requirements Specification", srsMarkdown,
                "srs-compiler-v2", "REQUIRED", "Canonical governed requirement baseline.");
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.BRD, "Business Requirements Document", brd,
                "brd-compiler-v2", "REQUIRED", "Business scope and requirement rationale are present in the canonical SRS.");
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.ARCHITECTURE, "Architecture Description", architecture,
                "iso-42010-arc42", "REQUIRED", "Architecture views are constrained by the canonical SRS evidence and unresolved decisions.");

        boolean hasFunctional = requirements.stream().anyMatch(requirement -> "FUNCTIONAL".equals(requirement.getRequirementType()));
        if (hasFunctional) {
            artifacts.add(new ArtifactDraft(DocumentationArtifactType.USE_CASES, "Use cases", useCases, "PLANTUML", uml, validArtifact("plantuml", isPlantUmlValid(uml))));
            dispositions.put(DocumentationArtifactType.USE_CASES, new ArtifactDisposition("REQUIRED", "Functional requirements provide traceable use cases."));
        } else omit(dispositions, DocumentationArtifactType.USE_CASES, "No functional requirement supports a use-case artifact.");

        if (isMeaningfulFlow(c4Context)) {
            artifacts.add(new ArtifactDraft(DocumentationArtifactType.C4_CONTEXT, "C4 system context", "```mermaid\n" + c4Context + "\n```", "MERMAID", c4Context, validArtifact("mermaid-c4-context", true)));
            dispositions.put(DocumentationArtifactType.C4_CONTEXT, new ArtifactDisposition("REQUIRED", "Confirmed actors or integrations support a system-context view."));
        } else omit(dispositions, DocumentationArtifactType.C4_CONTEXT, "No confirmed actor or integration supports a non-empty context diagram.");

        if (isMeaningfulFlow(workflowDiagram)) {
            artifacts.add(new ArtifactDraft(DocumentationArtifactType.WORKFLOWS, "Workflow and recovery diagrams", workflows, "MERMAID", workflowDiagram, validArtifact("mermaid-workflow", true)));
            dispositions.put(DocumentationArtifactType.WORKFLOWS, new ArtifactDisposition("REQUIRED", "The canonical SRS contains an ordered workflow with at least two steps."));
        } else omit(dispositions, DocumentationArtifactType.WORKFLOWS, "No sufficiently detailed canonical workflow supports a diagram.");

        if (!entities.isEmpty()) addMarkdown(artifacts, dispositions, DocumentationArtifactType.DATA_DICTIONARY, "Data Dictionary", dataDictionary,
                "data-dictionary-v2", "REQUIRED", "Confirmed domain entities support a conceptual data register.");
        else omit(dispositions, DocumentationArtifactType.DATA_DICTIONARY, "No confirmed domain entity supports a data dictionary.");

        if (isErdValid(erd)) {
            artifacts.add(new ArtifactDraft(DocumentationArtifactType.ERD, "Entity relationship diagram", "```mermaid\n" + erd + "\n```", "MERMAID", erd, validArtifact("mermaid-erd", true)));
            dispositions.put(DocumentationArtifactType.ERD, new ArtifactDisposition("REQUIRED", "The canonical SRS records at least two entities and an explicit relationship."));
        } else omit(dispositions, DocumentationArtifactType.ERD, "Entity names alone do not justify relationship cardinality; no ERD is emitted until the canonical SRS records a relationship.");

        if (!openApi.path("paths").isEmpty()) {
            artifacts.add(new ArtifactDraft(DocumentationArtifactType.OPENAPI, "OpenAPI contract", "```json\n" + openApiJson + "\n```", "OPENAPI_JSON", openApiJson, validateOpenApi(openApi)));
            dispositions.put(DocumentationArtifactType.OPENAPI, new ArtifactDisposition("REQUIRED", "Confirmed paths, methods, operation IDs, and requirement traces support an API contract."));
        } else omit(dispositions, DocumentationArtifactType.OPENAPI, apiOperations.isEmpty()
                ? "No API operation has a confirmed path, method, and operation ID; a zero-endpoint contract would be misleading."
                : "No valid traceable API operation remains after validation.");

        boolean hasSecurityEvidence = hasRequirementType(requirements, "SECURITY", "PRIVACY") || !srs.getSrsContent().path("risks").isEmpty();
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.SECURITY, "Security and Privacy Specification", security,
                "security-privacy-v2", hasSecurityEvidence ? "REQUIRED" : "PROPOSED",
                hasSecurityEvidence ? "Canonical security, privacy, or risk evidence requires review." : "A bounded security applicability review is proposed; no project-specific control is claimed.");
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.TEST_PLAN, "Verification and Test Plan", testPlan,
                "iso-29119-v2", "REQUIRED", "Every canonical requirement has acceptance and verification evidence.");

        if (hasRequirementType(requirements, "OPERATIONS", "NON_FUNCTIONAL") || meaningfulBrief(srs, "constraints")) {
            addMarkdown(artifacts, dispositions, DocumentationArtifactType.DEPLOYMENT, "Deployment Specification", deployment,
                    "deployment-v2", "REQUIRED", "Confirmed constraints and operational requirements bound the release controls.");
            addMarkdown(artifacts, dispositions, DocumentationArtifactType.OPERATIONS, "Operations Runbook", operations,
                    "operations-v2", "REQUIRED", "Operational requirements support a reviewable runbook; unresolved decisions remain explicit.");
        } else {
            omit(dispositions, DocumentationArtifactType.DEPLOYMENT, "No canonical deployment constraint or operational requirement supports a deployment specification.");
            omit(dispositions, DocumentationArtifactType.OPERATIONS, "No canonical service objective or operational requirement supports a runbook.");
        }

        if (hasFunctional && !actors.isEmpty()) addMarkdown(artifacts, dispositions, DocumentationArtifactType.USER_MANUAL, "User Manual", userManual,
                "user-manual-v2", "REQUIRED", "Confirmed actors and functional goals support a pre-implementation task guide.");
        else omit(dispositions, DocumentationArtifactType.USER_MANUAL, "A user manual requires both confirmed actors and functional requirements.");

        addMarkdown(artifacts, dispositions, DocumentationArtifactType.RISK_REGISTER, "Risk and Decision Register", risks,
                "iso-31000-v2", "REQUIRED", "Risks and missing decisions must remain visible to reviewers.");
        addMarkdown(artifacts, dispositions, DocumentationArtifactType.TRACEABILITY, "Traceability Matrix", traceability,
                "traceability-v2", "REQUIRED", "Every canonical requirement has a primary trace row and acceptance link.");
        model.set("documentPlan", documentPlan(dispositions));
        return new CanonicalDraft(model, artifacts, traces, dispositions);
    }

    private ValidationResult validate(CanonicalDraft draft) {
        List<String> issues = new ArrayList<>();
        List<String> materialGaps = new ArrayList<>();
        Set<String> requirementIds = new HashSet<>();
        for (JsonNode requirement : draft.model().path("requirements")) {
            if (!requirementIds.add(requirement.path("id").asText())) issues.add("Duplicate requirement ID in canonical model.");
            if (requirement.path("acceptanceCriteria").isEmpty()) issues.add(requirement.path("id").asText() + " has no acceptance criterion.");
            if ("FUNCTIONAL".equals(requirement.path("type").asText()) && requirement.path("useCaseId").asText().isBlank()) {
                issues.add(requirement.path("id").asText() + " has no use case.");
            }
        }
        Set<String> traceRequirementIds = new HashSet<>();
        for (TraceDraft trace : draft.traces()) {
            if (!traceRequirementIds.add(trace.requirement().getRequirementId())) issues.add("A requirement has more than one primary trace row.");
            if (trace.acceptanceCriterionId() == null) issues.add("Broken requirement trace.");
        }
        Map<DocumentationArtifactType, ArtifactDraft> artifacts = new EnumMap<>(DocumentationArtifactType.class);
        for (ArtifactDraft artifact : draft.artifacts()) {
            if (artifacts.put(artifact.type(), artifact) != null) issues.add("Duplicate package artifact: " + artifact.type() + ".");
            if (artifact.sourceContent().isBlank()) issues.add("Emitted artifact " + artifact.type() + " has no authoritative source content.");
            else if (!artifact.validation().path("valid").asBoolean(false)) issues.add(artifact.type() + " failed its artifact validator.");
        }
        for (Map.Entry<DocumentationArtifactType, ArtifactDisposition> planned : draft.dispositions().entrySet()) {
            boolean emitted = artifacts.containsKey(planned.getKey());
            if ("OMITTED".equals(planned.getValue().status()) && emitted) {
                issues.add(planned.getKey() + " is marked omitted but was emitted.");
            } else if (!"OMITTED".equals(planned.getValue().status()) && !emitted) {
                issues.add("Planned artifact was not emitted: " + planned.getKey() + ".");
            }
            if (planned.getValue().reason().isBlank()) issues.add(planned.getKey() + " has no artifact-disposition rationale.");
            if ("PROPOSED".equals(planned.getValue().status())) {
                materialGaps.add(planned.getKey() + " remains proposed: " + planned.getValue().reason());
            }
        }
        if (artifacts.containsKey(DocumentationArtifactType.USE_CASES)
                && !isPlantUmlValid(artifacts.get(DocumentationArtifactType.USE_CASES).sourceContent())) issues.add("Use-case UML source is invalid.");
        if (artifacts.containsKey(DocumentationArtifactType.C4_CONTEXT)
                && !isMeaningfulFlow(artifacts.get(DocumentationArtifactType.C4_CONTEXT).sourceContent())) issues.add("C4 context source is empty or invalid.");
        if (artifacts.containsKey(DocumentationArtifactType.WORKFLOWS)
                && !isMeaningfulFlow(artifacts.get(DocumentationArtifactType.WORKFLOWS).sourceContent())) issues.add("Workflow source is empty or invalid.");
        if (artifacts.containsKey(DocumentationArtifactType.ERD)
                && !isErdValid(artifacts.get(DocumentationArtifactType.ERD).sourceContent())) issues.add("ERD must contain at least two entities and one explicit relationship.");
        if (artifacts.containsKey(DocumentationArtifactType.OPENAPI)
                && !artifacts.get(DocumentationArtifactType.OPENAPI).validation().path("valid").asBoolean(false)) issues.add("OpenAPI contract is invalid.");
        if (traceRequirementIds.size() != requirementIds.size()) issues.add("Every requirement must have an explicit trace row.");
        for (String collection : List.of("actors", "entities", "integrations")) {
            for (JsonNode item : draft.model().path(collection)) {
                if (!isMeaningfulBriefValue(item.path("name").asText(""))) {
                    issues.add("Canonical " + collection + " contains an empty or placeholder label.");
                }
            }
        }
        ObjectNode outcome = objectMapper.createObjectNode();
        outcome.put("canonicalSchema", "linked-documentation-v2");
        outcome.put("requirementsChecked", requirementIds.size());
        outcome.put("traceLinksChecked", traceRequirementIds.size());
        outcome.put("artifactsChecked", artifacts.size());
        outcome.put("artifactsOmitted", draft.dispositions().values().stream().filter(item -> "OMITTED".equals(item.status())).count());
        outcome.put("artifactsProposed", draft.dispositions().values().stream().filter(item -> "PROPOSED".equals(item.status())).count());
        int totalWords = artifacts.values().stream().mapToInt(artifact -> wordCount(artifact.content())).sum();
        outcome.put("totalWords", totalWords);
        String generationMode = draft.model().path("generationManifest").path("mode").asText("STANDARD").toUpperCase(Locale.ROOT);
        boolean exhaustive = "EXHAUSTIVE".equals(generationMode);
        outcome.put("generationMode", generationMode);
        int declaredPackageWordTarget = draft.model().path("generationManifest").path("long_form_provenance")
                .path("expected_package_word_target").asInt(0);
        outcome.put("declaredPackageWordTarget", declaredPackageWordTarget);
        outcome.put("wordTargetSource", "canonical-srs.generation_manifest.long_form_provenance.expected_package_word_target");
        if (exhaustive && declaredPackageWordTarget <= 0) {
            issues.add("The canonical exhaustive manifest does not declare expected_package_word_target.");
        } else if (declaredPackageWordTarget > 0 && totalWords < declaredPackageWordTarget) {
            issues.add("The canonical manifest requires at least " + declaredPackageWordTarget + " useful words across the linked artifacts; generated " + totalWords + ".");
        }
        JsonNode narrativeSections = draft.model().path("srs").path("narrative_sections");
        int narrativeSectionCount = narrativeSections.isArray() ? narrativeSections.size() : 0;
        int narrativeWords = 0;
        Set<String> narrativeIds = new HashSet<>();
        Set<String> narrativeBodies = new HashSet<>();
        if (narrativeSections.isArray()) for (JsonNode section : narrativeSections) {
            String id = section.path("id").asText();
            String body = section.path("content").asText();
            narrativeWords += wordCount(body);
            if (id.isBlank() || !narrativeIds.add(id)) issues.add("Narrative sections must have unique stable IDs.");
            String normalizedBody = body.replaceAll("\\W+", " ").trim().toLowerCase(Locale.ROOT);
            if (!normalizedBody.isBlank() && !narrativeBodies.add(normalizedBody)) {
                issues.add("Narrative sections contain repeated boilerplate.");
            }
            if (body.matches("(?is).*\\b(?:tbd|lorem ipsum)\\b.*") || body.contains("{{") || body.contains("}}")) {
                issues.add("Narrative section " + id + " contains an unresolved template marker.");
            }
        }
        outcome.put("narrativeSectionsChecked", narrativeSectionCount);
        outcome.put("narrativeWords", narrativeWords);
        int contractedSections = draft.model().path("generationManifest").path("section_contracts").isArray()
                ? draft.model().path("generationManifest").path("section_contracts").size() : 0;
        if (contractedSections > 0 && narrativeSectionCount < contractedSections) {
            issues.add("The canonical manifest declares " + contractedSections + " narrative section contracts but only " + narrativeSectionCount + " are present.");
        }
        outcome.put("contractedNarrativeSections", contractedSections);
        outcome.put("wordTargetMet", declaredPackageWordTarget <= 0 || totalWords >= declaredPackageWordTarget);
        outcome.put("requirementTraceabilityCoverage", requirementIds.isEmpty() ? 0 : Math.round(traceRequirementIds.size() * 10_000.0 / requirementIds.size()) / 100.0);

        if (!"APPROVED".equals(draft.model().path("srsVersion").path("status").asText())) {
            materialGaps.add("The canonical SRS is not approved.");
        }
        String manifestStatus = draft.model().path("generationManifest").path("validation_status").asText();
        if (!manifestStatus.isBlank() && !"PASSED".equalsIgnoreCase(manifestStatus)) {
            materialGaps.add("The canonical generation manifest is not in PASSED state.");
        }
        for (JsonNode decision : draft.model().path("decisions")) {
            if (Set.of("UNRESOLVED", "OPEN", "NEEDS_REVIEW").contains(decision.path("status").asText().toUpperCase(Locale.ROOT))) {
                materialGaps.add("Open decision " + decision.path("id").asText("without ID") + " requires resolution.");
            }
        }
        Set<String> tracedOperations = new HashSet<>();
        draft.traces().stream().map(TraceDraft::apiOperationId).filter(Objects::nonNull).forEach(tracedOperations::add);
        for (JsonNode requirement : draft.model().path("requirements")) {
            if ("API".equals(requirement.path("type").asText()) && requirement.path("apiOperationId").asText().isBlank()) {
                materialGaps.add("API requirement " + requirement.path("id").asText() + " has no confirmed path, method, and operation ID; OpenAPI is omitted for that requirement.");
            }
        }
        boolean hasFunctionalRequirement = java.util.stream.StreamSupport.stream(draft.model().path("requirements").spliterator(), false)
                .anyMatch(requirement -> "FUNCTIONAL".equals(requirement.path("type").asText()));
        boolean hasDataRequirement = java.util.stream.StreamSupport.stream(draft.model().path("requirements").spliterator(), false)
                .anyMatch(requirement -> "DATA".equals(requirement.path("type").asText()));
        if (hasFunctionalRequirement && !artifacts.containsKey(DocumentationArtifactType.WORKFLOWS)) {
            materialGaps.add("Functional requirements exist, but the canonical SRS has no sufficiently detailed workflow artifact.");
        }
        if (hasFunctionalRequirement && !artifacts.containsKey(DocumentationArtifactType.C4_CONTEXT)) {
            materialGaps.add("Functional requirements exist, but confirmed actors and system-boundary evidence are insufficient for a context view.");
        }
        if (hasDataRequirement && !artifacts.containsKey(DocumentationArtifactType.ERD)) {
            materialGaps.add("Data requirements exist, but the canonical SRS has no explicit entity relationship evidence for an ERD.");
        }
        if (artifacts.containsKey(DocumentationArtifactType.OPENAPI)) {
            try {
                JsonNode api = objectMapper.readTree(artifacts.get(DocumentationArtifactType.OPENAPI).sourceContent());
                api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
                    String operationId = method.getValue().path("operationId").asText();
                    String requirementId = method.getValue().path("x-velocira-requirement-id").asText();
                    if (!tracedOperations.contains(operationId) || !requirementIds.contains(requirementId)) {
                        issues.add("OpenAPI operation " + operationId + " is not traceable to the canonical requirement set.");
                    }
                }));
            } catch (Exception exception) {
                issues.add("OpenAPI source cannot be parsed for canonical trace validation.");
            }
        }
        ArrayNode checks = outcome.putArray("checks");
        checks.add("requirement-to-use-case-or-nfr"); checks.add("requirement-to-entity-when-mentioned");
        checks.add("api-operation-to-canonical-requirement"); checks.add("requirement-to-acceptance-criterion");
        checks.add("emitted-artifact-syntax"); checks.add("artifact-plan-consistency");
        checks.add("no-empty-openapi-or-erd"); checks.add("canonical-manifest-targets");
        ArrayNode issueNodes = outcome.putArray("issues"); issues.forEach(issueNodes::add);
        ArrayNode gapNodes = outcome.putArray("materialGaps"); new LinkedHashSet<>(materialGaps).forEach(gapNodes::add);
        outcome.put("valid", issues.isEmpty());
        outcome.put("approvalEligible", issues.isEmpty() && materialGaps.isEmpty());
        outcome.put("exportReady", issues.isEmpty() && materialGaps.isEmpty());
        return new ValidationResult(issues.isEmpty(), issues, outcome);
    }

    private ArtifactDraft markdownArtifact(DocumentationArtifactType type, String title, String content, String validator) {
        List<String> issues = new ArrayList<>();
        if (!content.startsWith("# ")) issues.add("Markdown artifact must start with a level-one heading.");
        if (content.lines().filter(line -> line.startsWith("## ")).count() < 2) issues.add("Markdown artifact requires at least two reviewable sections.");
        if (content.contains("{{") || content.contains("}}")) issues.add("Unresolved template token detected.");
        return new ArtifactDraft(type, title, content, "MARKDOWN", content, validArtifact(validator, issues.isEmpty(), issues));
    }

    private ArrayNode documentPlan(Map<DocumentationArtifactType, ArtifactDisposition> dispositions) {
        ArrayNode plan = objectMapper.createArrayNode();
        for (DocumentationArtifactType type : DocumentationArtifactType.values()) {
            ObjectNode item = plan.addObject();
            item.put("artifactType", type.name());
            ArtifactDisposition disposition = dispositions.getOrDefault(type,
                    new ArtifactDisposition("OMITTED", "The compiler found no canonical evidence for this artifact."));
            item.put("status", disposition.status());
            item.put("reason", disposition.reason());
            item.put("emitted", !"OMITTED".equals(disposition.status()));
        }
        return plan;
    }

    private void addMarkdown(List<ArtifactDraft> artifacts, Map<DocumentationArtifactType, ArtifactDisposition> dispositions,
                             DocumentationArtifactType type, String title, String content, String validator,
                             String status, String reason) {
        artifacts.add(markdownArtifact(type, title, content, validator));
        dispositions.put(type, new ArtifactDisposition(status, reason));
    }

    private void omit(Map<DocumentationArtifactType, ArtifactDisposition> dispositions,
                      DocumentationArtifactType type, String reason) {
        dispositions.put(type, new ArtifactDisposition("OMITTED", reason));
    }

    private boolean hasRequirementType(List<SrsRequirementEntity> requirements, String... types) {
        Set<String> accepted = Set.of(types);
        return requirements.stream().anyMatch(requirement -> accepted.contains(requirement.getRequirementType()));
    }

    private boolean meaningfulBrief(SrsVersionEntity srs, String key) {
        return isMeaningfulBriefValue(srs.getBriefSnapshot().path(key).asText());
    }

    private String businessRequirements(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements,
                                        List<NamedNode> actors, List<NamedNode> rules) {
        StringBuilder text = documentHeader(project, "Business Requirements Document", srs)
                .append("## Executive intent\n\n").append(valueOrDecision(srs.getSrsContent().path("executive_summary").asText(),
                        "The business outcome requires stakeholder confirmation before investment decisions are finalized.")).append("\n\n")
                .append("## Problem and desired outcomes\n\n")
                .append(briefOrDecision(srs, "problem", "The primary business problem has not been confirmed.")).append("\n\n")
                .append("### Success measures\n\n").append(briefOrDecision(srs, "metrics", "Baseline, target, owner, and review period remain unresolved.")).append("\n\n")
                .append("## Scope and boundaries\n\n").append(briefOrDecision(srs, "scope", "The release boundary requires confirmation.")).append("\n\n")
                .append("### Explicit exclusions\n\n");
        appendJsonBullets(text, srs.getSrsContent().path("exclusions"), "No exclusions were confirmed; this is an open scope-control decision.");
        text.append("\n## Stakeholders and decision authority\n\n");
        appendNamedNodes(text, actors, "Stakeholder roles and authority require confirmation.");
        text.append("\n## Business rules\n\n");
        if (rules.isEmpty() && (meaningfulBrief(srs, "businessRules") || meaningfulBrief(srs, "business_rules"))) {
            text.append("- ").append(briefOrDecision(srs, "businessRules", "Business rules require confirmation.")).append("\n");
        } else {
            appendNamedNodes(text, rules, "No business rule is treated as confirmed until the project owner approves it.");
        }
        text.append("\n## Business requirements\n\n");
        appendRequirementSummaries(text, requirements, Set.of("BUSINESS", "FUNCTIONAL"));
        text.append("\n## Assumptions, dependencies, and unresolved decisions\n\n");
        appendDecisionRegister(text, srs);
        return text.toString();
    }

    private String architecture(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements,
                                List<NamedNode> actors, List<NamedNode> entities, List<NamedNode> integrations) {
        StringBuilder text = documentHeader(project, "Architecture Description", srs)
                .append("## Architecture purpose and conformance boundary\n\n")
                .append("This description follows ISO/IEC/IEEE 42010 concepts and arc42-style concerns without claiming standards certification. ")
                .append("Views are derived from confirmed requirements; an absent technology or topology remains unresolved rather than invented.\n\n")
                .append("## Stakeholders and concerns\n\n");
        for (NamedNode actor : actors) text.append("- **").append(actor.id()).append(" / ").append(actor.name()).append(":** workflow completion, authority boundaries, recoverability, and understandable feedback.\n");
        if (actors.isEmpty()) text.append("- **DECISION REQUIRED:** Confirm stakeholder roles, concerns, and approval authority.\n");
        text.append("\n## System context viewpoint\n\n```mermaid\n")
                .append(diagramSource(srs, "C4_CONTEXT", fallbackContextDiagram(project, actors, integrations))).append("\n```\n\n")
                .append("## Functional decomposition\n\n");
        appendRequirementSummaries(text, requirements, Set.of("FUNCTIONAL", "API", "DATA"));
        text.append("\n## Information viewpoint\n\n");
        appendNamedNodes(text, entities, "The conceptual information model is unresolved.");
        text.append("\n## Integration viewpoint\n\n");
        appendNamedNodes(text, integrations, "No external integration is confirmed for this release.");
        text.append("\n## Quality scenarios\n\n");
        appendQualityScenarios(text, srs.getSrsContent().path("quality_scenarios"));
        text.append("\n## Architecture decisions and rationale\n\n");
        appendDecisionRegister(text, srs);
        text.append("\n## Deployment viewpoint\n\n")
                .append(briefOrDecision(srs, "constraints", "Hosting, environments, regions, availability zones, scaling, and recovery topology remain unresolved."))
                .append("\n\n## Cross-cutting concerns\n\n");
        appendRequirementSummaries(text, requirements, Set.of("NON_FUNCTIONAL", "SECURITY", "PRIVACY", "ACCESSIBILITY", "OPERATIONS"));
        return text.toString();
    }

    private String workflows(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements, List<NamedNode> actors) {
        String diagram = diagramSource(srs, "WORKFLOW", fallbackWorkflowDiagram(srs));
        StringBuilder text = documentHeader(project, "Workflow and Recovery Specification", srs)
                .append("## Primary workflow map\n\n```mermaid\n").append(diagram).append("\n```\n\n")
                .append("## Actor responsibilities\n\n");
        appendNamedNodes(text, actors, "Actor ownership for workflow states remains unresolved.");
        text.append("\n## Detailed workflow register\n\n");
        JsonNode workflows = srs.getSrsContent().path("workflows");
        if (workflows.isArray() && !workflows.isEmpty()) {
            for (JsonNode workflow : workflows) {
                text.append("### ").append(workflow.path("id").asText("WF-UNRESOLVED")).append(" - ")
                        .append(workflow.path("title").asText("Workflow decision required")).append("\n\n")
                        .append("**Trigger:** ").append(workflow.path("trigger").asText("Unresolved")).append("\n\n")
                        .append("**Preconditions**\n\n");
                appendJsonBullets(text, workflow.path("preconditions"), "No preconditions confirmed.");
                text.append("\n**Main success flow**\n\n"); appendNumbered(text, workflow.path("main_flow"));
                text.append("\n**Alternate flows**\n\n"); appendJsonBullets(text, workflow.path("alternate_flows"), "Alternate paths require confirmation.");
                text.append("\n**Failure and recovery**\n\n"); appendJsonBullets(text, workflow.path("failure_recovery"), "Failure and recovery behavior requires confirmation.");
                text.append("\n**Postconditions**\n\n"); appendJsonBullets(text, workflow.path("postconditions"), "Postconditions require confirmation.");
                text.append("\n");
            }
        } else {
            text.append("No compiled workflow is available. Confirm triggers, states, alternate paths, failures, recovery, ownership, and postconditions.\n");
        }
        text.append("\n## Requirement-linked exception checklist\n\n");
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            text.append("- **").append(requirement.getRequirementId()).append(":** Validate success, invalid input, unauthorized action, dependency failure, duplicate/retry behavior, recovery, audit record, and visible user state where applicable.\n");
        }
        return text.toString();
    }

    private String dataDictionary(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements, List<NamedNode> entities) {
        StringBuilder text = documentHeader(project, "Data Dictionary and Lifecycle Specification", srs)
                .append("## Data-governance boundary\n\n")
                .append("Only confirmed domain concepts are listed. This conceptual register does not invent physical columns, keys, classifications, or retention rules that are absent from approved evidence.\n\n")
                .append("## Entity register\n\n");
        if (entities.isEmpty()) text.append("No entity model is confirmed. Data design must not begin until ownership and lifecycle decisions are recorded.\n\n");
        for (NamedNode entity : entities) {
            List<String> references = requirements.stream()
                    .filter(requirement -> requirementModel(srs, requirement.getRequirementId()).toString()
                            .toLowerCase(Locale.ROOT).contains(entity.name().toLowerCase(Locale.ROOT)))
                    .map(SrsRequirementEntity::getRequirementId).toList();
            text.append("### ").append(entity.id()).append(" - ").append(entity.name()).append("\n\n")
                    .append("- Canonical status: Confirmed domain entity in the approved project brief\n")
                    .append("- Concept identifier: ").append(entity.id()).append("\n")
                    .append("- Requirement references: ")
                    .append(references.isEmpty() ? "Confirmed brief and conceptual ERD" : String.join(", ", references))
                    .append("\n\n");
        }
        text.append("## Data requirements\n\n");
        appendRequirementSummaries(text, requirements, Set.of("DATA", "PRIVACY", "FUNCTIONAL"));
        text.append("\n## Implementation design boundary\n\n")
                .append("Before implementation, the downstream data design must define physical identifiers, required attributes, validation, ownership, access roles, classification, encryption, audit events, and lifecycle behavior. Those design choices must trace to this approved SRS and may not contradict its 24-hour idempotency, 24-month booking/audit retention, authorization, or conflict-prevention requirements.\n\n")
                .append("Migration, reconciliation, rollback, record-merging, and legal-hold behavior remain outside this requirements package until approved evidence defines them.\n");
        return text.toString();
    }

    private String securityAndPrivacy(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        StringBuilder text = documentHeader(project, "Security and Privacy Specification", srs)
                .append("## Protection objectives\n\n")
                .append("Security guidance is risk-based and project-specific. This document does not assert ISO, NIST, OWASP, privacy-law, or regulatory compliance.\n\n")
                .append("## Applicable guidance\n\n");
        appendStandards(text, srs, Set.of("ISO_27001", "OWASP_ASVS", "NIST_CSF", "NIST_SSDF", "WCAG_22"));
        text.append("\n## Security and privacy requirements\n\n");
        appendRequirementSummaries(text, requirements, Set.of("SECURITY", "PRIVACY", "NON_FUNCTIONAL"));
        text.append("\n## Threat and abuse-case review\n\n")
                .append("For every exposed capability, review identity spoofing, unauthorized access, privilege escalation, data disclosure, tampering, replay/duplication, denial of service, unsafe file or content handling, audit gaps, and recovery. Mark a threat not applicable only with rationale.\n\n")
                .append("## Security verification evidence\n\n")
                .append("- Authentication and session tests\n- Authorization tests for every role and object boundary\n- Input and output handling tests\n")
                .append("- Secret and configuration review\n- Dependency and supply-chain evidence\n- Logging, alerting, backup, and incident-response exercises\n")
                .append("- Privacy data-flow, retention, deletion, export, and access-control review\n\n")
                .append("## Unresolved security decisions\n\n");
        appendDecisionRegister(text, srs);
        return text.toString();
    }

    private String testPlan(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        StringBuilder text = documentHeader(project, "Verification and Test Plan", srs)
                .append("## Strategy\n\nTesting is risk-based and trace-driven. Each normative requirement has at least one explicit verification method and acceptance criterion. Test data, environments, owners, dates, and release evidence must be attached during execution.\n\n")
                .append("## Entry and exit criteria\n\n- Entry: approved requirement baseline, testable acceptance criteria, controlled environment, and representative data.\n")
                .append("- Exit: all MUST requirements pass or have an approved waiver; critical defects are closed; traceability and evidence are complete.\n\n")
                .append("## Requirement verification matrix\n\n")
                .append("| Test ID | Requirement | Method | Priority | Expected evidence |\n|---|---|---|---|---|\n");
        int index = 1;
        for (SrsRequirementEntity requirement : requirements) {
            text.append("|TEST-").append(String.format("%04d", index++)).append("|").append(requirement.getRequirementId()).append("|")
                    .append(requirement.getVerificationMethod()).append("|").append(requirement.getPriority()).append("|Execution result, logs/screenshots, and reviewer sign-off|\n");
        }
        text.append("\n## Detailed acceptance scenarios\n\n");
        for (SrsRequirementEntity requirement : requirements) {
            text.append("### ").append(requirement.getRequirementId()).append("\n\n")
                    .append("**Requirement:** ").append(requirement.getStatement()).append("\n\n")
                    .append("**Acceptance checks**\n\n");
            int criterion = 1;
            for (String acceptance : acceptanceCriteria(requirement.getAcceptanceCriteria())) {
                text.append(criterion++).append(". ").append(acceptance).append("\n");
            }
            text.append("\n**Negative and recovery coverage:** invalid input, unauthorized actor, duplicate/retry, dependency failure, interruption, and rollback are required when applicable.\n\n");
        }
        text.append("## Non-functional evidence\n\nPerformance, accessibility, security, resilience, recovery, compatibility, and operability tests require confirmed environments and measurable thresholds; unresolved thresholds block a final pass claim.\n");
        return text.toString();
    }

    private String deployment(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        StringBuilder text = documentHeader(project, "Deployment and Release Specification", srs)
                .append("## Confirmed delivery constraints\n\n").append(briefOrDecision(srs, "constraints", "Platform, environments, regions, timeline, budget, and release topology remain unresolved.")).append("\n\n")
                .append("## Environment model\n\n- Local development: not specified in the approved requirements baseline.\n- Continuous integration: not specified in the approved requirements baseline.\n- Test/staging: not specified in the approved requirements baseline.\n- Production: not specified in the approved requirements baseline.\n\n")
                .append("## Release pipeline controls\n\n1. Reproducible build and dependency lock.\n2. Static, unit, integration, security, and artifact-validation gates.\n3. Immutable versioned artifact publication.\n4. Environment-specific configuration and secret injection.\n5. Migration preflight, backup, deployment, smoke test, and rollback decision.\n6. Evidence capture and release approval.\n\n")
                .append("## Deployment requirements\n\n");
        appendRequirementSummaries(text, requirements, Set.of("OPERATIONS", "NON_FUNCTIONAL", "SECURITY"));
        text.append("\n## Rollback and migration\n\nRollback triggers, maximum tolerated interruption, database compatibility, feature-flag strategy, and migration recovery are unresolved unless stated in confirmed constraints.\n");
        return text.toString();
    }

    private String operations(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        StringBuilder text = documentHeader(project, "Operations Runbook", srs)
                .append("## Service objectives\n\n").append(briefOrDecision(srs, "qualityTargets", "Availability, latency, throughput, recovery, and support targets require measurable decisions.")).append("\n\n")
                .append("## Observability\n\n- Health and dependency status\n- User-journey success/failure metrics\n- Latency and saturation\n- Authorization and abuse signals\n- Queue/backlog or scheduled-work health\n- Backup and recovery evidence\n\n")
                .append("## Incident workflow\n\n1. Detect and classify impact.\n2. Assign incident owner and preserve evidence.\n3. Contain user and data risk.\n4. Recover the smallest safe service boundary.\n5. Validate business workflow health.\n6. Communicate status and record decisions.\n7. Complete root-cause and corrective-action review.\n\n")
                .append("## Operational requirements\n\n");
        appendRequirementSummaries(text, requirements, Set.of("OPERATIONS", "NON_FUNCTIONAL", "SECURITY"));
        text.append("\n## Runbook decision register\n\nEscalation contacts, severity definitions, alert thresholds, on-call coverage, RTO/RPO, backup schedule, maintenance windows, and status-communication channels require owner confirmation.\n");
        return text.toString();
    }

    private String userManual(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements, List<NamedNode> actors) {
        StringBuilder text = documentHeader(project, "User Manual", srs)
                .append("## Purpose and product boundary\n\n").append(briefOrDecision(srs, "scope", "The available user-facing capability set requires confirmation.")).append("\n\n")
                .append("## Roles\n\n");
        appendNamedNodes(text, actors, "No user role is confirmed; role-specific instructions cannot be safely inferred.");
        text.append("\n## Task guides\n\n");
        int index = 1;
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            text.append("### Task ").append(index++).append(" - ").append(requirement.getRequirementId()).append("\n\n")
                    .append("**Goal:** ").append(requirement.getStatement()).append("\n\n")
                    .append("**Before you begin:** Confirm that your role is authorized and the required information is available.\n\n")
                    .append("**Expected outcome**\n\n");
            appendJsonBullets(text, objectMapper.valueToTree(acceptanceCriteria(requirement.getAcceptanceCriteria())), "Expected outcome requires confirmation.");
            text.append("\n**If it fails:** Preserve the visible error/reference, avoid repeating irreversible actions, and follow the approved recovery or support route.\n\n");
        }
        text.append("## Accessibility and support\n\nKeyboard, assistive-technology, language, help, account-recovery, privacy, and support instructions remain required review areas. A generated manual is not final until validated against the implemented interface.\n");
        return text.toString();
    }

    private String riskRegister(ProjectEntity project, SrsVersionEntity srs) {
        StringBuilder text = documentHeader(project, "Risk and Decision Register", srs)
                .append("## Method\n\nRisks are recorded with evidence, impact, treatment, owner, trigger, and residual decision. Missing probability or impact values remain unresolved rather than receiving invented scores.\n\n")
                .append("## Confirmed risks\n\n");
        appendRegisterItems(text, srs.getSrsContent().path("risks"), "No confirmed risk entries are available.");
        text.append("\n## Open decisions\n\n");
        appendRegisterItems(text, srs.getSrsContent().path("decisions"), "No open decisions are registered in the approved SRS.");
        text.append("\n## Review cadence\n\nReview risks and decisions at requirements approval, architecture approval, pre-release, after material scope or dependency change, and after every production incident.\n");
        return text.toString();
    }

    private ObjectNode openApi(ProjectEntity project, List<SrsRequirementEntity> requirements,
                               Map<String, ApiOperation> confirmedOperations) {
        ObjectNode api = objectMapper.createObjectNode();
        api.put("openapi", "3.1.1");
        ObjectNode info = api.putObject("info"); info.put("title", project.getName() + " API"); info.put("version", "1.0.0");
        info.put("description", "Canonical SRS contract. Every emitted operation carries its source requirement ID.");
        ObjectNode paths = api.putObject("paths");
        ArrayNode unresolved = api.putArray("x-velocira-unresolved-api-requirements");
        for (SrsRequirementEntity requirement : requirements) {
            if (!"API".equals(requirement.getRequirementType())) continue;
            ApiOperation confirmed = confirmedOperations.get(requirement.getRequirementId());
            if (confirmed == null) {
                ObjectNode unresolvedRequirement = unresolved.addObject();
                unresolvedRequirement.put("requirementId", requirement.getRequirementId());
                unresolvedRequirement.put("decision", "Confirm path, HTTP method, and stable operation ID in the canonical SRS before contract publication.");
                continue;
            }
            ObjectNode pathItem = paths.path(confirmed.path()).isObject()
                    ? (ObjectNode) paths.path(confirmed.path()) : paths.putObject(confirmed.path());
            ObjectNode operation = pathItem.putObject(confirmed.method());
            operation.put("operationId", confirmed.operationId());
            operation.put("summary", requirementTitle(requirement));
            operation.put("description", requirement.getStatement());
            operation.put("x-velocira-requirement-id", requirement.getRequirementId());
            operation.putObject("responses").putObject("default")
                    .put("description", "Response details remain governed by the canonical SRS contract evidence.");
        }
        return api;
    }

    private String srsMarkdown(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements) {
        JsonNode content = srs.getSrsContent();
        JsonNode manifest = content.path("generation_manifest");
        String generationMode = manifest.path("mode").asText("STANDARD").toUpperCase(Locale.ROOT);
        int packageWordTarget = manifest.path("long_form_provenance").path("expected_package_word_target").asInt(0);
        StringBuilder text = documentHeader(project, "Software Requirements Specification", srs)
                .append("## Document control\n\n")
                .append("| Field | Value |\n|---|---|\n")
                .append("| Status | ").append(srsStatusLabel(srs.getStatus())).append(" |\n")
                .append("| Generation mode | ").append(generationMode).append(" |\n")
                .append("| Declared package word target | ").append(packageWordTarget > 0 ? packageWordTarget : "Not declared").append(" |\n")
                .append("| Source SRS version | ").append(srs.getVersionNumber()).append(" |\n")
                .append("| Standards profile | ").append(srs.getProfile().getName()).append(" |\n")
                .append("| Provider / model | ").append(srs.getProvider()).append(" / ").append(srs.getModel()).append(" |\n")
                .append("| Prompt/compiler | ").append(srs.getPromptVersion()).append(" |\n\n")
                .append("## Executive summary\n\n")
                .append(valueOrDecision(content.path("executive_summary").asText(), "An executive summary requires confirmation.")).append("\n\n")
                .append("## Purpose, audience, and conventions\n\n")
                .append("This SRS is the governed requirement baseline for product, design, engineering, security, privacy, QA, operations, and stakeholder review. ")
                .append("Normative SHALL statements define verifiable obligations. Recommendations, assumptions, unresolved decisions, and exclusions are labelled and do not silently become confirmed scope.\n\n")
                .append("## Scope\n\n").append(valueOrDecision(content.path("scope").asText(), briefOrDecision(srs, "scope", "The first-release scope is unresolved."))).append("\n\n")
                .append("### Objectives\n\n");
        appendJsonBullets(text, content.path("objectives"), briefOrDecision(srs, "metrics", "Measurable objectives require confirmation."));
        text.append("\n### Explicit exclusions\n\n");
        appendJsonBullets(text, content.path("exclusions"), "No exclusions are confirmed; this is a scope-control risk.");
        text.append("\n### Assumptions\n\n");
        appendJsonBullets(text, content.path("assumptions"), "No assumptions are currently registered.");
        text.append("\n## Stakeholders and users\n\n");
        appendJsonBullets(text, content.path("stakeholders"), briefOrDecision(srs, "users", "Stakeholders and authority boundaries require confirmation."));

        JsonNode sections = content.path("narrative_sections");
        if (sections.isArray()) {
            for (JsonNode section : sections) {
                text.append("\n## ").append(section.path("title").asText("Compiled section")).append("\n\n")
                        .append("**Source status:** ").append(section.path("source_status").asText("UNRESOLVED")).append("  \n")
                        .append("**Purpose:** ").append(section.path("purpose").asText("Review the project decision.")).append("\n\n")
                        .append(section.path("content").asText("Decision required.")).append("\n");
            }
        }
        text.append("\n## Detailed normative requirements\n\n")
                .append("The following requirements are independently identifiable, prioritized, sourced, accepted, and verifiable.\n\n");
        for (SrsRequirementEntity requirement : requirements) {
            JsonNode detail = requirementModel(srs, requirement.getRequirementId());
            text.append("### ").append(requirement.getRequirementId()).append("\n\n")
                    .append("**Title:** ").append(detail.path("title").asText("Requirement")).append("  \n")
                    .append("**Status:** ").append(detail.path("status").asText("NEEDS_REVIEW")).append("  \n")
                    .append("**Normative statement:** ").append(requirement.getStatement()).append("\n\n")
                    .append("**Rationale:** ").append(requirement.getRationale()).append("\n\n")
                    .append("- Priority: ").append(requirement.getPriority()).append("\n")
                    .append("- Type: ").append(requirement.getRequirementType()).append("\n")
                    .append("- Verification: ").append(requirement.getVerificationMethod()).append("\n")
                    .append("- Source classification: ").append(requirement.getSourceKind()).append("\n")
                    .append("- Evidence / assumption: ").append(requirement.getSourceDetail()).append("\n")
                    .append("- Trigger: ").append(detail.path("trigger").asText("Decision required")).append("\n")
                    .append("- Failure behavior: ").append(detail.path("failure_behavior").asText("Decision required")).append("\n")
                    .append("\n**Actors**\n\n");
            appendJsonBullets(text, detail.path("actors"), "Actor responsibility requires confirmation.");
            text.append("\n**Preconditions**\n\n");
            appendJsonBullets(text, detail.path("preconditions"), "No precondition is confirmed.");
            text.append("\n**Data involved**\n\n");
            appendJsonBullets(text, detail.path("data_involved"), "No additional data item is specified for this requirement.");
            text.append("\n**Dependencies and risks**\n\n");
            appendJsonBullets(text, detail.path("dependencies"), "No external dependency is specified for this requirement.");
            appendJsonBullets(text, detail.path("risks"), "No additional requirement-specific risk is specified.");
            text.append("\n")
                    .append("\n**Acceptance criteria**\n\n");
            int criterion = 1;
            for (String value : acceptanceCriteria(requirement.getAcceptanceCriteria())) {
                text.append(criterion++).append(". **AC-").append(slug(requirement.getRequirementId())).append("-")
                        .append(String.format("%03d", criterion - 1)).append(":** ").append(value).append("\n");
            }
            text.append("\n**Required exception review:** invalid input, unauthorized action, duplicate/retry, dependency failure, interruption, recovery, auditability, and user-visible status must be marked applicable or not applicable with rationale.\n");
            text.append("\n");
        }
        text.append("## Quality scenarios\n\n");
        appendQualityScenarios(text, content.path("quality_scenarios"));
        text.append("\n## Risk register\n\n");
        appendRegisterItems(text, content.path("risks"), "No confirmed risks are registered.");
        text.append("\n## Open-question and decision register\n\n");
        appendRegisterItems(text, content.path("decisions"), "No compiled open decisions are registered.");
        text.append("\n## Standards applicability\n\n");
        appendStandards(text, srs, Set.of());
        text.append("\n## Diagram register\n\n");
        JsonNode diagrams = content.path("diagrams");
        if (diagrams.isArray()) for (JsonNode diagram : diagrams) {
            text.append("### ").append(diagram.path("id").asText()).append(" - ").append(diagram.path("title").asText()).append("\n\n")
                    .append("Status: ").append(diagram.path("status").asText()).append(". ")
                    .append(diagram.path("rationale").asText()).append("\n\n```mermaid\n")
                    .append(diagram.path("source").asText()).append("\n```\n\n");
        }
        text.append("## Review and approval checklist\n\n")
                .append("- Confirm every unresolved decision or explicitly defer it.\n- Verify every requirement source and acceptance criterion.\n")
                .append("- Review role permissions, data ownership, failure recovery, measurable quality targets, and operational evidence.\n")
                .append("- Validate diagrams and contracts against the approved requirement baseline.\n- Record stakeholder approval and revision history before treating this document as a release baseline.\n");
        return text.toString();
    }

    private String useCases(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements, List<NamedNode> actors, List<NamedNode> entities) {
        StringBuilder text = documentHeader(project, "Use Cases", srs);
        int number = 1;
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            JsonNode detail = requirementModel(srs, requirement.getRequirementId());
            List<String> requirementActors = new ArrayList<>();
            if (detail.path("actors").isArray()) detail.path("actors").forEach(value -> requirementActors.add(value.asText()));
            String actor = requirementActors.isEmpty()
                    ? (actors.isEmpty() ? "DECISION REQUIRED - primary actor" : actors.getFirst().name())
                    : String.join(", ", requirementActors);
            text.append("## UC-").append(String.format("%03d", number++)).append(" - ").append(requirement.getRequirementId()).append("\n\n")
                    .append("- Primary actor: ").append(actor).append("\n")
                    .append("- Source requirement: ").append(requirement.getRequirementId()).append("\n")
                    .append("- Goal: ").append(requirement.getStatement()).append("\n")
                    .append("- Trigger: ").append(detail.path("trigger").asText("Decision required")).append("\n")
                    .append("- Preconditions:\n");
            if (detail.path("preconditions").isArray() && !detail.path("preconditions").isEmpty()) {
                detail.path("preconditions").forEach(value -> text.append("  - ").append(value.asText()).append("\n"));
            } else text.append("  - Decision required\n");
            text.append("- Main success behavior:\n  1. The confirmed trigger occurs under the stated preconditions.\n  2. ")
                    .append(requirement.getStatement()).append("\n  3. The applicable acceptance outcome is observable and retained as verification evidence.\n")
                    .append("- Acceptance criteria:\n");
            acceptanceCriteria(requirement.getAcceptanceCriteria()).forEach(value -> text.append("  - ").append(value).append("\n"));
            text.append("- Failure and recovery: ").append(detail.path("failure_behavior").asText("Decision required; do not infer a successful result.")).append("\n")
                    .append("- Related data: ");
            if (detail.path("data_involved").isArray() && !detail.path("data_involved").isEmpty()) {
                List<String> data = new ArrayList<>(); detail.path("data_involved").forEach(value -> data.add(value.asText())); text.append(String.join(", ", data));
            } else text.append("Decision required");
            text.append("\n");
        }
        return text.toString();
    }

    private String plantUml(ProjectEntity project, SrsVersionEntity srs, List<SrsRequirementEntity> requirements, List<NamedNode> actors) {
        Map<String, String> actorIds = new LinkedHashMap<>();
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            JsonNode detail = requirementModel(srs, requirement.getRequirementId());
            if (detail.path("actors").isArray()) detail.path("actors").forEach(actor -> {
                String name = actor.asText().trim();
                if (isMeaningfulBriefValue(name)) actorIds.putIfAbsent(name, "ACTOR_" + slug(name).toUpperCase(Locale.ROOT));
            });
        }
        if (actorIds.isEmpty()) for (NamedNode actor : actors) actorIds.put(actor.name(), "ACTOR_" + slug(actor.name()).toUpperCase(Locale.ROOT));
        if (actorIds.isEmpty()) actorIds.put("Project user", "ACTOR_PROJECT_USER");
        StringBuilder source = new StringBuilder("@startuml\nleft to right direction\nskinparam packageStyle rectangle\n");
        actorIds.forEach((name, id) -> source.append("actor \"").append(plantText(name)).append("\" as ").append(id).append("\n"));
        source.append("rectangle \"").append(plantText(project.getName())).append("\" {\n");
        int number = 1;
        for (SrsRequirementEntity requirement : requirements) {
            if (!"FUNCTIONAL".equals(requirement.getRequirementType())) continue;
            JsonNode detail = requirementModel(srs, requirement.getRequirementId());
            String id = "UC_" + String.format("%03d", number++);
            source.append("  usecase \"").append(plantText(requirement.getRequirementId() + ": " + truncate(requirement.getStatement(), 80)))
                    .append("\" as ").append(id).append("\n");
            List<String> linkedActors = new ArrayList<>();
            if (detail.path("actors").isArray()) detail.path("actors").forEach(actor -> linkedActors.add(actor.asText()));
            if (linkedActors.isEmpty()) linkedActors.add(actorIds.keySet().iterator().next());
            linkedActors.stream().map(actorIds::get).filter(Objects::nonNull).distinct()
                    .forEach(actorId -> source.append("  ").append(actorId).append(" --> ").append(id).append("\n"));
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

    private String traceability(ProjectEntity project, SrsVersionEntity srs, List<TraceDraft> traces) {
        StringBuilder text = documentHeader(project, "Traceability Matrix", srs)
                .append("This matrix connects every reviewed requirement to its design, API, and acceptance evidence. ")
                .append("Read each row from left to right: requirement -> use case -> entity -> API operation -> acceptance criterion.\n\n")
                .append("## Requirement matrix\n\n")
                .append("| Requirement | Use case | Design entity | API operation | Acceptance criterion | Evidence source |\n")
                .append("|---|---|---|---|---|---|\n");
        for (TraceDraft trace : traces) {
            text.append("|").append(trace.requirement().getRequirementId()).append("|")
                    .append(orDash(trace.useCaseId())).append("|").append(orDash(trace.entityId())).append("|")
                    .append(orDash(trace.apiOperationId())).append("|").append(trace.acceptanceCriterionId()).append("|")
                    .append(trace.requirement().getSourceKind()).append("|\n");
        }
        text.append("\n## Coverage and review rules\n\n")
                .append("Every normative requirement must retain a source classification, acceptance criterion, verification method, and primary trace row. ")
                .append("Blank design or entity links mean that the relationship is not yet supported by confirmed evidence; they are not permission to invent one.\n");
        return text.toString();
    }

    private ObjectNode validateOpenApi(ObjectNode api) {
        List<String> issues = new ArrayList<>();
        if (!"3.1.1".equals(api.path("openapi").asText())) issues.add("OpenAPI version must be 3.1.1.");
        if (api.path("info").path("title").asText().isBlank() || api.path("info").path("version").asText().isBlank()) issues.add("OpenAPI info is incomplete.");
        if (!api.path("paths").isObject()) issues.add("OpenAPI paths must be an object.");
        if (api.path("paths").isEmpty()) issues.add("OpenAPI must contain at least one confirmed operation.");
        Set<String> operationIds = new HashSet<>();
        api.path("paths").properties().forEach(path -> {
            if (!path.getKey().startsWith("/")) issues.add("OpenAPI path must start with '/'.");
            path.getValue().properties().forEach(method -> {
            String operationId = method.getValue().path("operationId").asText();
            if (!HTTP_METHODS.contains(method.getKey().toLowerCase(Locale.ROOT))) issues.add("Unsupported OpenAPI method: " + method.getKey() + ".");
            if (operationId.isBlank() || !operationIds.add(operationId) || method.getValue().path("x-velocira-requirement-id").asText().isBlank()
                    || !method.getValue().path("responses").isObject() || method.getValue().path("responses").isEmpty()) {
                issues.add("OpenAPI has a missing or duplicate operation trace.");
            }
        }); });
        return validArtifact("openapi-3.1", issues.isEmpty(), issues);
    }

    private ApiOperation confirmedApiOperation(JsonNode detail) {
        if (detail == null || !detail.isObject()) return null;
        JsonNode contract = firstObject(detail.path("api_operation"), detail.path("apiOperation"), detail.path("endpoint"), detail.path("api"));
        String path = firstText(detail.path("api_path"), detail.path("path"), contract.path("path"));
        String method = firstText(detail.path("http_method"), detail.path("method"), contract.path("http_method"), contract.path("method")).toLowerCase(Locale.ROOT);
        String operationId = firstText(detail.path("operation_id"), detail.path("operationId"), contract.path("operation_id"), contract.path("operationId"));
        if (!path.startsWith("/") || !HTTP_METHODS.contains(method) || operationId.isBlank()
                || !operationId.matches("[A-Za-z][A-Za-z0-9_.-]{2,120}")) return null;
        return new ApiOperation(path, method, operationId);
    }

    private boolean apiOperationMatchesLocalSource(JsonNode detail, SrsRequirementEntity requirement, ApiOperation operation) {
        String localSource = firstText(detail.path("source_detail"), detail.path("sourceDetail"));
        if (localSource.isBlank()) localSource = requirement.getSourceDetail();
        String normalized = localSource == null ? "" : localSource.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String signature = (operation.method() + " " + operation.path()).toLowerCase(Locale.ROOT);
        return normalized.contains(signature) || normalized.contains(operation.operationId().toLowerCase(Locale.ROOT));
    }

    private JsonNode firstObject(JsonNode... values) {
        for (JsonNode value : values) if (value != null && value.isObject()) return value;
        return objectMapper.createObjectNode();
    }

    private String firstText(JsonNode... values) {
        for (JsonNode value : values) if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText().trim();
        return "";
    }

    private StringBuilder documentHeader(ProjectEntity project, String documentTitle, SrsVersionEntity srs) {
        JsonNode manifest = srs.getSrsContent().path("generation_manifest");
        String mode = manifest.path("mode").asText("STANDARD").toUpperCase(Locale.ROOT);
        String actualModel = manifest.path("actual_model").asText(srs.getModel());
        return new StringBuilder("# ").append(project.getName()).append(" - ").append(documentTitle).append("\n\n")
                .append("Canonical source: SRS v").append(srs.getVersionNumber())
                .append(" | Status: ").append(srsStatusLabel(srs.getStatus()))
                .append(" | Generation: ").append(mode)
                .append(" | Model: ").append(actualModel).append("\n\n");
    }

    private String srsStatusLabel(SrsVersionStatus status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case DRAFT -> "Draft";
            case NEEDS_REVIEW -> "Needs stakeholder review";
            case APPROVED -> "Approved";
            case CHANGES_REQUESTED -> "Changes requested";
        };
    }

    private String valueOrDecision(String value, String decision) {
        return value == null || value.isBlank() ? "**DECISION REQUIRED:** " + decision : value;
    }

    private String briefOrDecision(SrsVersionEntity srs, String key, String decision) {
        JsonNode brief = srs.getBriefSnapshot();
        String value = brief.path(key).asText();
        if (value.isBlank() && key.equals("businessRules")) value = brief.path("business_rules").asText();
        if (value.isBlank() && key.equals("qualityTargets")) value = brief.path("quality_targets").asText();
        if (value.isBlank() && key.equals("metrics")) {
            value = brief.path("successMetrics").asText();
            if (value.isBlank()) value = brief.path("success_metrics").asText();
        }
        return valueOrDecision(value, decision);
    }

    private void appendJsonBullets(StringBuilder text, JsonNode values, String emptyMessage) {
        if (values.isArray() && !values.isEmpty()) {
            values.forEach(value -> text.append("- ").append(value.isTextual() ? value.asText() : value.path("description").asText(value.toString())).append("\n"));
        } else if (values.isTextual() && !values.asText().isBlank()) {
            text.append("- ").append(values.asText()).append("\n");
        } else {
            text.append("- ").append(emptyMessage).append("\n");
        }
    }

    private void appendNumbered(StringBuilder text, JsonNode values) {
        int index = 1;
        if (values.isArray()) for (JsonNode value : values) text.append(index++).append(". ").append(value.asText()).append("\n");
        if (index == 1) text.append("1. **DECISION REQUIRED:** Confirm the ordered workflow steps.\n");
    }

    private void appendNamedNodes(StringBuilder text, List<NamedNode> nodes, String emptyMessage) {
        if (nodes.isEmpty()) {
            text.append("- **DECISION REQUIRED:** ").append(emptyMessage).append("\n");
            return;
        }
        nodes.forEach(node -> text.append("- **").append(node.id()).append(":** ").append(node.name()).append("\n"));
    }

    private void appendRequirementSummaries(StringBuilder text, List<SrsRequirementEntity> requirements, Set<String> types) {
        int count = 0;
        for (SrsRequirementEntity requirement : requirements) {
            if (!types.isEmpty() && !types.contains(requirement.getRequirementType())) continue;
            text.append("### ").append(requirement.getRequirementId()).append("\n\n")
                    .append(requirement.getStatement()).append("\n\n")
                    .append("- Priority: ").append(requirement.getPriority()).append("\n")
                    .append("- Verification: ").append(requirement.getVerificationMethod()).append("\n")
                    .append("- Rationale: ").append(requirement.getRationale()).append("\n")
                    .append("- Source: ").append(requirement.getSourceDetail()).append("\n\n");
            count++;
        }
        if (count == 0) text.append("No requirement of the applicable type is confirmed; add one only when supported by project evidence.\n");
    }

    private void appendDecisionRegister(StringBuilder text, SrsVersionEntity srs) {
        JsonNode decisions = srs.getSrsContent().path("decisions");
        appendRegisterItems(text, decisions, "No open decisions are registered in the approved SRS.");
    }

    private void appendRegisterItems(StringBuilder text, JsonNode values, String emptyMessage) {
        if (!values.isArray() || values.isEmpty()) {
            text.append("- ").append(emptyMessage).append("\n");
            return;
        }
        for (JsonNode item : values) {
            text.append("### ").append(item.path("id").asText("ITEM")).append(" - ")
                    .append(item.path("title").asText("Review item")).append("\n\n")
                    .append("- Category: ").append(item.path("category").asText("Review")).append("\n")
                    .append("- Status: ").append(item.path("status").asText("UNRESOLVED")).append("\n")
                    .append("- Owner: ").append(item.path("owner").asText("Project owner")).append("\n")
                    .append("- Source: ").append(item.path("source_detail").asText("Governed project context")).append("\n\n")
                    .append(item.path("description").asText("Review required.")).append("\n\n");
        }
    }

    private void appendQualityScenarios(StringBuilder text, JsonNode scenarios) {
        if (!scenarios.isArray() || scenarios.isEmpty()) {
            text.append("- **DECISION REQUIRED:** Define source, stimulus, environment, affected artifact, response, and measurable response threshold for each material quality attribute.\n");
            return;
        }
        for (JsonNode scenario : scenarios) {
            text.append("### ").append(scenario.path("id").asText("QS-UNRESOLVED")).append(" - ")
                    .append(scenario.path("quality_attribute").asText("Quality decision")).append("\n\n")
                    .append("| Field | Scenario |\n|---|---|\n")
                    .append("| Status |").append(scenario.path("status").asText("UNRESOLVED")).append("|\n")
                    .append("| Source |").append(scenario.path("source").asText("Unresolved")).append("|\n")
                    .append("| Stimulus |").append(scenario.path("stimulus").asText("Unresolved")).append("|\n")
                    .append("| Environment |").append(scenario.path("environment").asText("Unresolved")).append("|\n")
                    .append("| Artifact |").append(scenario.path("artifact").asText("Unresolved")).append("|\n")
                    .append("| Response |").append(scenario.path("response").asText("Unresolved")).append("|\n")
                    .append("| Response measure |").append(scenario.path("response_measure").asText("Unresolved")).append("|\n\n");
        }
    }

    private void appendStandards(StringBuilder text, SrsVersionEntity srs, Set<String> keys) {
        JsonNode standards = applicableStandards(srs);
        int count = 0;
        if (standards.isArray()) for (JsonNode standard : standards) {
            String id = standard.path("id").asText();
            boolean selected = keys.isEmpty() || keys.stream().anyMatch(id::contains);
            if (!selected) continue;
            String rationale = standard.path("applicability_reason").asText(standard.path("description").asText());
            text.append("- **").append(standard.path("title").asText(id)).append(":** ")
                    .append(truncate(rationale, 240)).append("\n");
            count++;
        }
        if (count == 0) text.append("- No applicable standard entry is compiled for this section; reviewer confirmation is required.\n");
    }

    private ArrayNode applicableStandards(SrsVersionEntity srs) {
        ArrayNode result = objectMapper.createArrayNode();
        JsonNode standards = srs.getSrsContent().path("standards_applied");
        String exclusions = canonicalText(srs.getSrsContent().path("exclusions")).toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        if (standards.isArray()) for (JsonNode standard : standards) {
            String id = standard.path("id").asText(standard.path("title").asText());
            if (id.isBlank() || !seen.add(id.toLowerCase(Locale.ROOT))) continue;
            String rationale = standard.path("applicability_reason").asText(standard.path("description").asText());
            java.util.regex.Matcher matcher = APPLICABILITY_SIGNALS.matcher(rationale);
            if (matcher.find()) {
                List<String> signals = Arrays.stream(matcher.group(1).split("[,/]"))
                        .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
                        .filter(value -> value.length() >= 2).toList();
                boolean excludedOnly = !signals.isEmpty() && signals.stream().allMatch(exclusions::contains);
                if (excludedOnly) continue;
            }
            result.add(standard.deepCopy());
            if (result.size() == 10) break;
        }
        return result;
    }

    private String canonicalText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            StringBuilder value = new StringBuilder();
            node.forEach(item -> value.append(canonicalText(item)).append(' '));
            return value.toString();
        }
        return node.toString();
    }

    private String diagramSource(SrsVersionEntity srs, String type, String fallback) {
        JsonNode diagrams = srs.getSrsContent().path("diagrams");
        if (diagrams.isArray()) for (JsonNode diagram : diagrams) {
            if (type.equals(diagram.path("type").asText()) && !diagram.path("source").asText().isBlank()) return diagram.path("source").asText();
        }
        return fallback;
    }

    private JsonNode requirementModel(SrsVersionEntity srs, String requirementId) {
        JsonNode values = srs.getSrsContent().path("requirements");
        if (values.isArray()) for (JsonNode value : values) {
            if (requirementId.equals(value.path("id").asText())) return value;
        }
        return objectMapper.createObjectNode();
    }

    private String fallbackContextDiagram(ProjectEntity project, List<NamedNode> actors, List<NamedNode> integrations) {
        StringBuilder source = new StringBuilder("flowchart LR\n  SYSTEM[\"").append(plantText(project.getName())).append("\"]\n");
        if (actors.isEmpty()) source.append("  ACTOR_UNRESOLVED[\"Actors require confirmation\"] -.-> SYSTEM\n");
        for (NamedNode actor : actors) source.append("  ").append(mermaidName(actor.id())).append("[\"").append(plantText(actor.name())).append("\"] --> SYSTEM\n");
        for (NamedNode integration : integrations) source.append("  SYSTEM --> ").append(mermaidName(integration.id())).append("[\"").append(plantText(integration.name())).append("\"]\n");
        return source.toString();
    }

    private String fallbackWorkflowDiagram(SrsVersionEntity srs) {
        List<String> steps = new ArrayList<>();
        JsonNode compiled = srs.getSrsContent().path("workflows");
        if (compiled.isArray()) compiled.forEach(workflow -> workflow.path("main_flow").forEach(step -> {
            if (!step.asText().isBlank()) steps.add(step.asText());
        }));
        if (steps.isEmpty()) steps.addAll(splitBriefSteps(srs.getBriefSnapshot().path("workflows").asText()));
        StringBuilder source = new StringBuilder("flowchart TD\n");
        if (steps.isEmpty()) return source.append("  START[\"Workflow trigger - unresolved\"] --> END[\"Outcome - unresolved\"]\n").toString();
        for (int index = 0; index < steps.size(); index++) {
            source.append("  STEP_").append(index + 1).append("[\"").append(plantText(truncate(steps.get(index), 100))).append("\"]\n");
            if (index > 0) source.append("  STEP_").append(index).append(" --> STEP_").append(index + 1).append("\n");
        }
        return source.toString();
    }

    private List<String> splitBriefSteps(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("(?:\\r?\\n|;|\\.\\s+)+")).map(String::trim).filter(item -> !item.isBlank()).limit(12).toList();
    }

    private int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        return (int) Arrays.stream(value.trim().split("\\s+")).filter(item -> !item.isBlank()).count();
    }

    private ObjectNode validArtifact(String validator, boolean valid) { return validArtifact(validator, valid, List.of()); }
    private ObjectNode validArtifact(String validator, boolean valid, List<String> issues) {
        ObjectNode node = objectMapper.createObjectNode(); node.put("valid", valid); node.put("validator", validator);
        ArrayNode problems = node.putArray("issues"); issues.forEach(problems::add); return node;
    }
    private boolean isPlantUmlValid(String source) {
        return source != null && source.startsWith("@startuml") && source.trim().endsWith("@enduml")
                && source.contains("usecase \"") && source.contains(" --> ") && !source.contains("\u0000");
    }
    private boolean isMermaidFlowValid(String source) {
        return source != null && (source.startsWith("flowchart ") || source.startsWith("graph ")) && !source.contains("\u0000");
    }
    private boolean isMeaningfulFlow(String source) {
        if (!isMermaidFlowValid(source)) return false;
        long edges = source.lines().filter(line -> line.contains("-->") || line.contains("-.->") || line.contains("==>")).count();
        return edges > 0 && !source.toLowerCase(Locale.ROOT).contains("require confirmation")
                && !source.toLowerCase(Locale.ROOT).contains("unresolved");
    }
    private boolean isErdValid(String source) {
        if (source == null || !source.startsWith("erDiagram") || source.contains("\u0000")) return false;
        long entities = ERD_ENTITY.matcher(source).results().count();
        return entities >= 2 && ERD_RELATIONSHIP.matcher(source).find()
                && source.lines().allMatch(line -> !line.contains("PK") || line.trim().endsWith("PK"));
    }

    private ArrayNode erdRelationships(String source) {
        ArrayNode relationships = objectMapper.createArrayNode();
        if (source == null || source.isBlank()) return relationships;
        java.util.regex.Matcher matcher = ERD_RELATIONSHIP_CAPTURE.matcher(source);
        while (matcher.find()) {
            ObjectNode relationship = relationships.addObject();
            relationship.put("from", matcher.group(1));
            relationship.put("to", matcher.group(2));
            relationship.put("label", matcher.group(3) == null ? "related" : matcher.group(3).trim());
        }
        return relationships;
    }

    private List<NamedNode> canonicalEntities(SrsVersionEntity srs) {
        List<NamedNode> structured = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode definitions = srs.getSrsContent().path("definitions");
        if (definitions.isArray()) for (JsonNode definition : definitions) {
            String category = definition.path("category").asText().replace('_', ' ').replace('-', ' ').trim();
            String name = definition.path("term").asText(
                    definition.path("name").asText(definition.path("title").asText()));
            if (!(category.equalsIgnoreCase("Domain entity") || category.equalsIgnoreCase("Entity"))
                    || !isMeaningfulEntityName(name) || !seen.add(slug(name))) continue;
            structured.add(new NamedNode("ENTITY-" + String.format("%03d", structured.size() + 1), name.trim()));
        }
        if (!structured.isEmpty()) return structured;
        return namesFromBrief(srs.getBriefSnapshot(), "entities", "entity").stream()
                .filter(node -> isMeaningfulEntityName(node.name())).toList();
    }

    private boolean isMeaningfulEntityName(String value) {
        if (!isMeaningfulBriefValue(value)) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int words = normalized.split("\\s+").length;
        if (normalized.matches(".*\\b(?:only while|when needed|as needed|if required|unless|shall|must|should|will|may|can)\\b.*")) return false;
        if (normalized.matches(".*\\b(?:deadline|authority|priority|constraint|requirement|needed)\\b.*")) return false;
        return words <= 2 || !normalized.matches("^(?:access|allow|deny|ensure|prevent|retain|recover|detect|use|provide|support|review)\\b.*");
    }

    private List<NamedNode> namesFromBrief(JsonNode brief, String... keys) {
        String raw = "";
        for (String key : keys) {
            JsonNode candidate = brief.path(key);
            if (candidate.isTextual() && isMeaningfulBriefValue(candidate.asText())) {
                raw = candidate.asText();
                break;
            }
            if (candidate.isArray()) {
                raw = java.util.stream.StreamSupport.stream(candidate.spliterator(), false)
                        .filter(JsonNode::isTextual).map(JsonNode::asText).filter(this::isMeaningfulBriefValue)
                        .collect(java.util.stream.Collectors.joining(";"));
                if (!raw.isBlank()) break;
            }
        }
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
            if (!isMeaningfulBriefValue(value) || value.length() < 2 || value.split("\\s+").length > 6 || prose || !seen.add(slug(value))) continue;
            values.add(new NamedNode(prefixFromKeys(keys).toUpperCase(Locale.ROOT) + "-" + String.format("%03d", values.size() + 1), value));
            if (values.size() == 12) break;
        }
        return values;
    }
    private boolean isMeaningfulBriefValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && !Set.of("null", "none", "n/a", "unknown", "undefined").contains(normalized);
    }
    private String prefixFromKeys(String... keys) { return keys[keys.length - 1].replaceAll("s$", ""); }
    private void array(ObjectNode parent, String name, List<NamedNode> nodes) { ArrayNode array = parent.putArray(name); nodes.forEach(node -> { ObjectNode item = array.addObject(); item.put("id", node.id()); item.put("name", node.name()); }); }
    private String matchingEntity(SrsRequirementEntity requirement, List<NamedNode> entities) {
        String text = requirement.getStatement().toLowerCase(Locale.ROOT);
        return entities.stream().filter(entity -> text.contains(entity.name().toLowerCase(Locale.ROOT))).map(NamedNode::id).findFirst().orElse(null);
    }
    private List<String> acceptanceCriteria(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\r?\\n")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }
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

    private JsonNode srsRequirementDetail(SrsVersionEntity srs, String requirementId) {
        JsonNode details = srs.getSrsContent().path("requirements");
        if (details.isArray()) for (JsonNode detail : details) {
            if (requirementId.equals(detail.path("id").asText())) return detail;
        }
        return objectMapper.createObjectNode();
    }

    private String requirementTitle(SrsRequirementEntity requirement) {
        String statement = requirement.getStatement() == null ? "Requirement" : requirement.getStatement().trim();
        statement = statement.replaceFirst("(?i)^the system shall\\s+", "");
        if (statement.endsWith(".")) statement = statement.substring(0, statement.length() - 1);
        return statement.isBlank() ? requirement.getRequirementId() : Character.toUpperCase(statement.charAt(0)) + statement.substring(1);
    }
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
    private record ApiOperation(String path, String method, String operationId) { }
    private record ArtifactDisposition(String status, String reason) { }
    private record TraceDraft(SrsRequirementEntity requirement, String useCaseId, String entityId, String apiOperationId, String acceptanceCriterionId) { }
    private record ArtifactDraft(DocumentationArtifactType type, String title, String content, String sourceFormat, String sourceContent, ObjectNode validation) { }
    private record CanonicalDraft(ObjectNode model, List<ArtifactDraft> artifacts, List<TraceDraft> traces,
                                  Map<DocumentationArtifactType, ArtifactDisposition> dispositions) { }
    private record ValidationResult(boolean valid, List<String> issues, ObjectNode node) { }
}
