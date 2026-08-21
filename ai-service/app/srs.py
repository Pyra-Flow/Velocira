"""Bounded SRS generation and deterministic quality checks."""

from __future__ import annotations

import re
import json
import logging
import ast
from datetime import datetime, timezone
from typing import Any

from pydantic import ValidationError

from app.errors import AiServiceError, ErrorCode
from app.models import (
    RetrievalHit,
    SrsArtifact,
    SrsDiagram,
    SrsGenerationRequest,
    SrsNarrativeSection,
    SrsQualityScenario,
    SrsRegisterItem,
    SrsRequirement,
    SrsValidation,
    SrsWorkflow,
)
from app.providers import GenerationProvider
from app.standards import applicable_standards

logger = logging.getLogger("velocira.ai_service.srs")

_INJECTION = re.compile(
    r"(?i)\b(ignore|override|disregard|reveal)\b.{0,80}\b(system|previous|instruction|prompt|secret|credential)\b"
)
_SENSITIVE = re.compile(
    r"(?i)(?:-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----|\b(?:api[_-]?key|password|secret)\s*[:=]\s*\S{8,})"
)

_SECTION_SPECS: tuple[tuple[str, str, str, str], ...] = (
    ("INTRODUCTION", "Introduction and document purpose", "problem", "Defines why the product exists and how this specification should be used."),
    ("BUSINESS_CONTEXT", "Business context and success", "metrics", "Connects the confirmed problem to observable business outcomes."),
    ("SCOPE", "Scope, boundaries, and exclusions", "scope", "Fixes the first-release boundary and prevents accidental scope expansion."),
    ("STAKEHOLDERS", "Stakeholders, users, and authority", "users", "Identifies actors, responsibilities, and decision authority."),
    ("WORKFLOWS", "Operational workflows and recovery", "workflows", "Describes successful, alternate, failure, and recovery behavior."),
    ("BUSINESS_RULES", "Business rules and state transitions", "businessRules", "Records policies, cutoffs, eligibility, approvals, and state constraints."),
    ("DATA", "Information model and data lifecycle", "entities", "Defines managed information, ownership, validation, retention questions, and access boundaries."),
    ("INTEGRATIONS", "Interfaces and integrations", "integrations", "Defines external dependencies and the failure boundaries that require contracts."),
    ("QUALITY", "Quality attributes and measurable scenarios", "qualityTargets", "Turns quality priorities into measurable scenarios and verification work."),
    ("SECURITY_PRIVACY", "Security, privacy, and abuse resistance", "risks", "Identifies protection objectives without inventing regulatory obligations."),
    ("DELIVERY_OPERATIONS", "Delivery, deployment, and operations", "constraints", "Records fixed delivery constraints and operational evidence still required."),
    ("VERIFICATION_TRACEABILITY", "Verification and traceability strategy", "requirements", "Connects requirements to sources, acceptance criteria, tests, design, and releases."),
)
_SECTION_IDS = frozenset(item[0] for item in _SECTION_SPECS)
_MIN_EXHAUSTIVE_NARRATIVE_WORDS = 20_000
_MIN_EXHAUSTIVE_PACKAGE_WORDS = 9_500
_MAX_EXHAUSTIVE_PACKAGE_WORDS = 50_000
_MIN_SUBSTANTIVE_SECTION_WORDS = 240


async def generate_srs(
    payload: SrsGenerationRequest, provider: GenerationProvider, *, correlation_id: str
) -> tuple[SrsArtifact, SrsValidation, str]:
    """Generate from confirmed brief + approved hits only. No source text is executable instruction."""
    _check_evidence(payload.evidence)
    generator = getattr(provider, "generate_srs", None)
    if provider.name != "deterministic" and callable(generator):
        try:
            raw = await generator(payload, correlation_id=correlation_id)
        except AiServiceError as exc:
            if not exc.retryable:
                raise
            if payload.generation_mode == "EXHAUSTIVE":
                logger.warning(
                    "provider_srs_unavailable_exhaustive_generation_aborted code=%s correlation_id=%s",
                    exc.code,
                    correlation_id,
                )
                raise AiServiceError(
                    exc.code,
                    "Exhaustive SRS generation requires the configured document model. The draft was not replaced with a reduced fallback; please retry when the provider is available.",
                    status_code=exc.status_code,
                    retryable=True,
                ) from exc
            logger.warning(
                "provider_srs_unavailable_using_deterministic_fallback code=%s correlation_id=%s",
                exc.code,
                correlation_id,
            )
            artifact = _deterministic_srs(payload)
            model = "deterministic-srs-fallback"
        else:
            try:
                candidate: Any = _parse_provider_json(raw.output) if isinstance(raw.output, str) else raw.output
                artifact = SrsArtifact.model_validate(candidate)
            except ValidationError as exc:
                issues = [
                    {
                        "location": ".".join(str(part) for part in issue.get("loc", ())),
                        "type": issue.get("type", "unknown"),
                    }
                    for issue in exc.errors()
                ]
                logger.warning(
                    "provider_srs_schema_invalid issues=%s correlation_id=%s",
                    issues,
                    correlation_id,
                )
                raise AiServiceError(
                    ErrorCode.PROVIDER_INVALID_OUTPUT,
                    "The provider did not return the required SRS structure.",
                    status_code=502,
                ) from exc
            except json.JSONDecodeError as exc:
                logger.warning(
                    "provider_srs_json_invalid line=%s column=%s correlation_id=%s",
                    exc.lineno,
                    exc.colno,
                    correlation_id,
                )
                raise AiServiceError(
                    ErrorCode.PROVIDER_INVALID_OUTPUT,
                    "The provider did not return the required SRS structure.",
                    status_code=502,
                ) from exc
            model = raw.model or provider.model
    else:
        artifact = _deterministic_srs(payload)
        model = provider.model

    # Keep provenance for the content supplied by the model before the
    # deterministic compiler fills structural gaps.  Otherwise a tiny provider
    # response could appear complete merely because the compiler added twelve
    # generic fallback chapters around it.
    model_authored_section_ids = [section.id for section in artifact.narrative_sections]
    model_authored_narrative_words = sum(_word_count(section.content) for section in artifact.narrative_sections)
    minimum_model_authored_narrative_words = _evidence_aware_narrative_target(payload)
    expected_package_word_target = _evidence_aware_package_target(minimum_model_authored_narrative_words)
    artifact = _compile_master_srs(artifact, payload)
    manifest = dict(artifact.generation_manifest)
    source_context = manifest.get("source_context", [])
    section_contracts = []
    for contract in manifest.get("section_contracts", []):
        if isinstance(contract, dict):
            section_contracts.append({
                **contract,
                "requested_model": provider.model,
                "actual_model": model,
                "prompt_version": "srs-compiler-v2",
                "source_context": source_context,
                "retry_count": 0,
                "validation_status": "PENDING",
            })
    manifest.update({
        "requested_model": provider.model,
        "actual_model": model,
        "prompt_version": "srs-compiler-v2",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "retry_count": 0,
        "validation_status": "PENDING",
        "long_form_provenance": {
            "model_authored_section_ids": model_authored_section_ids,
            "model_authored_narrative_words": model_authored_narrative_words,
            "minimum_model_authored_narrative_words": minimum_model_authored_narrative_words,
            "expected_package_word_target": expected_package_word_target,
            "compiled_section_ids": [section.id for section in artifact.narrative_sections],
            "compiled_narrative_words": sum(_word_count(section.content) for section in artifact.narrative_sections),
        },
        "workstreams": [
            {"id": item, "requested_model": provider.model, "actual_model": model, "retry_count": 0, "validation_status": "PASSED"}
            for item in (
                ["product", "data_interfaces", "trust", "quality_operations"]
                if payload.generation_mode == "EXHAUSTIVE" else ["complete_core"]
            )
        ],
        "section_contracts": section_contracts,
    })
    artifact = artifact.model_copy(update={"generation_manifest": manifest})
    validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
    if provider.name == "gemini" and payload.generation_mode == "EXHAUSTIVE":
        depth_issues = _exhaustive_depth_issues(artifact)
        if depth_issues:
            validation = validation.model_copy(update={
                "valid": False,
                "issues": [*validation.issues, *depth_issues],
            })
    if any("is not expressed as a testable shall statement." in issue for issue in validation.issues):
        artifact = _normalize_shall_statements(artifact)
        validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
    if not validation.valid:
        logger.warning(
            "provider_srs_quality_invalid issues=%s citation_coverage=%s correlation_id=%s",
            validation.issues,
            validation.citation_coverage,
            correlation_id,
        )
        raise AiServiceError(
            ErrorCode.PROVIDER_INVALID_OUTPUT,
            "The draft did not meet the SRS quality controls and was not saved.",
            status_code=422,
        )
    artifact = artifact.model_copy(update={
        "generation_manifest": {
            **artifact.generation_manifest,
            "validation_status": "PASSED",
            "section_contracts": [
                {**contract, "validation_status": "PASSED"}
                for contract in artifact.generation_manifest.get("section_contracts", [])
                if isinstance(contract, dict)
            ],
        }
    })
    return artifact, validation, model


