from __future__ import annotations

from uuid import uuid4

import pytest

from app.errors import AiServiceError, ErrorCode
from app.models import ProjectContext, RetrievalHit, SrsArtifact, SrsGenerationRequest, SrsProfileInput
from app.providers import DeterministicTestProvider
from app.srs import (
    _compile_master_srs,
    _complete_narrative_sections,
    _exhaustive_depth_issues,
    _missing_discovery_issues,
    _normalize_adjacent_duplicate_words,
    _split_named_items,
    generate_srs,
    validate_srs,
)
from app.standards import applicable_standards, registry_payload


def _project(name: str, description: str, project_type: str = "WEB_APP") -> ProjectContext:
    return ProjectContext(id=uuid4(), name=name, description=description, type=project_type)


@pytest.mark.parametrize(
    ("project", "brief", "expected", "excluded"),
    [
        (_project("LocalBook", "Web booking for a local cleaning service"), {"users": "Customers, staff"}, {"ISO_29148", "ISO_12207", "WCAG_22", "OWASP_ASVS"}, {"ISO_42001"}),
        (_project("TenantOps", "Enterprise multi-tenant B2B SaaS platform"), {"integrations": "CRM API"}, {"ISO_27001", "NIST_CSF", "OPENAPI", "JSON_SCHEMA"}, {"ISO_42001"}),
        (_project("CareLedger", "Regulated healthcare portal for patient data"), {"users": "Patients, clinicians", "entities": "Patient record"}, {"ISO_27701", "ISO_27002", "ISO_22301"}, {"ISO_42001"}),
        (_project("ModelReview", "AI-enabled product using an LLM to review applications"), {"risks": "Model errors affect decisions"}, {"ISO_42001", "NIST_AI_RMF"}, set()),
        (_project("DataMesh", "Data-heavy integration platform with event streams"), {"integrations": "REST API, Kafka queue", "entities": "Dataset, message"}, {"OPENAPI", "ASYNCAPI", "JSON_SCHEMA"}, {"ISO_42001"}),
    ],
)
def test_standards_applicability_for_representative_projects(
    project: ProjectContext, brief: dict[str, str], expected: set[str], excluded: set[str]
) -> None:
    keys = {item["key"] for item in applicable_standards(project, brief)}

    assert expected <= keys
    assert not (excluded & keys)


def test_registry_has_reviewable_metadata_and_original_rules() -> None:
    registry = registry_payload()

    assert len(registry) >= 30
    assert all(item["official_url"].startswith("https://") for item in registry)
    assert all(item["checked_on"] == "2026-08-16" for item in registry)
    assert all(item["document_types"] and item["conditions"] and item["rules"] for item in registry)


def test_policy_phrases_are_not_compiled_as_domain_entities() -> None:
    entities = _split_named_items(
        "Professional; Booking; Access only while needed; Cleaner acceptance has a deadline",
        limit=20,
        kind="entity",
    )

    assert entities == ["Professional", "Booking"]


def test_standards_ignore_negated_and_excluded_payment_ai_and_complex_admin_scope() -> None:
    project = _project(
        "SkillLink",
        "A web app for local service booking with no payments or AI features.",
    )
    brief = {
        "scope": "Customers compare professional profiles and request available service appointments.",
        "constraints": (
            "Service booking is the fixed boundary, while excluded payment, subscriptions, analytics, "
            "AI matching, and complex admin capabilities may not enter release one."
        ),
        "exclusions": [
            "Payments and subscriptions are out of scope.",
            "AI matching is excluded.",
            "Complex administration tools are not included.",
        ],
    }

    standards = applicable_standards(project, brief)
    keys = {item["key"] for item in standards}

    assert {"ISO_29148", "ISO_42010", "ISO_25010", "ISO_29119", "RFC_2119_8174", "C4"} <= keys
    assert {"WCAG_22", "OWASP_ASVS"} <= keys
    assert not ({
        "ISO_42001", "NIST_AI_RMF", "NIST_CSF", "NIST_SSDF", "ISO_27001", "ISO_27002",
        "ISO_31000", "ISO_22301", "OWASP_SAMM",
    } & keys)
    assert len(standards) <= 12


