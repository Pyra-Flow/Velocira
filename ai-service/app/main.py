"""FastAPI application for isolated internal generation requests."""

from __future__ import annotations

import logging
import re
import time
from hmac import compare_digest
from typing import Annotated
from uuid import uuid4

from fastapi import FastAPI, Header, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings
from app.discovery import plan_for_generated_question, plan_next_question, unanswered_questions
from app.errors import AiServiceError, ErrorCode
from app.models import (
    ArtifactResponse,
    DiscoveryPlanningRequest,
    DiscoveryPlanningResponse,
    ErrorDetails,
    ErrorResponse,
    GenerationRequest,
    GenerationResponse,
    HealthResponse,
    ReadinessResponse,
    RetrievalDeleteRequest,
    RetrievalIndexRequest,
    RetrievalIndexResponse,
    RetrievalSearchRequest,
    RetrievalSearchResponse,
    SrsGenerationRequest,
    SrsGenerationResponse,
    StructuredArtifact,
)
from app.providers import GenerationProvider, create_provider
from app.retrieval import GovernedRetriever
from app.retry import RetryClassifier
from app.safety import ContentSafetyGate
from app.srs import generate_srs
from app.structured_output import StructuredOutputAdapter

logger = logging.getLogger("velocira.ai_service")
_CORRELATION_ID = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def _configure_logging(log_level: str) -> None:
    if not logging.getLogger().handlers:
        logging.basicConfig(
            level=log_level,
            format="%(asctime)s %(levelname)s %(name)s %(message)s",
        )


def _safe_correlation_id(value: str | None) -> str:
    if value and _CORRELATION_ID.fullmatch(value):
        return value
    return str(uuid4())