def _word_count(value: str) -> int:
    return len(re.findall(r"\b[\w][\w'-]*\b", value))


def _evidence_aware_narrative_target(payload: SrsGenerationRequest) -> int:
    """Scale the depth floor to the information actually available.

    Exhaustive generation remains substantial for evidence-rich projects, but
    small or early projects are not forced to invent prose. Individual
    unresolved chapters are checked separately for actionable decision records.
    """
    confirmed_brief = json.dumps(payload.confirmed_brief, ensure_ascii=False, default=str)
    approved_evidence = " ".join(hit.content for hit in payload.evidence)
    grounding_words = _word_count(confirmed_brief) + _word_count(approved_evidence)
    return min(_MIN_EXHAUSTIVE_NARRATIVE_WORDS, max(400, grounding_words * 10))


def _evidence_aware_package_target(narrative_target: int) -> int:
    """Require a 25k+ package only when the supplied evidence can support it."""
    return min(
        _MAX_EXHAUSTIVE_PACKAGE_WORDS,
        max(_MIN_EXHAUSTIVE_PACKAGE_WORDS, narrative_target + _MIN_EXHAUSTIVE_PACKAGE_WORDS),
    )


def _exhaustive_depth_issues(artifact: SrsArtifact) -> list[str]:
    """Reject compact or boilerplate-heavy exhaustive outputs before storage."""
    issues: list[str] = []
    types = {item.type for item in artifact.requirements}
    functional_count = sum(item.type == "FUNCTIONAL" for item in artifact.requirements)
    if len(artifact.requirements) < 12:
        issues.append("Exhaustive generation requires at least 12 distinct atomic requirements.")
    if functional_count < 3:
        issues.append("Exhaustive generation requires at least three distinct functional behaviors.")
    for label, expected in (
        ("product and UX", {"BUSINESS", "FUNCTIONAL", "UX"}),
        ("data or interface", {"DATA", "API"}),
        ("trust", {"SECURITY", "PRIVACY", "ACCESSIBILITY"}),
        ("quality and operations", {"NON_FUNCTIONAL", "OPERATIONS", "TEST"}),
    ):
        if not (types & expected):
            issues.append(f"Exhaustive generation is missing the {label} workstream.")

    provenance = artifact.generation_manifest.get("long_form_provenance", {})
    authored_ids = {
        str(value).strip().upper()
        for value in provenance.get("model_authored_section_ids", [])
        if str(value).strip()
    }
    missing_sections = sorted(_SECTION_IDS - authored_ids)
    if missing_sections:
        issues.append("Exhaustive generation is missing model-authored narrative sections: " + ", ".join(missing_sections) + ".")

    authored_words = int(provenance.get("model_authored_narrative_words", 0) or 0)
    narrative_target = int(provenance.get("minimum_model_authored_narrative_words", _MIN_EXHAUSTIVE_NARRATIVE_WORDS) or 0)
    if authored_words < narrative_target:
        issues.append(
            f"Exhaustive generation contains only {authored_words} model-authored narrative words; "
            f"at least {narrative_target} are required for the supplied project evidence."
        )

    authored_sections = [section for section in artifact.narrative_sections if section.id in authored_ids]
    normalized_sections: set[str] = set()
    for section in authored_sections:
        normalized = re.sub(r"\W+", " ", section.content).strip().casefold()
        if normalized in normalized_sections:
            issues.append(f"Exhaustive generation repeats narrative content in {section.id}.")
        normalized_sections.add(normalized)
        words = _word_count(section.content)
        if section.source_status != "UNRESOLVED" and words < _MIN_SUBSTANTIVE_SECTION_WORDS:
            issues.append(f"{section.id} is only {words} words; substantive exhaustive chapters require at least {_MIN_SUBSTANTIVE_SECTION_WORDS} words.")
        if section.source_status == "UNRESOLVED" and (
            words < 40 or not re.search(r"(?i)\b(decision|required|owner|approve|confirm|resolve)\b", section.content)
        ):
            issues.append(f"{section.id} is an unresolved chapter without an actionable decision record.")
        if re.search(r"(?i)(\{\{|\}\}|\b(?:tbd|lorem ipsum|placeholder)\b|this sample intentionally)", section.content):
            issues.append(f"{section.id} contains placeholder or fallback boilerplate.")
    return issues


