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
_MIN_EXHAUSTIVE_NARRATIVE_WORDS = 8_000
_MIN_EXHAUSTIVE_PACKAGE_WORDS = 9_500
_MAX_EXHAUSTIVE_PACKAGE_WORDS = 50_000
_MIN_SUBSTANTIVE_SECTION_WORDS = 240


async def generate_srs(
    payload: SrsGenerationRequest, provider: GenerationProvider, *, correlation_id: str
) -> tuple[SrsArtifact, SrsValidation, str]:
    """Generate from confirmed brief + approved hits only. No source text is executable instruction."""
    _check_srs_input(payload)
    if payload.generation_mode == "EXHAUSTIVE" and provider.name != "deterministic":
        missing_discovery = _missing_discovery_issues(payload.confirmed_brief)
        if missing_discovery:
            raise AiServiceError(
                ErrorCode.INSUFFICIENT_EVIDENCE,
                "Exhaustive generation is locked until focused discovery resolves: "
                + " ".join(missing_discovery),
                status_code=422,
            )
    generator = getattr(provider, "generate_srs", None)
    if provider.name != "deterministic" and callable(generator):
        try:
            raw = await generator(payload, correlation_id=correlation_id)
        except AiServiceError as exc:
            if not exc.retryable:
                raise
            logger.warning(
                "provider_srs_unavailable_generation_aborted mode=%s code=%s correlation_id=%s",
                payload.generation_mode,
                exc.code,
                correlation_id,
            )
            raise AiServiceError(
                exc.code,
                "SRS generation requires the configured document model. The draft was not replaced with a reduced fallback; please retry when the provider is available.",
                status_code=exc.status_code,
                retryable=True,
            ) from exc
        else:
            try:
                candidate: Any = _parse_provider_json(raw.output) if isinstance(raw.output, str) else raw.output
                candidate = _quarantine_invalid_provider_records(candidate, correlation_id=correlation_id)
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
    provider_workstreams = [
        dict(item) for item in manifest.get("workstreams", []) if isinstance(item, dict)
    ]
    if not provider_workstreams:
        provider_workstreams = [{
            "id": "deterministic_compiler",
            "requested_model": provider.model,
            "actual_model": model,
            "raw_requirement_count": len(artifact.requirements),
            "accepted_requirement_count": len(artifact.requirements),
            "retry_count": 0,
            "validation_status": "COMPILED_TEST_PROVIDER",
        }]
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
        "workstream_count": len(provider_workstreams),
        "workstreams": provider_workstreams,
        "section_contracts": section_contracts,
    })
    artifact = artifact.model_copy(update={"generation_manifest": manifest})
    validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
    if provider.name == "gemini" and payload.generation_mode == "EXHAUSTIVE":
        depth_issues = _exhaustive_depth_issues(artifact, payload)
        if depth_issues:
            validation = validation.model_copy(update={
                "valid": False,
                "issues": [*validation.issues, *depth_issues][:100],
            })
    if not validation.valid and provider.name == "gemini" and payload.generation_mode == "EXHAUSTIVE":
        quality_repairer = getattr(provider, "repair_srs_quality", None)
        if callable(quality_repairer):
            repaired = await quality_repairer(
                payload,
                artifact,
                issues=validation.issues,
                correlation_id=correlation_id,
            )
            if repaired.model and repaired.model != model:
                raise AiServiceError(
                    ErrorCode.PROVIDER_INVALID_OUTPUT,
                    "The final SRS quality repair changed models and was rejected.",
                    status_code=502,
                )
            artifact = _apply_quality_repair(artifact, repaired.output)
            repaired_words = sum(_word_count(section.content) for section in artifact.narrative_sections)
            repaired_manifest = dict(artifact.generation_manifest)
            repaired_provenance = dict(repaired_manifest.get("long_form_provenance", {}))
            repaired_provenance.update({
                "model_authored_section_ids": [section.id for section in artifact.narrative_sections],
                "model_authored_narrative_words": repaired_words,
                "compiled_narrative_words": repaired_words,
            })
            repaired_manifest.update({
                "retry_count": int(repaired_manifest.get("retry_count", 0) or 0) + 1,
                "quality_repair": {
                    "attempted": True,
                    "succeeded": True,
                    "requested_model": model,
                    "actual_model": repaired.model or model,
                    "input_tokens": repaired.usage.input_tokens,
                    "output_tokens": repaired.usage.output_tokens,
                },
                "long_form_provenance": repaired_provenance,
            })
            artifact = artifact.model_copy(update={"generation_manifest": repaired_manifest})
            validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
            depth_issues = _exhaustive_depth_issues(artifact, payload)
            if depth_issues:
                validation = validation.model_copy(update={
                    "valid": False,
                    "issues": [*validation.issues, *depth_issues][:100],
                })
    if any("repeats the adjacent word" in issue for issue in validation.issues):
        artifact = _normalize_adjacent_duplicate_words(artifact)
        validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
        if provider.name == "gemini" and payload.generation_mode == "EXHAUSTIVE":
            depth_issues = _exhaustive_depth_issues(artifact, payload)
            if depth_issues:
                validation = validation.model_copy(update={
                    "valid": False,
                    "issues": [*validation.issues, *depth_issues][:100],
                })
    if any("is not expressed as a testable shall statement." in issue for issue in validation.issues):
        artifact = _normalize_shall_statements(artifact)
        validation = validate_srs(artifact, payload.evidence, payload.confirmed_brief)
        if provider.name == "gemini" and payload.generation_mode == "EXHAUSTIVE":
            depth_issues = _exhaustive_depth_issues(artifact, payload)
            if depth_issues:
                validation = validation.model_copy(update={
                    "valid": False,
                    "issues": [*validation.issues, *depth_issues][:100],
                })
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