def create_app(
    *,
    settings: Settings | None = None,
    provider: GenerationProvider | None = None,
) -> FastAPI:
    settings = settings or Settings.from_environment()
    _configure_logging(settings.log_level)
    app = FastAPI(
        title="Velocira AI Service",
        version="0.1.0",
        description="Internal generation provider boundary. Do not expose directly to browsers.",
    )
    app.state.settings = settings
    app.state.provider: GenerationProvider | None = provider
    app.state.retriever = GovernedRetriever(settings)
    app.state.provider_error: AiServiceError | None = None
    if provider is None:
        try:
            app.state.provider = create_provider(settings)
        except AiServiceError as exc:
            # Keep liveness available so orchestration can distinguish a bad
            # configuration from a process outage.
            app.state.provider_error = exc

    @app.middleware("http")
    async def add_correlation_id(request: Request, call_next):  # type: ignore[no-untyped-def]
        correlation_id = _safe_correlation_id(request.headers.get("X-Correlation-Id"))
        request.state.correlation_id = correlation_id
        response = await call_next(request)
        response.headers["X-Correlation-Id"] = correlation_id
        return response

    @app.exception_handler(AiServiceError)
    async def handle_ai_error(request: Request, exc: AiServiceError) -> JSONResponse:
        correlation_id = getattr(request.state, "correlation_id", str(uuid4()))
        advice = RetryClassifier.classify(exc)
        logger.warning(
            "generation_request_rejected code=%s retryable=%s correlation_id=%s",
            exc.code.value,
            advice.retryable,
            correlation_id,
        )
        return JSONResponse(
            status_code=exc.status_code,
            content=ErrorResponse(
                error=ErrorDetails(
                    code=exc.code.value,
                    message=exc.message,
                    retryable=advice.retryable,
                )
            ).model_dump(mode="json"),
        )

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        # Do not echo request content or framework validation internals: this
        # service receives prompts and project descriptions that may be private.
        validation_issues = [
            {
                "location": ".".join(str(part) for part in issue.get("loc", ())),
                "type": issue.get("type", "unknown"),
            }
            for issue in exc.errors()
        ]
        logger.warning(
            "request_validation_failed path=%s issues=%s correlation_id=%s",
            request.url.path,
            validation_issues,
            getattr(request.state, "correlation_id", "missing"),
        )
        return JSONResponse(
            status_code=422,
            content=ErrorResponse(
                error=ErrorDetails(
                    code=ErrorCode.INVALID_REQUEST.value,
                    message="The generation request is invalid.",
                    retryable=False,
                )
            ).model_dump(mode="json"),
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_error(request: Request, exc: Exception) -> JSONResponse:
        correlation_id = getattr(request.state, "correlation_id", str(uuid4()))
        logger.exception("generation_request_failed correlation_id=%s", correlation_id)
        return JSONResponse(
            status_code=500,
            content=ErrorResponse(
                error=ErrorDetails(
                    code=ErrorCode.INTERNAL_ERROR.value,
                    message="The generation service encountered an unexpected error.",
                    retryable=False,
                ),
            ).model_dump(mode="json"),
        )

    @app.get("/health", response_model=HealthResponse, tags=["operational"])
    async def health() -> HealthResponse:
        return HealthResponse(status="ok")

    @app.get("/ready", response_model=ReadinessResponse, tags=["operational"])
    async def readiness(request: Request) -> ReadinessResponse:
        runtime_settings: Settings = request.app.state.settings
        provider_error: AiServiceError | None = request.app.state.provider_error
        if provider_error is not None:
            return ReadinessResponse(
                status="not_ready",
                provider=runtime_settings.provider,
                model=runtime_settings.model,
                internal_auth_configured=bool(runtime_settings.internal_service_token),
                detail="Configured provider is unavailable.",
            )
        if runtime_settings.production_like and not runtime_settings.internal_service_token:
            return ReadinessResponse(
                status="not_ready",
                provider=runtime_settings.provider,
                model=runtime_settings.model,
                internal_auth_configured=False,
                detail="Internal service authentication must be configured outside development.",
            )
        return ReadinessResponse(
            status="ready",
            provider=runtime_settings.provider,
            model=runtime_settings.model,
            internal_auth_configured=bool(runtime_settings.internal_service_token),
        )

    @app.post("/v1/generate", response_model=GenerationResponse, tags=["generation"])
    async def generate(
        payload: GenerationRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> GenerationResponse:
        runtime_settings: Settings = request.app.state.settings
        _require_ready_and_authorized(request, internal_service_token)
        _enforce_input_limit(payload, runtime_settings.max_input_bytes)
        ContentSafetyGate.inspect(payload)
        correlation_id: str = request.state.correlation_id
        started_at = time.perf_counter()
        provider_instance: GenerationProvider = request.app.state.provider
        result = await provider_instance.generate(payload, correlation_id=correlation_id)
        artifact, validator = StructuredOutputAdapter.validate(result.output)
        latency_ms = round((time.perf_counter() - started_at) * 1000)
        logger.info(
            "generation_completed project_id=%s job_id=%s provider=%s model=%s latency_ms=%s correlation_id=%s",
            payload.project.id,
            payload.job_id,
            provider_instance.name,
            provider_instance.model,
            latency_ms,
            correlation_id,
        )
        return GenerationResponse(
            success=True,
            provider=provider_instance.name,
            model=result.model or provider_instance.model,
            prompt_version=payload.prompt.version,
            artifact=ArtifactResponse(
                title=artifact.title,
                content=_render_artifact_content(artifact),
                sections=artifact.sections,
            ),
            validation=validator,
            usage=result.usage,
            latency_ms=latency_ms,
        )

    @app.post("/v1/discovery/plan", response_model=DiscoveryPlanningResponse, tags=["discovery"])
    async def plan_discovery(
        payload: DiscoveryPlanningRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> DiscoveryPlanningResponse:
        """Return one typed question; business rules remain owner-provided evidence."""
        _require_ready_and_authorized(request, internal_service_token)
        correlation_id: str = request.state.correlation_id
        result = plan_next_question(payload)
        provider_instance = request.app.state.provider
        planner = getattr(provider_instance, "plan_discovery_question", None)
        if callable(planner) and result.next_question is not None:
            try:
                selected_key = result.next_question.key
                candidates = [
                    question.model_dump(mode="json")
                    for question in payload.candidate_questions
                    if question.key == selected_key
                ]
                if not candidates:
                    candidates = [
                        {
                            "key": question.key,
                            "category": question.category,
                            "base_question": question.question_text,
                            "why_we_ask": question.why_we_ask,
                            "risk_level": question.risk_level,
                            "required": True,
                            "allows_multiple": question.allows_multiple,
                            "options": [option.model_dump(mode="json") for option in question.options],
                        }
                        for question in unanswered_questions(payload)
                        if question.key == selected_key
                    ]
                source_anchors = ["project:title", "project:description", "project:type"]
                if payload.project.industry:
                    source_anchors.append("project:industry")
                if payload.project.target_audience:
                    source_anchors.append("project:target-audience")
                if payload.project.tech_stack:
                    source_anchors.append("project:tech-stack")
                if payload.project.team_size:
                    source_anchors.append("project:team-size")
                source_anchors.extend(
                    f"answer:{answer.question_key or answer.category.lower()}" for answer in payload.answers
                )
                source_anchors.extend(f"open-question:{item.key}" for item in payload.open_questions)
                source_anchors.extend(f"evidence:{item.source_id}" for item in payload.evidence)
                planned = await planner(
                    project=payload.project.model_dump(mode="json"),
                    answers=[answer.model_dump(mode="json") for answer in payload.answers],
                    open_questions=[item.model_dump(mode="json") for item in payload.open_questions],
                    evidence=[item.model_dump(mode="json") for item in payload.evidence],
                    candidate_questions=candidates,
                    source_anchors=list(dict.fromkeys(source_anchors)),
                    correlation_id=correlation_id,
                )
                result = plan_for_generated_question(
                    payload,
                    planned.output,
                    planner="gemini-context-planner-v3",
                    model=planned.model or provider_instance.model,
                )
            except (AiServiceError, ValueError) as exc:
                # Free-tier quota or provider outages never stop discovery: the
                # deterministic catalog remains an explicit safe fallback.
                logger.warning(
                    "discovery_planner_fallback reason=%s correlation_id=%s",
                    exc.code.value if isinstance(exc, AiServiceError) else "invalid_contextual_question",
                    correlation_id,
                )
        logger.info(
            "discovery_question_planned project_id=%s planner=%s correlation_id=%s",
            payload.project.id,
            result.planner,
            correlation_id,
        )
        return result

    @app.post("/v1/retrieval/index", response_model=RetrievalIndexResponse, tags=["retrieval"])
    async def index_evidence(
        payload: RetrievalIndexRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> RetrievalIndexResponse:
        _require_ready_and_authorized(request, internal_service_token)
        await request.app.state.retriever.index(payload.chunk)
        logger.info("evidence_indexed project_id=%s chunk_id=%s correlation_id=%s",
                    payload.chunk.project_id, payload.chunk.chunk_id, request.state.correlation_id)
        return RetrievalIndexResponse(indexed=True)

    @app.post("/v1/retrieval/delete", status_code=204, response_class=Response, tags=["retrieval"])
    async def delete_evidence(
        payload: RetrievalDeleteRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> Response:
        _require_ready_and_authorized(request, internal_service_token)
        await request.app.state.retriever.delete(chunk_id=str(payload.chunk_id))
        logger.info("evidence_deleted project_id=%s chunk_id=%s correlation_id=%s",
                    payload.project_id, payload.chunk_id, request.state.correlation_id)
        return Response(status_code=204)

    @app.post("/v1/retrieval/search", response_model=RetrievalSearchResponse, tags=["retrieval"])
    async def search_evidence(
        payload: RetrievalSearchRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> RetrievalSearchResponse:
        _require_ready_and_authorized(request, internal_service_token)
        hits = await request.app.state.retriever.search(payload)
        return RetrievalSearchResponse(hits=hits)

    @app.post("/v1/srs/generate", response_model=SrsGenerationResponse, tags=["srs"])
    async def generate_srs_endpoint(
        payload: SrsGenerationRequest,
        request: Request,
        internal_service_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
    ) -> SrsGenerationResponse:
        _require_ready_and_authorized(request, internal_service_token)
        artifact, validation, model = await generate_srs(
            payload, request.app.state.provider, correlation_id=request.state.correlation_id
        )
        logger.info("srs_generated project_id=%s requirements=%s correlation_id=%s",
                    payload.project.id, len(artifact.requirements), request.state.correlation_id)
        return SrsGenerationResponse(
            provider=request.app.state.provider.name,
            model=model,
            prompt_version="srs-compiler-v2",
            artifact=artifact,
            validation=validation,
        )

    return app


def _require_ready_and_authorized(request: Request, token: str | None) -> None:
    settings: Settings = request.app.state.settings
    provider_error: AiServiceError | None = request.app.state.provider_error
    if provider_error is not None or request.app.state.provider is None:
        raise AiServiceError(
            ErrorCode.SERVICE_NOT_READY,
            "The generation service is not ready.",
            status_code=503,
            retryable=True,
        )
    if settings.production_like and not settings.internal_service_token:
        raise AiServiceError(
            ErrorCode.SERVICE_NOT_READY,
            "The generation service is not securely configured.",
            status_code=503,
        )
    if settings.internal_service_token and not (
        token and compare_digest(token, settings.internal_service_token)
    ):
        raise AiServiceError(
            ErrorCode.UNAUTHORIZED_CALLER,
            "The internal caller could not be authenticated.",
            status_code=401,
        )


def _enforce_input_limit(payload: GenerationRequest, max_input_bytes: int) -> None:
    import json

    encoded = json.dumps(
        {
            "project": payload.project.model_dump(mode="json"),
            "prompt": payload.prompt.content,
        },
        ensure_ascii=False,
        default=str,
    ).encode("utf-8")
    if len(encoded) > max_input_bytes:
        raise AiServiceError(
            ErrorCode.INVALID_REQUEST,
            "The generation input exceeds the configured size limit.",
            status_code=413,
        )


def _render_artifact_content(artifact: StructuredArtifact) -> str:
    return "\n\n".join(
        f"## {section.heading}\n\n{section.content}" for section in artifact.sections
    )


app = create_app()