@pytest.mark.asyncio
async def test_compiler_builds_manifest_sections_sources_terms_and_diagrams_without_inventing_values() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    payload = SrsGenerationRequest(
        project=_project("LocalBook", "Booking product for a local cleaning service"),
        confirmed_brief={
            "problem": "Customers currently arrange appointments by phone.",
            "scope": "Customers request a cleaning slot and staff confirm or reject it.",
            "users": "Customers, booking staff",
            "workflows": "Customer requests a slot; staff confirms or rejects the request; customer sees the outcome.",
            "entities": "Customer; Booking Request; Service Slot",
            "entityRelationships": [
                {
                    "from": "Customer",
                    "to": "Booking Request",
                    "cardinality": "ONE_TO_MANY",
                    "label": "initiates",
                },
                {
                    "from": "Booking Request",
                    "to": "Service Slot",
                    "cardinality": "ONE_TO_ONE",
                    "label": "reserves",
                },
            ],
            "businessRules": "A service slot cannot be confirmed twice.",
            "exclusions": "Online payment is out of scope.",
        },
        profile=SrsProfileInput(key="ENTERPRISE", name="Enterprise", controls=["Trace requirements"]),
        evidence=[RetrievalHit(
            source_id=source_id,
            chunk_id=chunk_id,
            source_title="Confirmed project brief",
            content="Owner-confirmed discovery answers for LocalBook.",
            score=1.0,
        )],
        generation_mode="EXHAUSTIVE",
    )

    artifact, validation, _ = await generate_srs(payload, DeterministicTestProvider("compiler-test"), correlation_id="compiler-test")

    assert artifact.generation_manifest["mode"] == "EXHAUSTIVE"
    assert artifact.generation_manifest["workstream_count"] == 1
    assert artifact.generation_manifest["workstreams"][0]["id"] == "deterministic_compiler"
    assert artifact.generation_manifest["workstreams"][0]["validation_status"] == "COMPILED_TEST_PROVIDER"
    assert artifact.generation_manifest["validation_status"] == "PASSED"
    assert artifact.generation_manifest["long_form_provenance"]["expected_package_word_target"] >= 9_500
    assert len(artifact.narrative_sections) == 12
    assert {diagram.type for diagram in artifact.diagrams} == {"C4_CONTEXT", "WORKFLOW", "ERD"}
    erd = next(diagram for diagram in artifact.diagrams if diagram.type == "ERD")
    assert "ENTITY_CUSTOMER ||--o{ ENTITY_BOOKING_REQUEST : initiates" in erd.source
    assert erd.status == "CONFIRMED"
    assert {item.category for item in artifact.definitions} >= {"Role", "Domain entity"}
    assert payload.confirmed_brief["scope"] in artifact.inclusions
    assert payload.confirmed_brief["exclusions"] in artifact.exclusions
    assert artifact.source_registry[0].source_detail == f"source_id={source_id}; chunk_id={chunk_id}; retrieval_score=1.0000"
    assert "99.9" not in artifact.model_dump_json()
    assert validation.acceptance_coverage == 100
    assert validation.traceability_coverage == 100
    assert validation.quality_score >= 95

    typo_requirement = artifact.requirements[0].model_copy(update={
        "dependencies": ["Availability Availability calendar"],
    })
    typo_artifact = artifact.model_copy(update={
        "requirements": [typo_requirement, *artifact.requirements[1:]],
    })
    normalized_typo = _normalize_adjacent_duplicate_words(typo_artifact)
    assert normalized_typo.requirements[0].dependencies == ["Availability calendar"]

    with_model_questions = artifact.model_copy(update={
        "open_questions": [
            "What audit-log export format should support operators receive?",
        ],
    })
    governed = _compile_master_srs(with_model_questions, payload)
    assert governed.open_questions == []
    assert governed.generation_manifest["discarded_unconfirmed_open_questions"] == [
        "What audit-log export format should support operators receive?",
    ]

    model_authored = artifact.narrative_sections[0].model_copy(update={
        "content": "ClinicFlow's authored introduction survives compilation. It explains the confirmed product boundary without replacing missing decisions with fabricated implementation detail.",
        "source_status": "CONFIRMED",
    })
    completed = _complete_narrative_sections([model_authored], artifact.narrative_sections)
    assert len(completed) == 12
    assert completed[0].content == model_authored.content
    assert completed[1].id == "BUSINESS_CONTEXT"


