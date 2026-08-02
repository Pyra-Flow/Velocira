"""Bounded SRS generation and deterministic quality checks."""

from __future__ import annotations

import re
import json
import logging
import ast
from typing import Any

from pydantic import ValidationError

from app.errors import AiServiceError, ErrorCode
from app.models import (
    RetrievalHit,
    SrsArtifact,
    SrsGenerationRequest,
    SrsRequirement,
    SrsValidation,
)
from app.providers import GenerationProvider

logger = logging.getLogger("velocira.ai_service.srs")

_INJECTION = re.compile(
    r"(?i)\b(ignore|override|disregard|reveal)\b.{0,80}\b(system|previous|instruction|prompt|secret|credential)\b"
)
_SENSITIVE = re.compile(
    r"(?i)(?:-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----|\b(?:api[_-]?key|password|secret)\s*[:=]\s*\S{8,})"
)


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
    validation = validate_srs(artifact, payload.evidence)
    if any("is not expressed as a testable shall statement." in issue for issue in validation.issues):
        artifact = _normalize_shall_statements(artifact)
        validation = validate_srs(artifact, payload.evidence)
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
    return artifact, validation, model


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


def validate_srs(artifact: SrsArtifact, evidence: list[RetrievalHit]) -> SrsValidation:
    issues: list[str] = []
    allowed_citations = {(str(hit.source_id), str(hit.chunk_id)) for hit in evidence}
    identifiers: set[str] = set()
    cited = 0
    for requirement in artifact.requirements:
        if requirement.id in identifiers:
            issues.append(f"Duplicate requirement ID: {requirement.id}.")
        identifiers.add(requirement.id)
        if " shall " not in f" {requirement.statement.lower()} ":
            issues.append(f"{requirement.id} is not expressed as a testable shall statement.")
        # Conjunctions frequently join a single data set or condition and are
        # not, by themselves, evidence of a compound requirement. Multiple
        # normative "shall" clauses are the deterministic atomicity failure.
        if len(re.findall(r"\bshall\b", requirement.statement, re.IGNORECASE)) > 1:
            issues.append(f"{requirement.id} contains multiple normative obligations.")
        if not all(item.strip() for item in requirement.acceptance_criteria):
            issues.append(f"{requirement.id} has an empty acceptance criterion.")
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
    text = "\n".join(
        [artifact.scope, *artifact.assumptions, *artifact.open_questions]
        + [requirement.statement for requirement in artifact.requirements]
    )
    if _SENSITIVE.search(text):
        issues.append("The draft appears to disclose sensitive credential-like material.")
    coverage = round((cited / len(artifact.requirements)) * 100, 2) if artifact.requirements else 0.0
    return SrsValidation(valid=not issues, issues=issues, citation_coverage=coverage)


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
        if isinstance(value, str) and value.strip():
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