def _normalize_shall_statements(artifact: SrsArtifact) -> SrsArtifact:
    """Repair only the normative wording required by the governed schema.

    Providers occasionally express an otherwise valid constraint using "must"
    or a descriptive sentence. This preserves the original constraint verbatim
    while making its requirement form explicit; it neither adds facts nor
    changes citations, priorities, or acceptance criteria.
    """
    requirements: list[SrsRequirement] = []
    for requirement in artifact.requirements:
        if " shall " in f" {requirement.statement.lower()} ":
            requirements.append(requirement)
            continue
        statement = requirement.statement.strip()
        if re.match(r"^the\s+.+?\s+must\s+", statement, re.IGNORECASE):
            statement = re.sub(r"\bmust\b", "shall", statement, count=1, flags=re.IGNORECASE)
        else:
            statement = f"The system shall satisfy this constraint: {statement}"
        requirements.append(requirement.model_copy(update={"statement": statement}))
    return artifact.model_copy(update={"requirements": requirements})


def _parse_provider_json(output: str) -> Any:
    """Accept JSON transport fences while keeping the inner schema strict."""
    candidate = output.strip()
    if candidate.startswith("```") and candidate.endswith("```"):
        first_line = candidate.find("\n")
        if first_line < 0:
            raise json.JSONDecodeError("Missing fenced JSON body", candidate, 0)
        candidate = candidate[first_line + 1 :].rsplit("```", 1)[0].strip()
    if candidate.lower().startswith("json"):
        candidate = candidate[4:].lstrip(" \t\r\n:")
    if not candidate.startswith(("{", "[")):
        start = candidate.find("{")
        end = candidate.rfind("}")
        if start >= 0 and end > start:
            candidate = candidate[start : end + 1]
    try:
        return json.loads(candidate)
    except json.JSONDecodeError as json_error:
        # Some providers occasionally return a Python-style object despite a
        # JSON response MIME type. literal_eval is non-executing, and the
        # result still has to pass the strict Pydantic SRS schema below.
        try:
            parsed = ast.literal_eval(candidate)
        except (SyntaxError, ValueError) as literal_error:
            raise json_error from literal_error
        if not isinstance(parsed, (dict, list)):
            raise json_error
        return parsed


def validate_srs(
    artifact: SrsArtifact, evidence: list[RetrievalHit], confirmed_brief: dict[str, Any] | None = None
) -> SrsValidation:
    issues: list[str] = []
    allowed_citations = {(str(hit.source_id), str(hit.chunk_id)) for hit in evidence}
    identifiers: set[str] = set()
    normalized_statements: set[str] = set()
    prefix_by_type = {
        "BUSINESS": "BR", "FUNCTIONAL": "FR", "NON_FUNCTIONAL": "NFR", "SECURITY": "SEC",
        "PRIVACY": "PRIV", "DATA": "DATA", "API": "API", "UX": "UX",
        "ACCESSIBILITY": "ACC", "OPERATIONS": "OPS", "TEST": "TEST",
    }
    supported_numbers = " ".join(
        [json.dumps(confirmed_brief or {}, ensure_ascii=False), *(hit.content for hit in evidence)]
    )
    cited = 0
    for requirement in artifact.requirements:
        if requirement.id in identifiers:
            issues.append(f"Duplicate requirement ID: {requirement.id}.")
        identifiers.add(requirement.id)
        expected_prefix = f"SRS-{prefix_by_type[requirement.type]}-"
        if not requirement.id.startswith(expected_prefix):
            issues.append(f"{requirement.id} does not match requirement type {requirement.type}.")
        normalized = re.sub(r"\W+", " ", requirement.statement).strip().casefold()
        if normalized in normalized_statements:
            issues.append(f"{requirement.id} duplicates another normative statement.")
        normalized_statements.add(normalized)
        if " shall " not in f" {requirement.statement.lower()} ":
            issues.append(f"{requirement.id} is not expressed as a testable shall statement.")
        # Conjunctions frequently join a single data set or condition and are
        # not, by themselves, evidence of a compound requirement. Multiple
        # normative "shall" clauses are the deterministic atomicity failure.
        if len(re.findall(r"\bshall\b", requirement.statement, re.IGNORECASE)) > 1:
            issues.append(f"{requirement.id} contains multiple normative obligations.")
        if not all(item.strip() for item in requirement.acceptance_criteria):
            issues.append(f"{requirement.id} has an empty acceptance criterion.")
        vague = re.search(r"(?i)\b(fast|secure|user[- ]friendly|scalable|highly available|responsive|robust)\b", requirement.statement)
        target_pattern = r"\b\d+(?:\.\d+)?\s*(?:%|ms|s|seconds?|minutes?|hours?|requests?|users?|mb|gb|tb)(?=\s|[.,;:)]|$)"
        measurable = bool(re.search(target_pattern, requirement.statement, re.IGNORECASE))
        if vague and not measurable and requirement.status not in {"ASSUMED", "UNRESOLVED"}:
            issues.append(f"{requirement.id} uses an unmeasurable quality term without marking a decision unresolved.")
        for numeric_target in re.findall(target_pattern, requirement.statement, re.IGNORECASE):
            if numeric_target.casefold() not in supported_numbers.casefold():
                issues.append(f"{requirement.id} contains an unsupported numeric target: {numeric_target}.")
        if requirement.source_kind == "CITATION":
            if not requirement.citations:
                issues.append(f"{requirement.id} claims a citation but has no evidence anchor.")
            else:
                cited += 1
        elif requirement.citations:
            issues.append(f"{requirement.id} is labelled an assumption but includes a citation.")
        for citation in requirement.citations:
            if (str(citation.source_id), str(citation.chunk_id)) not in allowed_citations:
                issues.append(f"{requirement.id} cites evidence outside the approved retrieval set.")
    text = json.dumps(artifact.model_dump(mode="json"), ensure_ascii=False, default=str)
    if _SENSITIVE.search(text):
        issues.append("The draft appears to disclose sensitive credential-like material.")
    coverage = round((cited / len(artifact.requirements)) * 100, 2) if artifact.requirements else 0.0
    accepted = sum(bool(item.acceptance_criteria) for item in artifact.requirements)
    acceptance_coverage = round((accepted / len(artifact.requirements)) * 100, 2) if artifact.requirements else 0.0
    traced = sum(bool(item.source_detail) and (item.source_kind == "ASSUMPTION" or bool(item.citations)) for item in artifact.requirements)
    traceability_coverage = round((traced / len(artifact.requirements)) * 100, 2) if artifact.requirements else 0.0
    completeness_components = (
        min(1.0, len(artifact.narrative_sections) / 12),
        acceptance_coverage / 100,
        traceability_coverage / 100,
        min(1.0, len(artifact.diagrams) / 3),
        min(1.0, len(artifact.standards_applied) / 7),
        1.0 if artifact.generation_manifest else 0.0,
        1.0 if artifact.source_registry else 0.0,
    )
    quality_score = round(sum(completeness_components) / len(completeness_components) * 100, 2)
    return SrsValidation(
        valid=not issues,
        issues=issues,
        citation_coverage=coverage,
        requirement_count=len(artifact.requirements),
        section_count=len(artifact.narrative_sections),
        acceptance_coverage=acceptance_coverage,
        traceability_coverage=traceability_coverage,
        quality_score=quality_score,
    )