@pytest.mark.asyncio
async def test_exhaustive_quality_gate_rejects_compiler_fallback_as_long_form_output() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    payload = SrsGenerationRequest(
        project=_project("LocalBook", "Booking product for a local cleaning service"),
        confirmed_brief={
            "scope": "Customers request a cleaning slot and staff confirm or reject it.",
            "users": "Customers, booking staff",
            "workflows": "Customer requests a slot; staff confirms or rejects the request.",
            "entities": "Customer, Booking request, Service slot",
        },
        profile=SrsProfileInput(key="ENTERPRISE", name="Enterprise", controls=["Trace requirements"]),
        evidence=[RetrievalHit(source_id=source_id, chunk_id=chunk_id, source_title="Confirmed project brief", content="Owner-confirmed discovery answers for LocalBook.", score=1.0)],
        generation_mode="EXHAUSTIVE",
    )

    artifact, _, _ = await generate_srs(payload, DeterministicTestProvider("compiler-test"), correlation_id="compiler-test")

    issues = _exhaustive_depth_issues(artifact)

    assert any("missing model-authored narrative sections" in issue for issue in issues)
    assert any("model-authored narrative words" in issue for issue in issues)

    material = artifact.model_copy(update={
        "open_questions": ["Which actor has authority to confirm a conflicting booking state?"],
    })
    material_issues = _exhaustive_depth_issues(material)
    assert any("material open discovery questions" in issue for issue in material_issues)

    non_material = artifact.model_copy(update={
        "open_questions": ["Which replaceable email vendor should the implementation team select?"],
    })
    non_material_issues = _exhaustive_depth_issues(non_material)
    assert not any("material open discovery questions" in issue for issue in non_material_issues)

    unresolved = artifact.model_copy(update={
        "requirements": [
            artifact.requirements[0].model_copy(update={"status": "UNRESOLVED"}),
            *artifact.requirements[1:],
        ],
    })
    unresolved_issues = _exhaustive_depth_issues(unresolved)
    assert any("unresolved normative decisions" in issue for issue in unresolved_issues)


def test_validator_rejects_wrong_prefix_duplicates_vague_claims_and_unsupported_targets() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    evidence = [RetrievalHit(
        source_id=source_id,
        chunk_id=chunk_id,
        source_title="Approved evidence",
        content="The product must preserve a submitted request.",
        score=1.0,
    )]
    base = {
        "schema_version": "2.0",
        "title": "Quality gate",
        "scope": "This specification covers the confirmed request workflow and nothing beyond that workflow.",
        "requirements": [{
            "id": "SRS-NFR-001",
            "type": "FUNCTIONAL",
            "priority": "MUST",
            "status": "CONFIRMED",
            "statement": "The system shall be fast and available for 99.9% of each month.",
            "rationale": "The workflow needs a quality decision.",
            "acceptance_criteria": ["The quality threshold is measured."],
            "source_kind": "CITATION",
            "source_detail": "Approved evidence.",
            "verification_method": "ANALYSIS",
            "citations": [{"source_id": source_id, "chunk_id": chunk_id, "label": "Approved evidence"}],
        }],
    }
    base["requirements"].append(dict(base["requirements"][0], id="SRS-FR-002"))

    result = validate_srs(SrsArtifact.model_validate(base), evidence, {"scope": "Preserve requests."})

    assert result.valid is False
    assert any("does not match requirement type" in issue for issue in result.issues)
    assert any("duplicates another normative statement" in issue for issue in result.issues)
    assert any("unsupported numeric target" in issue for issue in result.issues)