def _apply_quality_repair(artifact: SrsArtifact, output: dict[str, Any] | str) -> SrsArtifact:
    """Apply only exact-ID, fully validated model-authored replacements."""
    try:
        patch = _parse_provider_json(output) if isinstance(output, str) else output
        if not isinstance(patch, dict) or set(patch) != {"requirements", "narrative_sections"}:
            raise ValueError("Quality repair must contain exactly requirements and narrative_sections.")
        requirements = [SrsRequirement.model_validate(item) for item in patch["requirements"]]
        sections = [SrsNarrativeSection.model_validate(item) for item in patch["narrative_sections"]]
    except (KeyError, TypeError, ValueError, ValidationError) as exc:
        raise AiServiceError(
            ErrorCode.PROVIDER_INVALID_OUTPUT,
            "The final SRS quality repair failed server validation.",
            status_code=502,
        ) from exc
    existing_requirement_ids = {item.id for item in artifact.requirements}
    existing_section_ids = {item.id for item in artifact.narrative_sections}
    replacement_requirement_ids = [item.id for item in requirements]
    replacement_section_ids = [item.id for item in sections]
    if (
        len(set(replacement_requirement_ids)) != len(replacement_requirement_ids)
        or not set(replacement_requirement_ids).issubset(existing_requirement_ids)
        or set(replacement_section_ids) != existing_section_ids
        or len(replacement_section_ids) != len(existing_section_ids)
    ):
        raise AiServiceError(
            ErrorCode.PROVIDER_INVALID_OUTPUT,
            "The final SRS quality repair attempted an unbounded ID change.",
            status_code=502,
        )
    requirement_by_id = {item.id: item for item in requirements}
    section_by_id = {item.id: item for item in sections}
    return artifact.model_copy(update={
        "requirements": [requirement_by_id.get(item.id, item) for item in artifact.requirements],
        "narrative_sections": [section_by_id[item.id] for item in artifact.narrative_sections],
    })


def _quarantine_invalid_provider_records(candidate: Any, *, correlation_id: str) -> Any:
    """Validate all nested provider records without silently deleting any.

    A previous implementation quarantined individual invalid records and let
    the compiler fill the resulting holes. That could turn four provider
    records into three while the final manifest still appeared successful.
    The quarantine boundary is now the whole candidate: one malformed nested
    record rejects the draft and preserves the last canonical version.
    """
    if not isinstance(candidate, dict):
        return candidate

    normalized = dict(candidate)
    quarantined: list[dict[str, Any]] = []
    for field, model_type in (
        ("narrative_sections", SrsNarrativeSection),
        ("workflows", SrsWorkflow),
        ("quality_scenarios", SrsQualityScenario),
        ("requirements", SrsRequirement),
    ):
        records = normalized.get(field)
        if not isinstance(records, list):
            continue
        accepted: list[dict[str, Any]] = []
        for index, record in enumerate(records):
            if model_type is SrsRequirement and (
                not isinstance(record, dict) or "success_result" not in record
            ):
                quarantined.append({
                    "field": field,
                    "index": index,
                    "id": record.get("id") if isinstance(record, dict) else None,
                    "issues": [{"location": "success_result", "type": "missing"}],
                })
                continue
            try:
                accepted.append(model_type.model_validate(record).model_dump(mode="json"))
            except ValidationError as exc:
                quarantined.append({
                    "field": field,
                    "index": index,
                    "id": record.get("id") if isinstance(record, dict) else None,
                    "issues": [
                        {
                            "location": ".".join(str(part) for part in issue.get("loc", ())),
                            "type": issue.get("type", "unknown"),
                        }
                        for issue in exc.errors()
                    ],
                })
        normalized[field] = accepted

    if quarantined:
        logger.warning(
            "provider_srs_candidate_quarantined records=%s correlation_id=%s",
            quarantined,
            correlation_id,
        )
        raise AiServiceError(
            ErrorCode.PROVIDER_INVALID_OUTPUT,
            "The provider returned one or more malformed SRS records; the complete draft was rejected.",
            status_code=502,
        )
    return normalized