def _compile_master_srs(artifact: SrsArtifact, payload: SrsGenerationRequest) -> SrsArtifact:
    """Compile a complete, standards-informed document model from governed facts.

    The model proposes the atomic requirement core. This compiler supplies the
    stable document architecture, registers, viewpoints, and explicit unknowns
    so output depth does not depend on one model remembering every chapter.
    """
    brief = payload.confirmed_brief
    users = _split_named_items(_brief_value(brief, "users", "actors", "stakeholders"), limit=20)
    entities = _split_named_items(_brief_value(brief, "entities", "data", "records"), limit=30)
    integrations = _split_named_items(_brief_value(brief, "integrations"), limit=20)
    problem = _brief_value(brief, "problem") or payload.project.description
    scope = _brief_value(brief, "scope") or artifact.scope
    workflows_text = _brief_value(brief, "workflows", "workflow")
    quality_text = _brief_value(brief, "qualityTargets", "quality_targets")
    risks_text = _brief_value(brief, "risks")
    metrics = _brief_value(brief, "metrics", "successMetrics", "success_metrics")

    requirements: list[SrsRequirement] = []
    for requirement in artifact.requirements:
        inferred_title = _requirement_title(requirement.statement)
        requirements.append(requirement.model_copy(update={
            "title": inferred_title if requirement.title == "Requirement" else requirement.title,
            "status": "ASSUMED" if requirement.source_kind == "ASSUMPTION" else requirement.status,
            "actors": requirement.actors or users,
            "trigger": requirement.trigger if requirement.trigger != "Confirmed workflow event" else "The applicable confirmed workflow reaches this requirement.",
            "failure_behavior": requirement.failure_behavior if requirement.failure_behavior != "Failure behavior requires review." else "Failure handling is unresolved unless explicitly stated in the confirmed workflow or acceptance criteria.",
            "data_involved": requirement.data_involved or [entity for entity in entities if entity.lower() in requirement.statement.lower()],
        }))

    sections = _complete_narrative_sections(
        artifact.narrative_sections,
        _narrative_sections(payload, requirements),
    )
    workflows = artifact.workflows or _compiled_workflows(workflows_text, users, requirements)
    quality_scenarios = artifact.quality_scenarios or _compiled_quality_scenarios(quality_text, requirements)
    risk_register = artifact.risks or _compiled_risks(risks_text)
    decisions = artifact.decisions or _compiled_decisions(brief, artifact.open_questions)
    standards = artifact.standards_applied or _compiled_standards(payload)
    diagrams = artifact.diagrams or _compiled_diagrams(payload, users, entities, integrations, workflows_text)
    objectives = artifact.objectives or [value for value in (problem, metrics) if value]
    terminology = artifact.definitions or _compiled_terminology(users, entities, integrations)
    source_registry = artifact.source_registry or _compiled_sources(payload.evidence)
    executive_summary = artifact.executive_summary
    if executive_summary == "Pending compiled executive summary.":
        executive_summary = (
            f"{payload.project.name} addresses the confirmed problem: {_excerpt(problem, 800)}. "
            f"The first-release boundary is: {_excerpt(scope, 1_000)}. "
            "This specification separates confirmed facts, recommendations, assumptions, unresolved decisions, and exclusions; it does not claim unverified compliance."
        )
    return artifact.model_copy(update={
        "schema_version": "2.0",
        "document_control": artifact.document_control or {
            "status": "Needs stakeholder review",
            "detail_level": "Exhaustive",
            "requirements_profile": payload.profile.name,
            "generation_method": "Governed section compiler",
        },
        "generation_manifest": artifact.generation_manifest or _generation_manifest(payload),
        "executive_summary": executive_summary,
        "objectives": objectives,
        "stakeholders": artifact.stakeholders or users,
        "definitions": terminology,
        "source_registry": source_registry,
        "narrative_sections": sections,
        "workflows": workflows,
        "quality_scenarios": quality_scenarios,
        "risks": risk_register,
        "decisions": decisions,
        "standards_applied": standards,
        "diagrams": diagrams,
        "requirements": requirements,
    })


def _generation_manifest(payload: SrsGenerationRequest) -> dict[str, Any]:
    section_contracts = [
        {"section_id": section_id, "required_context": [context], "status": "COMPILED"}
        for section_id, context in (
            ("INTRODUCTION", "problem and purpose"), ("BUSINESS_CONTEXT", "outcomes and metrics"),
            ("SCOPE", "scope and exclusions"), ("STAKEHOLDERS", "roles and authority"),
            ("WORKFLOWS", "success, alternate, failure, and recovery flows"),
            ("BUSINESS_RULES", "policies and state constraints"), ("DATA", "entities and lifecycle"),
            ("INTEGRATIONS", "external contracts and failure boundaries"),
            ("QUALITY", "measurable quality scenarios"), ("SECURITY_PRIVACY", "risk and protection objectives"),
            ("DELIVERY_OPERATIONS", "deployment and operations"),
            ("VERIFICATION_TRACEABILITY", "acceptance, testing, and source links"),
        )
    ]
    return {
        "schema_version": "1.0",
        "mode": payload.generation_mode,
        "stages": [
            "canonical facts", "applicability", "workstream requirements", "normalization",
            "section compilation", "traceability", "consistency validation", "render verification",
        ],
        "workstream_count": 4 if payload.generation_mode == "EXHAUSTIVE" else 1,
        "section_contracts": section_contracts,
        "source_context": [
            "project title", "project description", "project type", "confirmed discovery brief",
            "selected standards profile", "approved evidence",
        ],
    }


def _compiled_terminology(users: list[str], entities: list[str], integrations: list[str]) -> list[SrsRegisterItem]:
    items = [("Role", value) for value in users] + [("Domain entity", value) for value in entities] + [("External system", value) for value in integrations]
    return [SrsRegisterItem(
        id=f"TERM-{index:03d}", category=category, title=value,
        description=f"Confirmed {category.lower()} name. Its precise definition and synonyms require stakeholder review.",
        status="DERIVED", owner="Business analyst", source_detail="Canonical project brief terminology.",
    ) for index, (category, value) in enumerate(items[:80], 1)]