def test_validator_rejects_duplicate_words_invalid_shall_grammar_and_unsupported_terms() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    evidence = [RetrievalHit(
        source_id=source_id,
        chunk_id=chunk_id,
        source_title="Confirmed booking evidence",
        content="Customers request a service booking and compare a professional profile.",
        score=1.0,
    )]
    citation = {"source_id": source_id, "chunk_id": chunk_id, "label": "Confirmed booking evidence"}
    artifact = SrsArtifact.model_validate({
        "schema_version": "2.0",
        "title": "SkillLink validation fixture",
        "scope": "Customers request a service booking after comparing a professional profile.",
        "requirements": [
            {
                "id": "SRS-FR-001",
                "type": "FUNCTIONAL",
                "priority": "MUST",
                "statement": "The SkillLink system shall confirm the selected professional professional.",
                "rationale": "Customers need a visible booking outcome.",
                "acceptance_criteria": ["The customer sees the confirmed professional."],
                "actors": ["Cleaner"],
                "preconditions": ["A customer selected a professional profile."],
                "data_involved": ["PaymentRecord"],
                "source_kind": "CITATION",
                "source_detail": "Confirmed booking evidence.",
                "verification_method": "TEST",
                "citations": [citation],
            },
            {
                "id": "SRS-FR-002",
                "type": "FUNCTIONAL",
                "priority": "MUST",
                "statement": "The SkillLink system shall shall expose the booking status.",
                "rationale": "Customers need a visible booking status.",
                "acceptance_criteria": ["The customer sees the booking status."],
                "actors": ["Customers"],
                "preconditions": ["A customer requested a booking."],
                "data_involved": ["service booking"],
                "source_kind": "CITATION",
                "source_detail": "Confirmed booking evidence.",
                "verification_method": "TEST",
                "citations": [citation],
            },
        ],
    })

    result = validate_srs(
        artifact,
        evidence,
        {"users": "Customers", "entities": "Professional profile, service booking"},
    )

    assert result.valid is False
    assert any("repeats the adjacent word 'professional'" in issue for issue in result.issues)
    assert any("complete single-sentence SHALL grammar" in issue for issue in result.issues)
    assert any("uses unsupported actor: Cleaner" in issue for issue in result.issues)
    assert any("uses unsupported data term: PaymentRecord" in issue for issue in result.issues)