def _evidence_aware_narrative_target(payload: SrsGenerationRequest) -> int:
    """Scale the depth floor to the information actually available.

    Exhaustive generation remains substantial for evidence-rich projects, but
    small or early projects are not forced to invent prose. Individual
    unresolved chapters are checked separately for actionable decision records.
    """
    confirmed_brief = json.dumps(payload.confirmed_brief, ensure_ascii=False, default=str)
    approved_evidence = " ".join(hit.content for hit in payload.evidence)
    grounding_words = _word_count(confirmed_brief) + _word_count(approved_evidence)
    # The floor must prove substantive chapter coverage without rewarding
    # generic expansion. Twelve governed chapters at the per-section floor are
    # stronger evidence than an arbitrary 10x paraphrase of the source brief.
    section_floor = len(_SECTION_SPECS) * _MIN_SUBSTANTIVE_SECTION_WORDS
    return min(_MIN_EXHAUSTIVE_NARRATIVE_WORDS, max(section_floor, grounding_words * 3))


def _evidence_aware_package_target(narrative_target: int) -> int:
    """Require a 25k+ package only when the supplied evidence can support it."""
    return min(
        _MAX_EXHAUSTIVE_PACKAGE_WORDS,
        max(_MIN_EXHAUSTIVE_PACKAGE_WORDS, narrative_target + _MIN_EXHAUSTIVE_PACKAGE_WORDS),
    )


_NON_MATERIAL_IMPLEMENTATION_QUESTION = re.compile(
    r"(?i)\b(?:vendor|framework|library|package|build tool|deployment tool|hosting provider|cloud provider|"
    r"code organization|internal naming|repository layout|programming language|database engine)\b"
)
_MATERIAL_DECISION_SIGNAL = re.compile(
    r"(?i)\b(?:scope|include|exclude|actor|role|authority|permission|workflow|state|status|confirm|decline|"
    r"cancel|reschedul\w*|no[- ]show|entity|ownership|access|retention|deletion|security|privacy|risk|failure|"
    r"recover\w*|retry|performance|reliability|accessibility|target|threshold|metric|deadline|budget|price|rating|"
    r"availability|concurrency|idempotency|endpoint|operation|http|api|path|method)\b"
)


def _material_open_questions(values: list[str]) -> list[str]:
    """Separate blocking product decisions from replaceable implementation choices."""
    material: list[str] = []
    for value in values:
        question = re.sub(r"\s+", " ", value).strip()
        if not question:
            continue
        non_material_implementation = _NON_MATERIAL_IMPLEMENTATION_QUESTION.search(question)
        if non_material_implementation and not _MATERIAL_DECISION_SIGNAL.search(question):
            continue
        material.append(question)
    return material


def _exhaustive_depth_issues(
    artifact: SrsArtifact, payload: SrsGenerationRequest | None = None
) -> list[str]:
    """Reject compact or boilerplate-heavy exhaustive outputs before storage."""
    issues: list[str] = []
    types = {item.type for item in artifact.requirements}
    functional_count = sum(item.type == "FUNCTIONAL" for item in artifact.requirements)
    if len(artifact.requirements) < 20:
        issues.append("Exhaustive generation requires at least 20 distinct atomic requirements for a complete project brief.")
    if functional_count < 5:
        issues.append("Exhaustive generation requires at least five distinct functional behaviors.")
    for label, expected in (
        ("product and UX", {"BUSINESS", "FUNCTIONAL", "UX"}),
        ("data or interface", {"DATA", "API"}),
        ("trust", {"SECURITY", "PRIVACY", "ACCESSIBILITY"}),
        ("quality and operations", {"NON_FUNCTIONAL", "OPERATIONS", "TEST"}),
    ):
        if not (types & expected):
            issues.append(f"Exhaustive generation is missing the {label} workstream.")

    if payload is not None:
        http_context = " ".join([
            json.dumps(payload.confirmed_brief, ensure_ascii=False, default=str),
            *(hit.content for hit in payload.evidence),
        ])
        confirmed_http_contract = re.search(
            r"(?i)\b(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+/[A-Za-z0-9._~!$&'()*+,;=:@%{}/-]+",
            http_context,
        )
        if confirmed_http_contract and not any(
            requirement.type == "API" and requirement.api_operation is not None
            for requirement in artifact.requirements
        ):
            issues.append("Exhaustive generation omitted the confirmed HTTP operation contract from its API requirements.")

    material_questions = _material_open_questions(artifact.open_questions)
    if material_questions:
        issues.append(
            "Exhaustive generation still has material open discovery questions: "
            + " | ".join(material_questions[:5])
            + "."
        )
    unresolved_requirements = [item.id for item in artifact.requirements if item.status == "UNRESOLVED"]
    if unresolved_requirements:
        issues.append(
            "Exhaustive generation contains unresolved normative decisions: "
            + ", ".join(unresolved_requirements[:20])
            + "."
        )
    unresolved_sections = [item.id for item in artifact.narrative_sections if item.source_status == "UNRESOLVED"]
    if unresolved_sections:
        issues.append(
            "Exhaustive generation contains unresolved document chapters: "
            + ", ".join(unresolved_sections[:20])
            + "."
        )
    unresolved_scenarios = [item.id for item in artifact.quality_scenarios if item.status == "UNRESOLVED"]
    if unresolved_scenarios:
        issues.append(
            "Exhaustive generation contains unresolved quality scenarios: "
            + ", ".join(unresolved_scenarios[:20])
            + "."
        )

    workstreams = [
        item for item in artifact.generation_manifest.get("workstreams", []) if isinstance(item, dict)
    ]
    expected_workstreams = {"product", "data_interfaces", "trust", "quality_operations"}
    present_workstreams = {str(item.get("id", "")) for item in workstreams}
    if present_workstreams != expected_workstreams:
        issues.append("Exhaustive generation did not preserve truthful provenance for all four specialist workstreams.")
    for item in workstreams:
        identifier = str(item.get("id", "unknown"))
        if item.get("validation_status") != "PASSED":
            issues.append(f"Exhaustive workstream {identifier} did not pass per-workstream validation.")
        if int(item.get("raw_requirement_count", 0) or 0) < 5:
            issues.append(f"Exhaustive workstream {identifier} returned fewer than five applicable atomic requirements.")
        if int(item.get("accepted_requirement_count", 0) or 0) < 1:
            issues.append(f"Exhaustive workstream {identifier} contributed no semantically unique requirement.")
        requested_model = str(item.get("requested_model", "")).strip()
        actual_model = str(item.get("actual_model", "")).strip()
        if requested_model and actual_model and requested_model != actual_model:
            issues.append(f"Exhaustive workstream {identifier} silently changed models from {requested_model} to {actual_model}.")

    if payload is not None:
        issues.extend(_missing_discovery_issues(payload.confirmed_brief))

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