def _compiled_sources(evidence: list[RetrievalHit]) -> list[SrsRegisterItem]:
    return [SrsRegisterItem(
        id=f"SRC-{index:03d}", category="Approved evidence", title=hit.source_title,
        description="Approved project evidence available for requirement grounding; content is treated as untrusted data.",
        status="CONFIRMED", owner="Project owner",
        source_detail=f"source_id={hit.source_id}; chunk_id={hit.chunk_id}; retrieval_score={hit.score:.4f}",
    ) for index, hit in enumerate(evidence, 1)]


def _narrative_sections(payload: SrsGenerationRequest, requirements: list[SrsRequirement]) -> list[SrsNarrativeSection]:
    brief = payload.confirmed_brief
    aliases = {
        "metrics": ("metrics", "successMetrics", "success_metrics"),
        "users": ("users", "actors", "stakeholders"),
        "businessRules": ("businessRules", "business_rules"),
        "qualityTargets": ("qualityTargets", "quality_targets"),
    }
    result: list[SrsNarrativeSection] = []
    for section_id, title, key, purpose in _SECTION_SPECS:
        value = _brief_value(brief, *(aliases.get(key, (key,))))
        if key == "requirements":
            value = f"{len(requirements)} atomic requirements are registered with acceptance and source traceability."
        if value:
            content = f"Confirmed project context: {_excerpt(value, 5_000)}"
            status = "CONFIRMED"
        else:
            content = f"No confirmed {title.lower()} detail is available. This remains an explicit unresolved decision and must not be inferred during design or implementation."
            status = "UNRESOLVED"
        result.append(SrsNarrativeSection(id=section_id, title=title, purpose=purpose, content=content, source_status=status))
    return result


def _complete_narrative_sections(
    model_sections: list[SrsNarrativeSection], fallback_sections: list[SrsNarrativeSection]
) -> list[SrsNarrativeSection]:
    """Preserve authored chapters while deterministically filling missing contracts.

    Completing missing sections keeps STANDARD and deterministic generation
    usable.  EXHAUSTIVE provider generation is separately rejected when it
    relies on these fallbacks, so completion never disguises a thin model
    response as long-form documentation.
    """
    by_id: dict[str, SrsNarrativeSection] = {}
    for section in model_sections:
        by_id.setdefault(section.id, section)
    return [by_id.get(fallback.id, fallback) for fallback in fallback_sections]


def _compiled_workflows(text: str, users: list[str], requirements: list[SrsRequirement]) -> list[SrsWorkflow]:
    if not text:
        return [SrsWorkflow(
            id="WF-001", title="Primary workflow - decision required", actors=users,
            trigger="The initiating event is not yet confirmed.",
            main_flow=["The primary success path must be confirmed before implementation."],
            alternate_flows=["Alternate paths are unresolved."], failure_recovery=["Failure and recovery behavior are unresolved."],
            requirement_ids=[item.id for item in requirements[:12]],
        )]
    steps = [item.strip(" .") for item in re.split(r"(?:\r?\n|;|\.\s+)", text) if item.strip()]
    return [SrsWorkflow(
        id="WF-001", title="Confirmed primary workflow", actors=users,
        trigger="The workflow is initiated under the conditions described in the confirmed project brief.",
        main_flow=steps[:20] or [text],
        alternate_flows=["Any alternate path not stated in the brief remains unresolved."],
        failure_recovery=["Apply only the failure and recovery behavior explicitly stated in the brief; otherwise request a decision."],
        postconditions=["The confirmed workflow outcome is recorded or an unresolved exception is visible for review."],
        requirement_ids=[item.id for item in requirements if item.type == "FUNCTIONAL"][:40],
    )]


def _compiled_quality_scenarios(text: str, requirements: list[SrsRequirement]) -> list[SrsQualityScenario]:
    linked = next((item for item in requirements if item.type in {"NON_FUNCTIONAL", "SECURITY", "PRIVACY", "ACCESSIBILITY", "OPERATIONS"}), None)
    measure = text or "A measurable response threshold is unresolved and must be approved before release."
    return [SrsQualityScenario(
        id="QS-001", quality_attribute="Highest-priority confirmed quality outcome" if text else "Quality target - decision required",
        source="Confirmed quality-target answer" if text else "Missing project decision",
        stimulus="A representative user or system event exercises the first-release workflow.",
        environment="The confirmed first-release operating environment and expected load.",
        artifact="The product behavior covered by " + (linked.id if linked else "the unresolved quality requirement"),
        response="The system produces the documented outcome without violating confirmed constraints.",
        response_measure=measure,
        status="CONFIRMED" if text else "UNRESOLVED",
    )]


def _compiled_risks(text: str) -> list[SrsRegisterItem]:
    values = _split_items(text, limit=30)
    return [SrsRegisterItem(
        id=f"RISK-{index:03d}", category="Project risk", title=_short_title(value), description=value,
        status="CONFIRMED", source_detail="Confirmed project brief - risks.",
    ) for index, value in enumerate(values, 1)]


def _compiled_decisions(brief: dict[str, Any], open_questions: list[str]) -> list[SrsRegisterItem]:
    values = list(open_questions)
    expected = (
        ("users", "Confirm the named user roles and their authority boundaries."),
        ("workflows", "Confirm the primary workflow, exception paths, and recovery behavior."),
        ("entities", "Confirm data ownership, validation, retention, and deletion rules."),
        ("qualityTargets", "Confirm measurable performance, reliability, security, and accessibility targets."),
        ("constraints", "Confirm fixed delivery constraints and which dimension may move."),
    )
    for key, message in expected:
        aliases = (key, "quality_targets") if key == "qualityTargets" else (key,)
        if not _brief_value(brief, *aliases):
            values.append(message)
    deduplicated = list(dict.fromkeys(item.strip() for item in values if item.strip()))
    return [SrsRegisterItem(
        id=f"DEC-{index:03d}", category="Open decision", title=_short_title(value), description=value,
        status="UNRESOLVED", source_detail="Gap detected in the governed project context.",
    ) for index, value in enumerate(deduplicated[:80], 1)]


def _compiled_standards(payload: SrsGenerationRequest) -> list[SrsRegisterItem]:
    return [SrsRegisterItem(
        id=f"STD-{item['key']}", category="Standards guidance", title=f"{item['name']} {item['version']}",
        description=f"{item['purpose']} {item['applicability_reason']} {item['claim']}",
        status="RECOMMENDED", owner="Documentation reviewer",
        source_detail=f"Official source: {item['official_url']} (checked {item['checked_on']}).",
    ) for item in applicable_standards(payload.project, payload.confirmed_brief)]


