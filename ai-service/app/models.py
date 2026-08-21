"""Pydantic request, response, and reproducibility schemas."""

from __future__ import annotations

from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class PromptReference(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(min_length=1, max_length=120)
    version: str = Field(min_length=1, max_length=64)
    content: str = Field(min_length=1, max_length=50_000)


class ProjectContext(BaseModel):
    """Project fields the internal generator needs for a small artifact."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    id: UUID
    name: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=20_000)
    type: str = Field(min_length=1, max_length=100)


class GenerationOptions(BaseModel):
    model_config = ConfigDict(extra="forbid")

    temperature: float = Field(default=0.0, ge=0.0, le=2.0)
    max_output_tokens: int = Field(default=1_200, ge=64, le=16_000)


class GenerationRequest(BaseModel):
    """An internal, immutable work item sent by the Spring job worker."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    job_id: UUID
    project: ProjectContext
    artifact_type: str = Field(min_length=1, max_length=100)
    prompt: PromptReference
    # The Spring worker owns durable idempotency. Keeping this optional makes
    # the isolated service compatible with the narrow Phase 2 wire contract.
    idempotency_key: str | None = Field(default=None, min_length=16, max_length=255)
    options: GenerationOptions = Field(default_factory=GenerationOptions)

    @field_validator("idempotency_key")
    @classmethod
    def reject_control_characters(cls, value: str | None) -> str | None:
        if value is None:
            return value
        if any(ord(char) < 32 for char in value):
            raise ValueError("idempotency_key cannot contain control characters")
        return value


class StructuredSection(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    id: str = Field(pattern=r"^[a-z0-9][a-z0-9-]{0,63}$")
    heading: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1, max_length=20_000)


class StructuredArtifact(BaseModel):
    """The minimum schema accepted from a provider before persistence."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    schema_version: Literal["1.0"]
    title: str = Field(min_length=1, max_length=300)
    artifact_type: str = Field(min_length=1, max_length=100)
    version: str = Field(min_length=1, max_length=64)
    sections: list[StructuredSection] = Field(min_length=1, max_length=50)


class UsageMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid")

    input_tokens: int = Field(ge=0)
    output_tokens: int = Field(ge=0)
    cost_cents: int = Field(ge=0)


class ValidationOutcome(BaseModel):
    model_config = ConfigDict(extra="forbid")

    valid: bool
    issues: list[str]


class SafetyOutcome(BaseModel):
    model_config = ConfigDict(extra="forbid")

    passed: bool
    checks: list[str]


class ArtifactResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str
    content: str
    sections: list[StructuredSection]


class ErrorDetails(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    message: str
    retryable: bool


class GenerationResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    success: Literal[True]
    provider: str
    model: str
    prompt_version: str
    artifact: ArtifactResponse
    validation: ValidationOutcome
    usage: UsageMetadata
    latency_ms: int = Field(ge=0)
    error: None = None


class RetryAdvice(BaseModel):
    model_config = ConfigDict(extra="forbid")

    retryable: bool
    retry_after_seconds: float | None = Field(default=None, ge=0)


class ErrorResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    success: Literal[False] = False
    provider: None = None
    model: None = None
    prompt_version: None = None
    artifact: None = None
    validation: ValidationOutcome = Field(
        default_factory=lambda: ValidationOutcome(valid=False, issues=[])
    )
    usage: None = None
    latency_ms: int = 0
    error: ErrorDetails


class HealthResponse(BaseModel):
    status: Literal["ok"]


class ReadinessResponse(BaseModel):
    status: Literal["ready", "not_ready"]
    provider: str
    model: str
    internal_auth_configured: bool
    detail: str | None = None


# ---------------------------------------------------------------------------
# Discovery interview planner contracts
# ---------------------------------------------------------------------------


class DiscoveryProjectContext(BaseModel):
    """Minimal project context supplied by the trusted Spring interview service."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    id: UUID
    name: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=20_000)
    type: str = Field(min_length=1, max_length=100)
    industry: str | None = Field(default=None, max_length=200)
    target_audience: str | None = Field(default=None, max_length=1_000)
    tech_stack: str | None = Field(default=None, max_length=1_000)
    team_size: int | None = Field(default=None, ge=1, le=100_000)