def _missing_discovery_issues(brief: dict[str, Any]) -> list[str]:
    """Name focused decisions that must exist before exhaustive generation can pass."""
    decisions = (
        ("scope", ("scope", "inclusions", "included"), "Confirm the first-release inclusions and end-to-end customer outcome."),
        ("exclusions", ("exclusions", "outOfScope", "out_of_scope"), "Confirm explicit first-release exclusions."),
        ("users", ("users", "actors", "stakeholders"), "Confirm named actors and their authority boundaries."),
        ("workflows", ("workflows", "workflow"), "Confirm workflow triggers, states, success, exceptions, and recovery."),
        ("entities", ("entities", "data", "records"), "Confirm domain entities, ownership, access, and lifecycle."),
        ("business_rules", ("businessRules", "business_rules"), "Confirm business-rule conditions, decisions, deadlines, and expiration behavior."),
        ("risks", ("risks",), "Confirm material failure events, impact, detection, owner, and recovery."),
        ("quality", ("qualityTargets", "quality_targets"), "Confirm measurable reliability, performance, and accessibility targets."),
        ("constraints", ("constraints",), "Confirm exact platform, schedule, budget, and scope constraints."),
        ("metrics", ("metrics", "successMetrics", "success_metrics"), "Confirm metric numerator, denominator, target, and review window."),
    )
    issues: list[str] = []
    for kind, keys, question in decisions:
        value = _brief_value(brief, *keys)
        if not value or not _decision_is_complete(kind, value):
            issues.append("Missing discovery decision: " + question)
    return issues


def _looks_like_incomplete_decision(value: str) -> bool:
    normalized = re.sub(r"\s+", " ", value).strip().casefold()
    vague_values = {
        "customer booking first",
        "access only while needed",
        "detect quickly and recover",
        "platform or technology is fixed",
        "operational owner has final authority",
        "request-to-confirmation time",
        "cleaner acceptance has a deadline",
        "customers request an appointment",
        "fast and reliable experience",
    }
    return len(normalized.split()) < 4 or normalized in vague_values


def _decision_is_complete(kind: str, value: str) -> bool:
    """Apply a small, evidence-facing completeness check to each decision."""
    normalized = re.sub(r"\s+", " ", value).strip().casefold()
    if _looks_like_incomplete_decision(normalized):
        return False
    minimum_words = {
        "scope": 8,
        "exclusions": 4,
        "users": 6,
        "workflows": 12,
        "entities": 8,
        "business_rules": 8,
        "risks": 10,
        "quality": 8,
        "constraints": 8,
        "metrics": 10,
    }[kind]
    if len(re.findall(r"[a-z0-9]+", normalized)) < minimum_words:
        return False
    detail_patterns = {
        "scope": r"\b(first release|release one|in scope|covers?|includes?)\b",
        "exclusions": r"\b(exclude(?:d|s)?|out of scope|not included|deferred?|without|no)\b",
        "users": r"\b(can|may|owns?|authority|submit(?:s)?|review(?:s)?|book(?:s)?|manage(?:s)?)\b",
        "workflows": r"\b(if|when|then|reject(?:s|ed)?|return(?:s|ed)?|fail(?:s|ed|ure)?|conflict|cancel(?:s|led)?|expire(?:s|d)?|retry|recover(?:y|s|ed)?)\b",
        "entities": r"\b(owns?|access|retain(?:s|ed)?|delete(?:s|d)?|status|lifecycle|created?|updated?)\b",
        "business_rules": r"\b(only|cannot|must|within|deadline|expire(?:s|d)?|before|after|when|if)\b",
        "risks": r"\b(impact|detect(?:s|ed|ion)?|recover(?:y|s|ed)?|prevent(?:s|ed)?|loss|fail(?:s|ed|ure)?|conflict|owner)\b",
        "quality": r"(?:\b(?:measure(?:d|ment)?|percentile|seconds?|minutes?|keyboard|wcag|availability|rate)\b|\d)",
        "constraints": r"\b(web|mobile|platform|technology|deadline|schedule|budget|release|fixed|outside)\b",
        "metrics": r"\b(numerator|denominator|target|window|percentage|percentile|median|rate|daily|weekly|monthly|quarterly|month|week)\b",
    }
    return re.search(detail_patterns[kind], normalized) is not None


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