def _compiled_diagrams(
    payload: SrsGenerationRequest, users: list[str], entities: list[str], integrations: list[str], workflow: str,
) -> list[SrsDiagram]:
    system_id = _diagram_id(payload.project.name, "SYSTEM")
    context_lines = ["flowchart LR", f'  {system_id}["{_diagram_text(payload.project.name)}"]']
    for index, actor in enumerate(users[:8], 1):
        context_lines.append(f'  ACTOR_{index}["{_diagram_text(actor)}"] --> {system_id}')
    for index, integration in enumerate(integrations[:8], 1):
        context_lines.append(f'  {system_id} --> EXT_{index}["{_diagram_text(integration)}"]')
    if len(context_lines) == 2:
        context_lines.append(f'  UNRESOLVED["Actors and external systems require confirmation"] -.-> {system_id}')

    workflow_steps = [item.strip(" .") for item in re.split(r"(?:\r?\n|;|\.\s+)", workflow) if item.strip()][:10]
    workflow_lines = ["flowchart TD"]
    if workflow_steps:
        for index, step in enumerate(workflow_steps, 1):
            workflow_lines.append(f'  WF_{index}["{_diagram_text(step, 100)}"]')
            if index > 1:
                workflow_lines.append(f"  WF_{index - 1} --> WF_{index}")
        workflow_status = "CONFIRMED"
    else:
        workflow_lines.extend(('  START["Workflow trigger - unresolved"] --> OUTCOME["Expected outcome - unresolved"]',))
        workflow_status = "UNRESOLVED"

    erd_lines = ["erDiagram"]
    for entity in entities[:20]:
        erd_lines.extend((f"  {_diagram_id(entity, 'ENTITY')} {{", "    uuid id PK", "  }"))
    if len(erd_lines) == 1:
        erd_lines.extend(("  UNRESOLVED_ENTITY {", "    uuid id PK", "  }"))
    return [
        SrsDiagram(id="DGM-001", type="C4_CONTEXT", title="System context", notation="MERMAID",
                   source="\n".join(context_lines), rationale="Shows only confirmed actors and external-system boundaries.", status="DERIVED"),
        SrsDiagram(id="DGM-002", type="WORKFLOW", title="Primary workflow", notation="MERMAID",
                   source="\n".join(workflow_lines), rationale="Makes the confirmed sequence and unresolved gaps reviewable.", status=workflow_status),
        SrsDiagram(id="DGM-003", type="ERD", title="Conceptual data model", notation="MERMAID",
                   source="\n".join(erd_lines), rationale="Lists confirmed entities without inventing unsupported relationships.", status="DERIVED" if entities else "UNRESOLVED"),
    ]


def _split_items(value: str, *, limit: int) -> list[str]:
    return [item.strip(" -•\t.") for item in re.split(r"(?:\r?\n|;|•)+", value) if item.strip(" -•\t.")][:limit]


def _split_named_items(value: str, *, limit: int) -> list[str]:
    values: list[str] = []
    for item in re.split(r"(?:\r?\n|;|,|•)+", value):
        candidate = item.strip(" -•\t.")
        if (
            1 < len(candidate) <= 120
            and len(candidate.split()) <= 10
            and candidate.casefold() not in {"null", "none", "n/a", "unknown", "undefined"}
        ):
            values.append(candidate)
    return list(dict.fromkeys(values))[:limit]


def _requirement_title(statement: str) -> str:
    value = re.sub(r"(?i)^the\s+.+?\s+system\s+shall\s+", "", statement).strip(" .")
    value = re.sub(r"(?i)^the\s+system\s+shall\s+", "", value).strip(" .")
    return _short_title(value)


def _short_title(value: str, limit: int = 90) -> str:
    compact = re.sub(r"\s+", " ", value).strip(" .")
    return (compact[:limit].rsplit(" ", 1)[0] if len(compact) > limit else compact) or "Decision required"


