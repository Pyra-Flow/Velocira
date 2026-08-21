from __future__ import annotations

from uuid import uuid4

import pytest

from app.models import ProjectContext, RetrievalHit, SrsArtifact, SrsGenerationRequest, SrsProfileInput
from app.providers import DeterministicTestProvider
from app.srs import _complete_narrative_sections, _exhaustive_depth_issues, generate_srs, validate_srs
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
            "entities": "Customer, Booking request, Service slot",
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
    assert artifact.generation_manifest["workstream_count"] == 4
    assert artifact.generation_manifest["validation_status"] == "PASSED"
    assert artifact.generation_manifest["long_form_provenance"]["expected_package_word_target"] >= 9_500
    assert len(artifact.narrative_sections) == 12
    assert {diagram.type for diagram in artifact.diagrams} == {"C4_CONTEXT", "WORKFLOW", "ERD"}
    assert {item.category for item in artifact.definitions} >= {"Role", "Domain entity"}
    assert artifact.source_registry[0].source_detail == f"source_id={source_id}; chunk_id={chunk_id}; retrieval_score=1.0000"
    assert "99.9" not in artifact.model_dump_json()
    assert validation.acceptance_coverage == 100
    assert validation.traceability_coverage == 100
    assert validation.quality_score >= 95

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