def _normalize_adjacent_duplicate_words(artifact: SrsArtifact) -> SrsArtifact:
    """Remove mechanical adjacent-word duplication without changing facts."""
    pattern = re.compile(r"(?i)\b([a-z][a-z'-]{1,})\b(?:\s|[\u00a0])+\1\b")

    def normalize(value: Any) -> Any:
        if isinstance(value, str):
            previous = None
            while value != previous:
                previous = value
                value = pattern.sub(r"\1", value)
            return value
        if isinstance(value, list):
            return [normalize(item) for item in value]
        if isinstance(value, dict):
            return {key: normalize(item) for key, item in value.items()}
        return value

    return SrsArtifact.model_validate(normalize(artifact.model_dump(mode="json")))


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


def _adjacent_duplicate_word(value: str) -> str | None:
    match = re.search(r"(?i)\b([a-z][a-z'-]{1,})\b(?:\s|[\u00a0])+\1\b", value)
    return match.group(1) if match else None


def _artifact_strings(value: Any, path: str = "artifact") -> list[tuple[str, str]]:
    if isinstance(value, dict):
        return [
            item
            for key, nested in value.items()
            for item in _artifact_strings(nested, f"{path}.{key}" if path else str(key))
        ]
    if isinstance(value, list):
        return [
            item
            for index, nested in enumerate(value)
            for item in _artifact_strings(nested, f"{path}.{index}")
        ]
    return [(path.removeprefix("artifact."), value)] if isinstance(value, str) else []


_GENERIC_DOMAIN_TERMS = {
    "application", "authorized user", "end user", "platform", "project owner", "service", "system", "user",
}
_DOMAIN_STOPWORDS = {
    "a", "an", "and", "applicable", "authorized", "confirmed", "for", "of", "or", "record", "records",
    "application", "platform", "record", "records", "selected", "service", "system", "the", "to", "user", "users",
}


def _is_grounded_domain_term(term: str, grounding_text: str) -> bool:
    camel_split = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", term)
    normalized = re.sub(r"[^a-z0-9]+", " ", camel_split.casefold()).strip()
    if not normalized or normalized in _GENERIC_DOMAIN_TERMS or normalized in grounding_text:
        return True
    tokens = [token for token in normalized.split() if token not in _DOMAIN_STOPWORDS and len(token) >= 3]
    if not tokens:
        return True
    grounding_tokens = set(re.findall(r"[a-z0-9]+", grounding_text))
    for token in tokens:
        variants = {token}
        if token.endswith("ies") and len(token) > 4:
            variants.add(token[:-3] + "y")
        if token.endswith("s") and len(token) > 3:
            variants.add(token[:-1])
        if not any(variant in grounding_tokens for variant in variants):
            return False
    return True