class DiscoveryAnswerInput(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    question_key: str | None = Field(default=None, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    disposition: Literal["ANSWERED", "UNKNOWN", "SKIPPED"]
    question_text: str | None = Field(default=None, max_length=2_000)
    answer_text: str | None = Field(default=None, max_length=12_000)
    selected_option_keys: list[str] = Field(default_factory=list, max_length=12)


class DiscoveryOpenQuestionInput(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    question_text: str = Field(min_length=1, max_length=2_000)
    reason: str = Field(min_length=1, max_length=2_000)
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    material: bool = True


class DiscoveryEvidenceInput(BaseModel):
    """A bounded owner-approved excerpt. Its content is always untrusted data."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    source_id: UUID
    title: str = Field(min_length=1, max_length=255)
    excerpt: str = Field(min_length=1, max_length=4_000)


class DiscoveryChoiceOption(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(pattern=r"^[a-z0-9][a-z0-9-]{0,79}$")
    label: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=300)


class DiscoveryCandidateQuestion(BaseModel):
    """Server-owned question boundary the model may tailor but not expand."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    base_question: str = Field(min_length=1, max_length=2_000)
    why_we_ask: str = Field(min_length=1, max_length=2_000)
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    required: bool
    allows_multiple: bool
    options: list[DiscoveryChoiceOption] = Field(default_factory=list, max_length=8)


class DiscoveryPlanningRequest(BaseModel):
    """Typed internal request. The browser never calls this endpoint directly."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    project: DiscoveryProjectContext
    answers: list[DiscoveryAnswerInput] = Field(default_factory=list, max_length=30)
    visible_open_question_keys: list[str] = Field(default_factory=list, max_length=40)
    open_questions: list[DiscoveryOpenQuestionInput] = Field(default_factory=list, max_length=40)
    evidence: list[DiscoveryEvidenceInput] = Field(default_factory=list, max_length=8)
    candidate_questions: list[DiscoveryCandidateQuestion] = Field(default_factory=list, max_length=30)
    source_anchors: list[str] = Field(default_factory=list, max_length=100)


class DiscoveryQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    question_text: str = Field(min_length=1, max_length=2_000)
    why_we_ask: str = Field(min_length=1, max_length=2_000)
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    allows_multiple: bool = False
    options: list[DiscoveryChoiceOption] = Field(default_factory=list, max_length=8)


class DiscoveryCandidateScore(BaseModel):
    """Reviewable information-value score for a server-owned candidate."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    key: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    score: int
    reasons: list[str] = Field(default_factory=list, max_length=8)


class DiscoveryPlanningResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    planner: str
    model: str
    next_question: DiscoveryQuestion | None
    selection_reason: str | None = Field(default=None, max_length=2_000)
    missing_requirement: str | None = Field(default=None, max_length=2_000)
    source_context: list[str] = Field(default_factory=list, max_length=20)
    confirmed_context_used: list[str] = Field(default_factory=list, max_length=12)
    assumptions_to_validate: list[str] = Field(default_factory=list, max_length=12)
    candidate_scores: list[DiscoveryCandidateScore] = Field(default_factory=list, max_length=30)
    suggestions: list[str] = Field(default_factory=list, max_length=10)
    assumptions: list[str] = Field(default_factory=list, max_length=10)


# ---------------------------------------------------------------------------
# Governed retrieval and SRS contracts
# ---------------------------------------------------------------------------


class KnowledgeChunkInput(BaseModel):
    """Trusted metadata for a single owner-approved evidence excerpt."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    project_id: UUID
    owner_id: UUID
    source_id: UUID
    chunk_id: UUID
    source_title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=12_000)


class RetrievalIndexRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    chunk: KnowledgeChunkInput


class RetrievalDeleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    project_id: UUID
    owner_id: UUID
    chunk_id: UUID


class RetrievalSearchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    project_id: UUID
    owner_id: UUID
    query: str = Field(min_length=1, max_length=12_000)
    limit: int = Field(default=12, ge=1, le=24)


class RetrievalHit(BaseModel):
    model_config = ConfigDict(extra="forbid")
    source_id: UUID
    chunk_id: UUID
    source_title: str
    content: str
    score: float


class RetrievalSearchResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")
    hits: list[RetrievalHit]


class RetrievalIndexResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")
    indexed: bool


class SrsProfileInput(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    key: str = Field(pattern=r"^[A-Z][A-Z0-9_]{1,49}$")
    name: str = Field(min_length=1, max_length=120)
    controls: list[str] = Field(min_length=1, max_length=20)


class SrsCitation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    source_id: UUID
    chunk_id: UUID
    label: str = Field(min_length=1, max_length=255)


class SrsNarrativeSection(BaseModel):
    """One reviewable chapter in the compiled master specification."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^[A-Z][A-Z0-9_-]{1,49}$")
    title: str = Field(min_length=2, max_length=180)
    purpose: str = Field(min_length=8, max_length=600)
    content: str = Field(min_length=20, max_length=12_000)
    source_status: Literal["CONFIRMED", "DERIVED", "RECOMMENDED", "ASSUMED", "UNRESOLVED"]


class SrsWorkflow(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^WF-[0-9]{3,}$")
    title: str = Field(min_length=2, max_length=180)
    actors: list[str] = Field(default_factory=list, max_length=20)
    trigger: str = Field(min_length=2, max_length=1_000)
    preconditions: list[str] = Field(default_factory=list, max_length=20)
    main_flow: list[str] = Field(min_length=1, max_length=40)
    alternate_flows: list[str] = Field(default_factory=list, max_length=30)
    failure_recovery: list[str] = Field(default_factory=list, max_length=30)
    postconditions: list[str] = Field(default_factory=list, max_length=20)
    requirement_ids: list[str] = Field(default_factory=list, max_length=40)


class SrsQualityScenario(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^QS-[0-9]{3,}$")
    quality_attribute: str = Field(min_length=2, max_length=120)
    source: str = Field(min_length=2, max_length=500)
    stimulus: str = Field(min_length=2, max_length=1_000)
    environment: str = Field(min_length=2, max_length=1_000)
    artifact: str = Field(min_length=2, max_length=500)
    response: str = Field(min_length=2, max_length=1_500)
    response_measure: str = Field(min_length=2, max_length=1_000)
    status: Literal["CONFIRMED", "RECOMMENDED", "UNRESOLVED"]


class SrsRegisterItem(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^[A-Z][A-Z0-9_-]{1,49}$")
    category: str = Field(min_length=2, max_length=100)
    title: str = Field(min_length=2, max_length=180)
    description: str = Field(min_length=8, max_length=2_000)
    status: Literal["CONFIRMED", "DERIVED", "RECOMMENDED", "ASSUMED", "UNRESOLVED", "OUT_OF_SCOPE"]
    owner: str = Field(default="Project owner", min_length=2, max_length=120)
    source_detail: str = Field(min_length=5, max_length=1_000)


class SrsDiagram(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^DGM-[0-9]{3,}$")
    type: Literal["C4_CONTEXT", "CONTAINER", "WORKFLOW", "SEQUENCE", "STATE", "ERD", "DEPLOYMENT", "DATA_FLOW", "THREAT_BOUNDARY"]
    title: str = Field(min_length=2, max_length=180)
    notation: Literal["MERMAID", "PLANTUML"]
    source: str = Field(min_length=10, max_length=20_000)
    rationale: str = Field(min_length=8, max_length=1_000)
    status: Literal["CONFIRMED", "DERIVED", "RECOMMENDED", "UNRESOLVED"]


class SrsRequirement(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: str = Field(pattern=r"^SRS-(?:BR|FR|NFR|SEC|PRIV|DATA|API|UX|ACC|OPS|TEST)-[0-9]{3,}$")
    type: Literal[
        "BUSINESS", "FUNCTIONAL", "NON_FUNCTIONAL", "SECURITY", "PRIVACY",
        "DATA", "API", "UX", "ACCESSIBILITY", "OPERATIONS", "TEST",
    ]
    title: str = Field(default="Requirement", min_length=2, max_length=180)
    priority: Literal["MUST", "SHOULD", "COULD"]
    status: Literal["CONFIRMED", "RECOMMENDED", "ASSUMED", "UNRESOLVED"] = "CONFIRMED"
    statement: str = Field(min_length=20, max_length=4_000)
    rationale: str = Field(min_length=8, max_length=2_000)
    acceptance_criteria: list[str] = Field(min_length=1, max_length=10)
    actors: list[str] = Field(default_factory=list, max_length=20)
    preconditions: list[str] = Field(default_factory=list, max_length=20)
    trigger: str = Field(default="Confirmed workflow event", min_length=2, max_length=1_000)
    failure_behavior: str = Field(default="Failure behavior requires review.", min_length=8, max_length=2_000)
    data_involved: list[str] = Field(default_factory=list, max_length=30)
    dependencies: list[str] = Field(default_factory=list, max_length=30)
    risks: list[str] = Field(default_factory=list, max_length=30)
    source_kind: Literal["CITATION", "ASSUMPTION"]
    source_detail: str = Field(min_length=5, max_length=2_000)
    verification_method: Literal["TEST", "ANALYSIS", "INSPECTION", "DEMONSTRATION"]
    citations: list[SrsCitation] = Field(default_factory=list, max_length=8)


class SrsArtifact(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    schema_version: Literal["1.0", "2.0"]
    title: str = Field(min_length=1, max_length=300)
    document_control: dict[str, str] = Field(default_factory=dict)
    generation_manifest: dict[str, Any] = Field(default_factory=dict)
    executive_summary: str = Field(default="Pending compiled executive summary.", min_length=20, max_length=8_000)
    scope: str = Field(min_length=20, max_length=6_000)
    objectives: list[str] = Field(default_factory=list, max_length=30)
    stakeholders: list[str] = Field(default_factory=list, max_length=40)
    definitions: list[SrsRegisterItem] = Field(default_factory=list, max_length=80)
    source_registry: list[SrsRegisterItem] = Field(default_factory=list, max_length=40)
    exclusions: list[str] = Field(default_factory=list, max_length=20)
    assumptions: list[str] = Field(default_factory=list, max_length=30)
    open_questions: list[str] = Field(default_factory=list, max_length=30)
    narrative_sections: list[SrsNarrativeSection] = Field(default_factory=list, max_length=40)
    workflows: list[SrsWorkflow] = Field(default_factory=list, max_length=40)
    quality_scenarios: list[SrsQualityScenario] = Field(default_factory=list, max_length=60)
    risks: list[SrsRegisterItem] = Field(default_factory=list, max_length=80)
    decisions: list[SrsRegisterItem] = Field(default_factory=list, max_length=80)
    standards_applied: list[SrsRegisterItem] = Field(default_factory=list, max_length=80)
    diagrams: list[SrsDiagram] = Field(default_factory=list, max_length=30)
    requirements: list[SrsRequirement] = Field(min_length=1, max_length=240)


class SrsGenerationRequest(BaseModel):
    """Bounded, server-owned context only; browser uploads never become instructions."""

    model_config = ConfigDict(extra="forbid")
    project: ProjectContext
    confirmed_brief: dict
    profile: SrsProfileInput
    evidence: list[RetrievalHit] = Field(min_length=1, max_length=16)
    generation_mode: Literal["STANDARD", "EXHAUSTIVE"] = "EXHAUSTIVE"


class SrsValidation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    valid: bool
    issues: list[str] = Field(default_factory=list, max_length=100)
    citation_coverage: float = Field(ge=0, le=100)
    requirement_count: int = Field(default=0, ge=0)
    section_count: int = Field(default=0, ge=0)
    acceptance_coverage: float = Field(default=0, ge=0, le=100)
    traceability_coverage: float = Field(default=0, ge=0, le=100)
    quality_score: float = Field(default=0, ge=0, le=100)


class SrsGenerationResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")
    provider: str
    model: str
    prompt_version: str
    artifact: SrsArtifact
    validation: SrsValidation