def _diagram_id(value: str, prefix: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").upper()
    return f"{prefix}_{normalized[:40] or 'ITEM'}"


def _diagram_text(value: str, limit: int = 80) -> str:
    return re.sub(r"[\"<>]", "'", re.sub(r"\s+", " ", value)).strip()[:limit]


def _check_evidence(evidence: list[RetrievalHit]) -> None:
    if not evidence:
        raise AiServiceError(
            ErrorCode.INSUFFICIENT_EVIDENCE,
            "Approved project evidence is required before an SRS can be generated.",
            status_code=422,
        )
    if any(_INJECTION.search(hit.content) for hit in evidence):
        raise AiServiceError(
            ErrorCode.CONTENT_SAFETY_BLOCKED,
            "An evidence source contains an unsafe instruction override and cannot be used.",
            status_code=422,
        )


def _deterministic_srs(payload: SrsGenerationRequest) -> SrsArtifact:
    """Create a useful, bounded SRS when an external provider is unavailable.

    This is deliberately template-driven rather than a faux LLM. Every
    requirement is derived from a named, confirmed brief section and cites the
    same owner-provided evidence anchor, making the fallback reviewable instead
    of producing a generic placeholder document.
    """
    if _is_healthcare_brief(payload):
        return _healthcare_srs(payload)

    hit = payload.evidence[0]
    project_name = payload.project.name
    brief = payload.confirmed_brief
    problem = _brief_value(brief, "problem")
    scope = _brief_value(brief, "scope")
    workflows = _brief_value(brief, "workflows")
    users = _brief_value(brief, "users", "actors")
    entities = _brief_value(brief, "entities")
    business_rules = _brief_value(brief, "businessRules", "business_rules")
    integrations = _brief_value(brief, "integrations")
    quality_targets = _brief_value(brief, "qualityTargets", "quality_targets")
    constraints = _brief_value(brief, "constraints")
    risks = _brief_value(brief, "risks")

    requirements: list[SrsRequirement] = []
    citation = [{"source_id": hit.source_id, "chunk_id": hit.chunk_id, "label": hit.source_title}]

    def add(
        kind: str, section: str, statement: str, rationale: str, acceptance: str,
        verification: str = "TEST", priority: str = "MUST",
    ) -> None:
        number = 1 + sum(item.type == kind for item in requirements)
        prefix = "FR" if kind == "FUNCTIONAL" else "NFR"
        requirements.append(SrsRequirement(
            id=f"SRS-{prefix}-{number:03d}", type=kind, priority=priority,
            statement=statement, rationale=rationale, acceptance_criteria=[acceptance],
            source_kind="CITATION", source_detail=_source_detail(section, _brief_value(brief, section)),
            verification_method=verification, citations=citation,
        ))

    if workflows:
        add("FUNCTIONAL", "workflows",
            f"The {project_name} system shall support the confirmed workflow: {_excerpt(workflows)}.",
            "The primary workflow is a confirmed delivery outcome.",
            "A test demonstrates the documented workflow from initiation through its stated outcome.")
    if scope:
        add("FUNCTIONAL", "scope",
            f"The {project_name} system shall provide the first-release capability described as: {_excerpt(scope)}.",
            "The first-release scope defines the minimum releasable product boundary.",
            "A release review maps each stated scope outcome to an implemented capability.")
    if entities:
        add("FUNCTIONAL", "entities",
            f"The {project_name} system shall manage the confirmed information: {_excerpt(entities)}.",
            "These records are explicitly identified as information the system must manage.",
            "A test creates, retrieves, and updates each confirmed information type within authorized workflow steps.")
    if users:
        add("FUNCTIONAL", "users",
            f"The {project_name} system shall provide the capabilities required by the confirmed user groups: {_excerpt(users)}.",
            "The confirmed users define who needs to complete the product workflows.",
            "Representative users can complete their stated tasks using only the capabilities assigned to them.")
    if business_rules:
        add("FUNCTIONAL", "businessRules",
            f"The {project_name} system shall enforce the confirmed business rules: {_excerpt(business_rules)}.",
            "Owner-provided policies and approvals must be applied consistently.",
            "Tests cover an allowed case and a rejected case for each documented rule.")
    if integrations:
        add("FUNCTIONAL", "integrations",
            f"The {project_name} system shall exchange the confirmed information with: {_excerpt(integrations)}.",
            "The named external dependency is part of the confirmed project boundary.",
            "An integration test verifies the expected request, response, and failure handling for each confirmed connection.")
    if quality_targets:
        add("NON_FUNCTIONAL", "qualityTargets",
            f"The {project_name} system shall meet the confirmed quality targets: {_excerpt(quality_targets)}.",
            "The project owner identified these qualities as important to successful operation.",
            "A test or operational review produces evidence for every stated quality target.",
            verification="ANALYSIS")
    if constraints:
        add("NON_FUNCTIONAL", "constraints",
            f"The {project_name} system shall operate within the confirmed constraints: {_excerpt(constraints)}.",
            "Delivery and operation must respect the owner-provided constraints.",
            "A release review records compliance with each stated constraint.",
            verification="INSPECTION")
    if risks:
        add("NON_FUNCTIONAL", "risks",
            f"The {project_name} system shall address the confirmed risks: {_excerpt(risks)}.",
            "Known risks require visible controls before release.",
            "A risk review documents a mitigation or an explicit owner decision for each stated risk.",
            verification="INSPECTION")

    # Readiness prevents this in normal operation, but retain a useful
    # evidence-cited minimum document for direct service calls and legacy data.
    if not requirements:
        add("FUNCTIONAL", "problem",
            f"The {project_name} system shall address the confirmed problem: {_excerpt(problem or 'the owner-provided project objective')}.",
            "The confirmed project objective is the minimum basis for the initial SRS.",
            "A reviewer can trace the implemented outcome to the cited project brief.")
    return SrsArtifact(
        schema_version="1.0",
        title=f"{project_name} — Software Requirements Specification",
        scope=(f"This SRS covers the confirmed first-release scope: {_excerpt(scope)}. " if scope else "This SRS is bounded to the confirmed project brief. ")
              + (f"It addresses the stated problem: {_excerpt(problem)}." if problem else "It does not claim unconfirmed capabilities."),
        exclusions=_as_list(_brief_value(brief, "exclusions")) or ["Capabilities not confirmed in the project brief are out of scope."],
        assumptions=[],
        open_questions=[],
        requirements=requirements,
    )


def _brief_value(brief: dict[str, Any], *keys: str) -> str:
    for key in keys:
        value = brief.get(key)
        if isinstance(value, str) and value.strip() and value.strip().casefold() not in {"null", "none", "n/a", "unknown", "undefined"}:
            return value.strip()
    return ""


def _is_healthcare_brief(payload: SrsGenerationRequest) -> bool:
    source = " ".join(
        [payload.project.name, payload.project.description, *(
            value for value in payload.confirmed_brief.values() if isinstance(value, str)
        )]
    ).lower()
    return any(term in source for term in ("clinic", "patient", "doctor", "medical", "healthcare", "prescription"))


def _healthcare_srs(payload: SrsGenerationRequest) -> SrsArtifact:
    """Produce concise, independently testable requirements for a confirmed clinic brief."""
    brief = payload.confirmed_brief
    hit = payload.evidence[0]
    project_name = payload.project.name
    source = " ".join(value for value in brief.values() if isinstance(value, str)).lower()
    requirements: list[SrsRequirement] = []
    citation = [{"source_id": hit.source_id, "chunk_id": hit.chunk_id, "label": hit.source_title}]

    def has(*terms: str) -> bool:
        return any(term in source for term in terms)

    def add(
        kind: str, title: str, statement: str, rationale: str, acceptance: list[str],
        *, source_sections: str, verification: str = "TEST", priority: str = "MUST",
    ) -> None:
        number = 1 + sum(item.type == kind for item in requirements)
        prefix = "FR" if kind == "FUNCTIONAL" else "NFR"
        requirements.append(SrsRequirement(
            id=f"SRS-{prefix}-{number:03d}", type=kind, priority=priority,
            statement=f"The {project_name} system shall {statement}.", rationale=rationale,
            acceptance_criteria=acceptance,
            source_kind="CITATION",
            source_detail=f"Confirmed project brief — {source_sections}.",
            verification_method=verification, citations=citation,
        ))

    if has("register patient", "patient profile", "patient profiles"):
        add("FUNCTIONAL", "Patient registration",
            "create and maintain a unique patient profile with the confirmed contact and clinical identity information",
            "Patient registration and duplicate prevention are part of the confirmed first-release workflow.",
            ["A receptionist can register a patient with all required fields.",
             "A duplicate identifier is rejected without creating a second patient profile."],
            source_sections="scope, entities, and business rules")
    if has("appointment", "doctor availability", "schedule"):
        add("FUNCTIONAL", "Appointment scheduling",
            "schedule an appointment only when the selected clinician is available",
            "The appointment journey requires an availability check before a booking is confirmed.",
            ["An available time slot can be booked and appears on the clinician schedule.",
             "An overlapping booking for the same clinician is rejected."],
            source_sections="workflows, scope, and business rules")
    if has("cancel", "reschedule"):
        add("FUNCTIONAL", "Appointment changes",
            "allow an authorized patient or receptionist to cancel or reschedule an appointment according to the clinic policy",
            "Appointment changes are explicitly included in the confirmed patient and receptionist workflows.",
            ["An eligible appointment can be cancelled or rescheduled.",
             "A change outside the documented policy is rejected and leaves the original appointment unchanged."],
            source_sections="scope, users, and business rules")
    if has("check-in", "check in"):
        add("FUNCTIONAL", "Patient check-in",
            "record patient check-in status for a confirmed appointment",
            "The reception workflow includes confirming the patient's arrival on the appointment day.",
            ["A receptionist can check in a patient with a valid appointment.",
             "The appointment status and check-in time are available to authorized care staff."],
            source_sections="workflows and entities")
    if has("diagnos", "prescription", "visit note", "treatment plan", "medical record"):
        add("FUNCTIONAL", "Clinical documentation",
            "allow an authorized clinician to record the confirmed clinical visit information in the patient's record",
            "The care workflow requires clinicians to record diagnoses, notes, prescriptions, or follow-up information.",
            ["An authorized clinician can add a visit record to the selected patient.",
             "The completed visit record is available to authorized users on a later visit."],
            source_sections="workflows, scope, entities, and users")
    if has("role-based", "permissions", "receptionist", "nurse", "clinic administrator"):
        add("FUNCTIONAL", "Role-based access",
            "authorize actions according to the confirmed patient, clinical, reception, and administrative roles",
            "The brief defines distinct roles with different permitted actions on patient and operational data.",
            ["A permitted role can complete its assigned action.",
             "A role without permission receives an access-denied result and no data is changed."],
            source_sections="users and business rules")
    if has("email", "sms", "reminder", "notification"):
        add("FUNCTIONAL", "Appointment notifications",
            "send the confirmed appointment confirmation and reminder notifications through the configured channels",
            "The confirmed integrations include patient appointment communications.",
            ["A successful booking produces the configured confirmation notification.",
             "A scheduled reminder records its delivery outcome or failure for follow-up."],
            source_sections="scope and integrations")
    if has("upload", "file storage", "medical report", "laboratory result", "scanned"):
        add("FUNCTIONAL", "Clinical attachments",
            "store and retrieve authorized uploaded clinical documents against the relevant patient record",
            "The confirmed integration and entity answers include uploaded reports and supporting documents.",
            ["An authorized user can upload a supported document to a patient record.",
             "An unauthorized user cannot retrieve the document."],
            source_sections="entities and integrations")

    if has("authentication", "privacy", "security", "encrypted", "sensitive"):
        add("NON_FUNCTIONAL", "Protection of patient data",
            "protect patient data with authenticated access and encrypted communication",
            "The confirmed quality goals prioritize privacy and security for sensitive clinical information.",
            ["Unauthenticated access to patient records is rejected.",
             "A security review verifies encrypted transport for authenticated application traffic."],
            source_sections="quality targets, constraints, and business rules", verification="ANALYSIS")
    if has("audit", "logged", "logging"):
        add("NON_FUNCTIONAL", "Auditability",
            "create an audit record for confirmed access and modification events involving patient records",
            "The brief requires accountable access to sensitive clinical information.",
            ["A patient-record access event has an audit entry identifying the user and time.",
             "A patient-record modification has an audit entry identifying the changed record and user."],
            source_sections="quality targets and business rules", verification="INSPECTION")
    if has("99.9", "availability", "uptime", "downtime"):
        target = "99.9%" if "99.9" in source else "the confirmed availability target"
        add("NON_FUNCTIONAL", "Availability",
            f"meet {target} availability for appointment and patient-record access during clinic operating hours",
            "The confirmed quality goals identify availability as necessary for clinic operations.",
            ["Availability monitoring reports the measured service level for the review period.",
             "A planned or unplanned outage is recorded with its impact and recovery time."],
            source_sections="quality targets and risks", verification="ANALYSIS")
    if has("backup", "disaster recovery", "data loss", "corruption"):
        add("NON_FUNCTIONAL", "Recovery",
            "maintain and verify a recovery process for confirmed patient and appointment data",
            "The confirmed risks include loss, corruption, or unavailability of clinical records.",
            ["A recovery test restores a representative patient record and appointment from a backup.",
             "The recovery test result records the restoration time and any data loss."],
            source_sections="quality targets and risks", verification="TEST")

    if not requirements:
        # Defensive fallback for an unusually sparse but healthcare-tagged brief.
        return _generic_minimum_srs(payload)
    scope = _brief_value(brief, "scope") or _brief_value(brief, "problem")
    return SrsArtifact(
        schema_version="1.0", title=f"{project_name} — Software Requirements Specification",
        scope=f"This SRS covers the confirmed clinic first-release outcome: {_first_sentence(scope)}",
        exclusions=_as_list(_brief_value(brief, "exclusions")) or ["Capabilities not confirmed in the project brief are out of scope."],
        assumptions=[], open_questions=[], requirements=requirements,
    )


def _generic_minimum_srs(payload: SrsGenerationRequest) -> SrsArtifact:
    """Prevent recursion when a healthcare-tagged legacy brief has no usable sections."""
    hit = payload.evidence[0]
    requirement = SrsRequirement(
        id="SRS-FR-001", type="FUNCTIONAL", priority="MUST",
        statement=f"The {payload.project.name} system shall address the confirmed project objective.",
        rationale="The owner-confirmed project brief defines the minimum product outcome.",
        acceptance_criteria=["A reviewer can trace the implemented outcome to the confirmed project brief."],
        source_kind="CITATION", source_detail="Confirmed project brief.", verification_method="INSPECTION",
        citations=[{"source_id": hit.source_id, "chunk_id": hit.chunk_id, "label": hit.source_title}],
    )
    return SrsArtifact(schema_version="1.0", title=f"{payload.project.name} — Software Requirements Specification",
                       scope="This SRS is bounded to the confirmed project brief and does not claim unconfirmed capabilities.",
                       exclusions=["Capabilities not confirmed in the project brief are out of scope."], assumptions=[], open_questions=[], requirements=[requirement])


def _excerpt(value: str, limit: int = 900) -> str:
    """Keep a source-derived clause readable and schema-safe."""
    normalized = re.sub(r"\s+", " ", value).strip().rstrip(".")
    # User prose can itself contain normative language; it is evidence, not a
    # second system obligation in the generated requirement sentence.
    normalized = re.sub(r"\bshall\b", "is expected to", normalized, flags=re.IGNORECASE)
    return normalized[:limit].rstrip(" ,;:")


def _first_sentence(value: str, limit: int = 360) -> str:
    sentence = re.split(r"(?<=[.!?])\s+", _excerpt(value, limit))[0]
    return sentence.rstrip(".") + "."


def _source_detail(section: str, value: str) -> str:
    return f"Confirmed project brief — {section}: {_excerpt(value, 280) or 'owner-provided project context'}."


def _as_list(value: str) -> list[str]:
    return [item.strip(" -•\t") for item in re.split(r"(?:\r?\n|;|•)+", value) if item.strip(" -•\t")]