def test_validator_accepts_only_grounded_unique_api_operation_contracts() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    evidence = [RetrievalHit(
        source_id=source_id,
        chunk_id=chunk_id,
        source_title="Confirmed booking API",
        content="POST /api/bookings uses operation ID createBookingRequest for a customer booking request.",
        score=1.0,
    )]
    requirement = {
        "id": "SRS-API-001",
        "type": "API",
        "priority": "MUST",
        "statement": "The system shall accept the confirmed customer booking request.",
        "rationale": "The confirmed contract exposes the booking request operation.",
        "acceptance_criteria": ["A valid request receives a reviewable booking outcome."],
        "actors": ["customer"],
        "preconditions": ["The customer selected an available booking slot."],
        "api_operation": {
            "path": "/api/bookings",
            "method": "POST",
            "operation_id": "createBookingRequest",
        },
        "source_kind": "CITATION",
        "source_detail": "Confirmed booking API: POST /api/bookings uses operation ID createBookingRequest.",
        "verification_method": "TEST",
        "citations": [{"source_id": source_id, "chunk_id": chunk_id, "label": "Confirmed booking API"}],
    }
    artifact = SrsArtifact.model_validate({
        "schema_version": "2.0",
        "title": "Booking API",
        "scope": "This specification covers the confirmed customer booking API operation.",
        "requirements": [requirement],
    })

    assert validate_srs(artifact, evidence, {"users": "customer"}).valid is True

    unsupported = artifact.model_copy(update={
        "requirements": [artifact.requirements[0].model_copy(update={
            "api_operation": artifact.requirements[0].api_operation.model_copy(update={
                "path": "/api/payments",
                "operation_id": "capturePayment",
            }),
        })],
    })
    result = validate_srs(unsupported, evidence, {"users": "customer"})
    assert result.valid is False
    assert any("HTTP operation contract not confirmed" in issue for issue in result.issues)

    second_evidence = RetrievalHit(
        source_id=uuid4(),
        chunk_id=uuid4(),
        source_title="Confirmed booking status API",
        content="GET /api/bookings/{bookingId} uses operation ID getBookingStatus.",
        score=1.0,
    )
    mismatched = artifact.model_copy(update={
        "requirements": [artifact.requirements[0].model_copy(update={
            "api_operation": artifact.requirements[0].api_operation.model_copy(update={
                "path": "/api/bookings/{bookingId}",
                "method": "GET",
                "operation_id": "getBookingStatus",
            }),
        })],
    })
    result = validate_srs(
        mismatched,
        [*evidence, second_evidence],
        {"users": "customer", "apiContracts": second_evidence.content},
    )
    assert result.valid is False
    assert any("does not match its requirement-local source detail" in issue for issue in result.issues)


def test_incomplete_exhaustive_discovery_returns_focused_missing_decisions() -> None:
    issues = _missing_discovery_issues({
        "scope": "Customer booking first",
        "exclusions": "Payments are excluded",
        "users": "Customers and professionals",
        "workflows": "Customers request an appointment",
        "entities": "Booking and profile records",
        "businessRules": "Cleaner acceptance has a deadline",
        "risks": "Detect quickly and recover",
        "qualityTargets": "Fast and reliable experience",
        "constraints": "Platform or technology is fixed",
        "metrics": "Request-to-confirmation time",
    })

    assert any("first-release inclusions" in issue for issue in issues)
    assert any("workflow triggers, states, success, exceptions, and recovery" in issue for issue in issues)
    assert any("business-rule conditions, decisions, deadlines" in issue for issue in issues)
    assert any("metric numerator, denominator, target, and review window" in issue for issue in issues)


@pytest.mark.asyncio
async def test_incomplete_exhaustive_request_is_rejected_before_the_provider_is_called() -> None:
    class NeverCalledProvider:
        name = "gemini"
        model = "gemma-4-31b-it"

        async def generate_srs(self, request, *, correlation_id):  # type: ignore[no-untyped-def]
            del request, correlation_id
            raise AssertionError("incomplete discovery must fail before model generation")

    payload = SrsGenerationRequest(
        project=_project("SkillLink", "A local service booking web application."),
        confirmed_brief={
            "scope": "Customer booking first",
            "users": "Customers and professionals",
            "workflows": "Customers request an appointment",
        },
        profile=SrsProfileInput(key="STARTER", name="Starter", controls=["Trace requirements"]),
        evidence=[RetrievalHit(
            source_id=uuid4(),
            chunk_id=uuid4(),
            source_title="Partial discovery",
            content="Customers request a local service appointment.",
            score=1.0,
        )],
        generation_mode="EXHAUSTIVE",
    )

    with pytest.raises(AiServiceError) as error:
        await generate_srs(payload, NeverCalledProvider(), correlation_id="missing-discovery")

    assert error.value.code is ErrorCode.INSUFFICIENT_EVIDENCE
    assert "first-release inclusions" in error.value.message
    assert "workflow triggers, states, success, exceptions, and recovery" in error.value.message