def _capitalized_domain_terms(statement: str) -> list[str]:
    action = re.split(r"(?i)\bshall\b", statement, maxsplit=1)[-1]
    allowed = {"API", "HTTP", "ID", "MUST", "PII", "SHALL", "UI", "URL"}
    return [
        match.group(0)
        for match in re.finditer(r"\b[A-Z][A-Za-z0-9_-]{2,}\b", action)
        if match.group(0) not in allowed
    ]


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
    grounding_text = " ".join(
        [json.dumps(confirmed_brief or {}, ensure_ascii=False), *(hit.content for hit in evidence)]
    ).casefold()
    cited = 0
    api_operation_ids: set[str] = set()
    api_routes: set[tuple[str, str]] = set()
    normalized_grounding_text = re.sub(r"\s+", " ", grounding_text)
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
        shall_count = len(re.findall(r"\bshall\b", requirement.statement, re.IGNORECASE))
        if shall_count > 1:
            issues.append(f"{requirement.id} contains multiple normative obligations.")
        stripped_statement = requirement.statement.strip()
        has_internal_sentence_end = bool(re.search(r"[.!?]\s+\S", stripped_statement[:-1]))
        if shall_count != 1 or has_internal_sentence_end or not re.match(
            r"(?is)^The\s+.+?\s+shall\s+(?!shall\b)\S.+[.!?]$",
            stripped_statement,
        ):
            issues.append(f"{requirement.id} does not use a complete single-sentence SHALL grammar.")
        duplicate = _adjacent_duplicate_word(requirement.statement)
        if duplicate:
            issues.append(f"{requirement.id} repeats the adjacent word '{duplicate}'.")
        if not all(item.strip() for item in requirement.acceptance_criteria):
            issues.append(f"{requirement.id} has an empty acceptance criterion.")
        for field, terms in (("actor", requirement.actors), ("data term", requirement.data_involved)):
            for term in terms:
                if not _is_grounded_domain_term(term, grounding_text):
                    issues.append(f"{requirement.id} uses unsupported {field}: {term}.")
        for term in _capitalized_domain_terms(requirement.statement):
            if not _is_grounded_domain_term(term, grounding_text):
                issues.append(f"{requirement.id} uses unsupported domain term: {term}.")
        if requirement.type == "API":
            operation = requirement.api_operation
            if operation is None:
                issues.append(f"{requirement.id} is an API requirement without a confirmed HTTP operation contract.")
            else:
                operation_id = operation.operation_id.casefold()
                route = (operation.method, operation.path.casefold())
                if operation_id in api_operation_ids:
                    issues.append(f"{requirement.id} duplicates API operation ID {operation.operation_id}.")
                api_operation_ids.add(operation_id)
                if route in api_routes:
                    issues.append(f"{requirement.id} duplicates API route {operation.method} {operation.path}.")
                api_routes.add(route)
                signature = f"{operation.method} {operation.path}".casefold()
                if signature not in normalized_grounding_text or operation_id not in normalized_grounding_text:
                    issues.append(
                        f"{requirement.id} uses an HTTP operation contract not confirmed by the project evidence."
                    )
                local_contract_text = re.sub(
                    r"\s+",
                    " ",
                    " ".join((
                        requirement.title,
                        requirement.statement,
                        requirement.source_detail,
                        requirement.trigger,
                    )).casefold(),
                )
                if signature not in local_contract_text and operation_id not in local_contract_text:
                    issues.append(
                        f"{requirement.id} HTTP operation contract does not match its requirement-local source detail."
                    )
        elif requirement.api_operation is not None:
            issues.append(f"{requirement.id} attaches an HTTP operation contract to non-API type {requirement.type}.")
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
    for location, value in _artifact_strings(artifact.model_dump(mode="json")):
        duplicate = _adjacent_duplicate_word(value)
        if duplicate and not re.fullmatch(r"requirements\.\d+\.statement", location):
            issues.append(f"{location} repeats the adjacent word '{duplicate}'.")
            if len(issues) >= 100:
                break
    if confirmed_brief:
        confirmed_inclusions = _as_list(_brief_value(confirmed_brief, "inclusions", "included", "scope"))
        confirmed_exclusions = _as_list(_brief_value(
            confirmed_brief, "exclusions", "outOfScope", "out_of_scope", "avoid"
        ))
        for label, confirmed_values, compiled_values in (
            ("inclusion", confirmed_inclusions, artifact.inclusions),
            ("exclusion", confirmed_exclusions, artifact.exclusions),
        ):
            normalized_compiled = {re.sub(r"\s+", " ", item).strip().casefold() for item in compiled_values}
            for value in confirmed_values:
                normalized = re.sub(r"\s+", " ", value).strip().casefold()
                if normalized and normalized not in normalized_compiled:
                    issues.append(f"Confirmed {label} was not preserved: {value}.")
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
        issues=issues[:100],
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
    users = _split_named_items(_brief_value(brief, "users", "actors", "stakeholders"), limit=20, kind="actor")
    entities = _split_named_items(_brief_value(brief, "entities", "data", "records"), limit=30, kind="entity")
    integrations = _split_named_items(_brief_value(brief, "integrations"), limit=20, kind="integration")
    problem = _brief_value(brief, "problem") or payload.project.description
    confirmed_scope = _brief_value(brief, "scope")
    scope = confirmed_scope or artifact.scope
    workflows_text = _brief_value(brief, "workflows", "workflow")
    quality_text = _brief_value(brief, "qualityTargets", "quality_targets")
    risks_text = _brief_value(brief, "risks")
    metrics = _brief_value(brief, "metrics", "successMetrics", "success_metrics")
    entity_relationships = _confirmed_entity_relationships(brief, entities)

    requirements: list[SrsRequirement] = []
    for requirement in artifact.requirements:
        inferred_title = _requirement_title(requirement.statement)
        requirements.append(requirement.model_copy(update={
            "title": inferred_title if requirement.title == "Requirement" else requirement.title,
            "status": "ASSUMED" if requirement.source_kind == "ASSUMPTION" else requirement.status,
            "actors": requirement.actors or users,
            "trigger": requirement.trigger if requirement.trigger != "Confirmed workflow event" else "The applicable confirmed workflow reaches this requirement.",
            "success_result": (
                requirement.acceptance_criteria[0]
                if requirement.success_result == "The stated acceptance outcome is observable."
                else requirement.success_result
            ),
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
    confirmed_open_questions = _as_list(_brief_value(brief, "openQuestions", "open_questions"))
    if payload.generation_mode == "EXHAUSTIVE":
        # Exhaustive generation is entered only after the discovery readiness
        # gate. The model may not reopen product decisions that are absent from
        # the confirmed brief; doing so would turn speculation into a blocker.
        open_questions = _merge_confirmed_text([], confirmed_open_questions, limit=30)
        confirmed_normalized = {
            re.sub(r"\s+", " ", item).strip().casefold() for item in confirmed_open_questions
        }
        discarded_unconfirmed_open_questions = [
            item for item in artifact.open_questions
            if re.sub(r"\s+", " ", item).strip().casefold() not in confirmed_normalized
        ]
    else:
        open_questions = _merge_confirmed_text(
            artifact.open_questions,
            confirmed_open_questions,
            limit=30,
        )
        discarded_unconfirmed_open_questions = []
    decisions = artifact.decisions or _compiled_decisions(brief, open_questions)
    standards = artifact.standards_applied or _compiled_standards(payload)
    diagrams = artifact.diagrams or _compiled_diagrams(
        payload, users, entities, integrations, workflows_text, entity_relationships,
    )
    objectives = _merge_confirmed_text(artifact.objectives, [value for value in (problem, metrics) if value], limit=30)
    stakeholders = _merge_confirmed_text(artifact.stakeholders, users, limit=40)
    inclusions = _merge_confirmed_text(
        artifact.inclusions,
        _as_list(_brief_value(brief, "inclusions", "included", "scope")),
        limit=100,
        confirmed_first=True,
    )
    exclusions = _merge_confirmed_text(
        artifact.exclusions,
        _as_list(_brief_value(brief, "exclusions", "outOfScope", "out_of_scope", "avoid")),
        limit=100,
        confirmed_first=True,
    )
    assumptions = _merge_confirmed_text(
        artifact.assumptions,
        _as_list(_brief_value(brief, "assumptions")),
        limit=30,
    )
    terminology = artifact.definitions or _compiled_terminology(users, entities, integrations)
    source_registry = artifact.source_registry or _compiled_sources(payload.evidence)
    executive_summary = artifact.executive_summary
    if executive_summary == "Pending compiled executive summary.":
        executive_summary = (
            f"{payload.project.name} addresses the confirmed problem: {_excerpt(problem, 800)}. "
            f"The first-release boundary is: {_excerpt(scope, 1_000)}. "
            "This specification separates confirmed facts, recommendations, assumptions, unresolved decisions, and exclusions; it does not claim unverified compliance."
        )
    generation_manifest = _generation_manifest(payload)
    generation_manifest.update(artifact.generation_manifest)
    generation_manifest["mode"] = payload.generation_mode
    generation_manifest["workstream_count"] = len(generation_manifest.get("workstreams", [])) or generation_manifest["workstream_count"]
    generation_manifest["discarded_unconfirmed_open_questions"] = discarded_unconfirmed_open_questions
    generation_manifest["open_question_classification"] = [
        {
            "question": question,
            "material": bool(_material_open_questions([question])),
            "classification": (
                "BLOCKING_DISCOVERY"
                if _material_open_questions([question])
                else "NON_BLOCKING_IMPLEMENTATION_SELECTION"
            ),
        }
        for question in open_questions
    ]
    return artifact.model_copy(update={
        "schema_version": "2.0",
        "document_control": artifact.document_control or {
            "status": "Needs stakeholder review",
            "detail_level": payload.generation_mode.title(),
            "requirements_profile": payload.profile.name,
            "generation_method": "Governed section compiler",
        },
        "generation_manifest": generation_manifest,
        "executive_summary": executive_summary,
        "scope": (
            f"This SRS covers the confirmed first-release scope: {_excerpt(confirmed_scope, 5_800)}."
            if confirmed_scope else artifact.scope
        ),
        "objectives": objectives,
        "stakeholders": stakeholders,
        "inclusions": inclusions,
        "exclusions": exclusions,
        "assumptions": assumptions,
        "open_questions": open_questions,
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
    payload: SrsGenerationRequest,
    users: list[str],
    entities: list[str],
    integrations: list[str],
    workflow: str,
    entity_relationships: list[tuple[str, str, str, str]] | None = None,
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
    for from_entity, to_entity, connector, label in entity_relationships or []:
        erd_lines.append(
            f"  {_diagram_id(from_entity, 'ENTITY')} {connector} "
            f"{_diagram_id(to_entity, 'ENTITY')} : {_diagram_text(label, 60)}"
        )
    if len(erd_lines) == 1:
        erd_lines.extend(("  UNRESOLVED_ENTITY {", "    uuid id PK", "  }"))
    return [
        SrsDiagram(id="DGM-001", type="C4_CONTEXT", title="System context", notation="MERMAID",
                   source="\n".join(context_lines), rationale="Shows only confirmed actors and external-system boundaries.", status="DERIVED"),
        SrsDiagram(id="DGM-002", type="WORKFLOW", title="Primary workflow", notation="MERMAID",
                   source="\n".join(workflow_lines), rationale="Makes the confirmed sequence and unresolved gaps reviewable.", status=workflow_status),
        SrsDiagram(id="DGM-003", type="ERD", title="Conceptual data model", notation="MERMAID",
                   source="\n".join(erd_lines), rationale=(
                       "Shows only entities and cardinalities explicitly confirmed in the project brief."
                       if entity_relationships else
                       "Lists confirmed entities without inventing unsupported relationships."
                   ), status="CONFIRMED" if entity_relationships else ("DERIVED" if entities else "UNRESOLVED")),
    ]


def _confirmed_entity_relationships(
    brief: dict[str, Any],
    entities: list[str],
) -> list[tuple[str, str, str, str]]:
    """Read only explicit, structured entity cardinalities from the brief."""
    raw = brief.get("entityRelationships", brief.get("entity_relationships", []))
    if not isinstance(raw, list):
        return []
    entity_names = {item.casefold(): item for item in entities}
    connectors = {
        "ONE_TO_ONE": "||--||",
        "ONE_TO_ZERO_OR_ONE": "||--o|",
        "ONE_TO_MANY": "||--o{",
        "ZERO_OR_ONE_TO_MANY": "o|--o{",
        "MANY_TO_MANY": "}o--o{",
    }
    relationships: list[tuple[str, str, str, str]] = []
    seen: set[tuple[str, str, str]] = set()
    for item in raw[:40]:
        if not isinstance(item, dict):
            continue
        from_entity = entity_names.get(str(item.get("from", "")).strip().casefold())
        to_entity = entity_names.get(str(item.get("to", "")).strip().casefold())
        cardinality = str(item.get("cardinality", "")).strip().upper()
        label = re.sub(r"\s+", " ", str(item.get("label", "")).strip())
        if (
            from_entity is None
            or to_entity is None
            or from_entity == to_entity
            or cardinality not in connectors
            or not (2 <= len(label) <= 60)
        ):
            continue
        key = (from_entity.casefold(), to_entity.casefold(), label.casefold())
        if key in seen:
            continue
        seen.add(key)
        relationships.append((from_entity, to_entity, connectors[cardinality], label))
    return relationships


def _split_items(value: str, *, limit: int) -> list[str]:
    return [item.strip(" -•\t.") for item in re.split(r"(?:\r?\n|;|•)+", value) if item.strip(" -•\t.")][:limit]


def _split_named_items(value: str, *, limit: int, kind: str = "item") -> list[str]:
    values: list[str] = []
    for item in re.split(r"(?:\r?\n|;|,|•)+", value):
        candidate = item.strip(" -•\t.")
        normalized = candidate.casefold()
        policy_phrase = bool(re.search(
            r"(?i)\b(?:only|while|when|until|unless|must|shall|should|needed|controls?|allows?|prevents?|"
            r"expires?|recovers?|acceptance|deadline|authority|decision|threshold)\b",
            candidate,
        ))
        if (
            1 < len(candidate) <= 120
            and len(candidate.split()) <= 10
            and normalized not in {"null", "none", "n/a", "unknown", "undefined"}
            and (kind != "entity" or not policy_phrase)
        ):
            values.append(candidate)
    return list(dict.fromkeys(values))[:limit]


def _merge_confirmed_text(
    existing: list[str], confirmed: list[str], *, limit: int, confirmed_first: bool = False
) -> list[str]:
    """Append canonical brief facts without losing useful provider analysis."""
    merged: list[str] = []
    seen: set[str] = set()
    values = (*confirmed, *existing) if confirmed_first else (*existing, *confirmed)
    for value in values:
        normalized = re.sub(r"\s+", " ", value).strip()
        key = normalized.casefold()
        if not normalized or key in seen:
            continue
        seen.add(key)
        merged.append(normalized)
    return merged[:limit]


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


def _check_srs_input(payload: SrsGenerationRequest) -> None:
    """Keep untrusted project data out of the provider prompt when unsafe.

    Discovery answers are owner-confirmed facts, but remain untrusted text. A
    direct internal caller must not be able to bypass the same injection and
    credential boundary applied to retrieval evidence.
    """
    if not payload.evidence:
        raise AiServiceError(
            ErrorCode.INSUFFICIENT_EVIDENCE,
            "Approved project evidence is required before an SRS can be generated.",
            status_code=422,
        )
    untrusted_values = [
        payload.project.name,
        payload.project.description or "",
        json.dumps(payload.confirmed_brief, ensure_ascii=False, default=str),
        *(hit.content for hit in payload.evidence),
    ]
    if any(_INJECTION.search(value) for value in untrusted_values):
        raise AiServiceError(
            ErrorCode.CONTENT_SAFETY_BLOCKED,
            "Project input contains an unsafe instruction override and cannot be used.",
            status_code=422,
        )
    if any(_SENSITIVE.search(value) for value in untrusted_values):
        raise AiServiceError(
            ErrorCode.CONTENT_SAFETY_BLOCKED,
            "Project input appears to include a credential and cannot be sent to the generation provider.",
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
    if len(normalized) <= limit:
        return normalized
    boundary = normalized[:limit].rsplit(" ", 1)[0].rstrip(" ,;:")
    return f"{boundary or normalized[:limit].rstrip(' ,;:')}..."


def _first_sentence(value: str, limit: int = 360) -> str:
    sentence = re.split(r"(?<=[.!?])\s+", _excerpt(value, limit))[0]
    return sentence.rstrip(".") + "."


def _source_detail(section: str, value: str) -> str:
    return f"Confirmed project brief — {section}: {_excerpt(value, 280) or 'owner-provided project context'}."


def _as_list(value: str) -> list[str]:
    return [item.strip(" -•\t") for item in re.split(r"(?:\r?\n|;|•)+", value) if item.strip(" -•\t")]
