"""Opt-in live checks for the two production generation models.

Run with ``RUN_LIVE_GENERATION_TESTS=1`` and ``GEMINI_API_KEY`` configured.
The default test suite never makes paid or rate-limited network calls.
Set ``SKILLLINK_SRS_OUTPUT`` to persist the validated canonical SkillLink
artifact, raw brief, validation outcome, and exact model for export tests.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
from uuid import uuid4

import pytest

from app.config import Settings
from app.discovery import _QUESTION_CATALOG, plan_for_generated_question, plan_next_question
from app.models import (
    DiscoveryCandidateQuestion,
    DiscoveryPlanningRequest,
    DiscoveryProjectContext,
    ProjectContext,
    RetrievalHit,
    SrsGenerationRequest,
    SrsProfileInput,
)
from app.providers import GeminiProvider
from app.srs import generate_srs


pytestmark = pytest.mark.skipif(
    os.getenv("RUN_LIVE_GENERATION_TESTS") != "1" or not os.getenv("GEMINI_API_KEY"),
    reason="live generation checks are opt-in",
)


def _provider() -> GeminiProvider:
    return GeminiProvider(Settings(
        provider="gemini",
        model=os.getenv("AI_SERVICE_MODEL", "gemini-3.1-flash-lite-preview"),
        discovery_model="gemini-3.6-flash",
        fallback_model="",
        gemini_api_key=os.environ["GEMINI_API_KEY"],
        provider_timeout_seconds=600,
    ))


@pytest.mark.asyncio
async def test_live_gemini_flash_skilllink_question_and_options_cover_booking_risk() -> None:
    project = DiscoveryProjectContext(
        id=uuid4(),
        name="SkillLink",
        description=(
            "A local service-booking platform where customers discover, compare, and book trusted professionals. "
            "The first release includes service categories, location filters, professional profiles, ratings, "
            "availability, pricing display, booking confirmation, notifications, and visible booking status. "
            "The primary concern is preventing two requests from confirming the same professional and time slot; "
            "payments, subscriptions, advanced analytics, AI features, and complex admin tools are excluded."
        ),
        type="WEB_APP",
        industry="Local services",
        target_audience="customers and trusted local professionals",
    )
    catalog_question = next(question for question in _QUESTION_CATALOG if question.key == "risks")
    candidate = DiscoveryCandidateQuestion(
        key=catalog_question.key,
        category=catalog_question.category,
        base_question=catalog_question.question_text,
        why_we_ask=catalog_question.why_we_ask,
        risk_level=catalog_question.risk_level,
        required=True,
        allows_multiple=False,
        options=[],
    )
    payload = DiscoveryPlanningRequest(project=project, candidate_questions=[candidate])
    selected = plan_next_question(payload).next_question
    assert selected is not None

    raw = await _provider().plan_discovery_question(
        project=project.model_dump(mode="json"),
        answers=[],
        open_questions=[],
        evidence=[],
        candidate_questions=[candidate.model_dump(mode="json")],
        source_anchors=["project:description", "project:industry", "project:target-audience"],
        validation_request=payload,
        correlation_id="live-discovery-model-test",
    )
    try:
        result = plan_for_generated_question(
            payload,
            raw.output,
            planner="live-model-test",
            model=raw.model or "",
        )
    except ValueError as exc:
        pytest.fail(
            "live discovery output failed the application quality gate: "
            f"{exc}; raw={json.dumps(raw.output, ensure_ascii=False, sort_keys=True)}"
        )

    assert result.model == "gemini-3.6-flash"
    assert result.next_question is not None
    rendered = json.dumps(result.next_question.model_dump(mode="json"), ensure_ascii=False).casefold()
    assert sum(term in rendered for term in (
        "booking", "professional", "availability", "slot", "conflict", "double-book", "confirm",
    )) >= 3
    assert "cleaner" not in rendered
    assert len(result.next_question.options) >= 4
    assert any(option.key == "not-decided" for option in result.next_question.options)
    for option in result.next_question.options:
        assert option.description.strip()
        if option.key != "not-decided":
            assert len(f"{option.label} {option.description}".split()) >= 12


@pytest.mark.asyncio
async def test_live_configured_document_generation_is_project_specific_and_valid() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    payload = SrsGenerationRequest(
        project=ProjectContext(
            id=uuid4(),
            name="AeroTurn",
            description="An aircraft maintenance inspection hand-off service for line technicians and safety inspectors.",
            type="WEB_APP",
        ),
        confirmed_brief={
            "problem": "Paper inspection findings are delayed during aircraft turnaround.",
            "users": "Line technicians submit findings; safety inspectors accept or return them for correction.",
            "scope": "The first release covers finding submission, review, correction, acceptance, and an immutable status history.",
            "workflows": "A technician submits an aircraft finding. An inspector accepts it or returns it with a correction reason.",
            "entities": "Aircraft, inspection finding, review decision, correction, status event.",
            "quality_goals": "A finding must never be silently lost or shown as accepted before inspector approval.",
        },
        profile=SrsProfileInput(
            key="SAFETY_REVIEW",
            name="Safety review baseline",
            controls=["Trace every requirement to confirmed project evidence."],
        ),
        evidence=[RetrievalHit(
            source_id=source_id,
            chunk_id=chunk_id,
            source_title="Confirmed AeroTurn brief",
            content=(
                "The owner confirmed that line technicians submit aircraft inspection findings and safety inspectors "
                "either accept each finding or return it with a correction reason. Accepted status requires an inspector decision."
            ),
            score=1.0,
        )],
        generation_mode="STANDARD",
    )

    artifact, validation, model = await generate_srs(
        payload,
        _provider(),
        correlation_id="live-document-model-test",
    )

    assert model == os.getenv("AI_SERVICE_MODEL", "gemini-3.1-flash-lite-preview")
    assert validation.valid
    assert artifact.requirements
    rendered = artifact.model_dump_json().casefold()
    assert "aircraft" in rendered
    assert "inspection" in rendered


@pytest.mark.asyncio
async def test_live_configured_exhaustive_generation_preserves_complete_skilllink_context() -> None:
    source_id, chunk_id = uuid4(), uuid4()
    payload = SrsGenerationRequest(
        project=ProjectContext(
            id=uuid4(),
            name="SkillLink",
            description=(
                "A local service booking platform that helps customers quickly find, compare, and book trusted "
                "professionals, with the goal of making appointments faster and more reliable."
            ),
            type="WEB_APP",
        ),
        confirmed_brief={
            "problem": (
                "Customers currently compare local professionals across inconsistent messages and calendars, which "
                "makes appointment confirmation slow and creates a material risk of confirmed double-bookings."
            ),
            "scope": (
                "The first release covers the complete customer outcome from location-aware service discovery and "
                "professional comparison through an availability request, professional confirmation, and visible "
                "booking status, including supported cancellation, rescheduling, and no-show states."
            ),
            "inclusions": (
                "Service categories and location filters; professional profiles with qualifications, rating summaries, "
                "and trust information; professional-owned availability; displayed service pricing; booking requests "
                "and confirmations; rescheduling, cancellation, no-show handling, notifications, and booking-status history."
            ),
            "exclusions": (
                "Payment collection and payment-provider integrations are out of scope; subscriptions are out of scope; "
                "advanced analytics are out of scope; AI ranking or matching features are out of scope; complex "
                "administration tools are out of scope."
            ),
            "users": "Customer; Professional; Support Operator",
            "userCapabilities": (
                "Customers can search, compare, request, reschedule, and cancel their own bookings. Professionals own "
                "their profile, service prices, availability, duration, buffers, and confirmation decisions. Support "
                "operators may inspect booking and audit history but cannot confirm a booking or edit professional availability."
            ),
            "workflows": (
                "When a customer selects an available service slot, SkillLink places a temporary hold and creates one "
                "idempotent booking request. The professional confirms or declines it. Confirmation moves the request "
                "to CONFIRMED and notifies both actors; decline moves it to DECLINED and releases the slot. If the slot "
                "overlaps an existing buffered CONFIRMED booking at hold creation or confirmation, the request moves to "
                "CONFLICT and the later confirmation is blocked. If the 5-minute hold elapses before a professional decision, "
                "the request moves to EXPIRED and releases the slot. A retry returns the existing request, and notification "
                "delivery failure leaves the canonical status unchanged so the customer can recover without a duplicate booking."
            ),
            "entities": (
                "Customer Account; Professional Profile; Service; Qualification; Rating Summary; Contact Verification Status; "
                "Price Display; Availability Window; Booking; Slot Hold; Confirmation Decision; Notification Attempt; Audit Event."
            ),
            "entityRelationships": [
                {"from": "Customer Account", "to": "Booking", "cardinality": "ONE_TO_MANY", "label": "initiates"},
                {"from": "Professional Profile", "to": "Booking", "cardinality": "ONE_TO_MANY", "label": "receives"},
                {"from": "Professional Profile", "to": "Service", "cardinality": "ONE_TO_MANY", "label": "offers"},
                {"from": "Professional Profile", "to": "Qualification", "cardinality": "ONE_TO_MANY", "label": "records"},
                {"from": "Professional Profile", "to": "Availability Window", "cardinality": "ONE_TO_MANY", "label": "owns"},
                {"from": "Booking", "to": "Slot Hold", "cardinality": "ONE_TO_ZERO_OR_ONE", "label": "reserves"},
                {"from": "Booking", "to": "Confirmation Decision", "cardinality": "ONE_TO_ZERO_OR_ONE", "label": "receives"},
                {"from": "Booking", "to": "Notification Attempt", "cardinality": "ONE_TO_MANY", "label": "records"},
                {"from": "Booking", "to": "Audit Event", "cardinality": "ONE_TO_MANY", "label": "records"},
            ],
            "businessRules": (
                "Only a professional may confirm a request. A professional cannot have overlapping CONFIRMED bookings "
                "after service duration and buffers are applied. A Slot Hold expires after 5 minutes unless confirmed. "
                "The same idempotency key returns the original booking request for 24 hours. Customers may reschedule or "
                "cancel before the fixed 24-hour cutoff; professionals cannot override that cutoff in release one, and only "
                "the project owner may approve a future release change. A professional records NO_SHOW only after the appointment start time."
            ),
            "discoveryFilters": (
                "Customers select a service category and location, then filter by postal district, 10-kilometre radius, "
                "available date, displayed price range, and minimum rating before comparing profiles. A valid discovery "
                "search contains a supported service category, a non-empty location, and syntactically valid optional filter "
                "values; it remains valid when it returns zero professionals. Missing required values and malformed filters are invalid."
            ),
            "availability": (
                "Professionals own availability in their local IANA time zone. SkillLink stores instants in UTC, applies "
                "the service duration plus before-and-after buffers, permits one concurrent confirmed booking per "
                "professional, and exposes conflicts before confirmation."
            ),
            "pricing": (
                "Each profile displays a confirmed fixed amount or hourly rate, currency, unit, and what the service price "
                "includes. The booking preserves the displayed price snapshot, but SkillLink never authorizes, captures, "
                "refunds, or reconciles a payment."
            ),
            "integrations": "Transactional Email Gateway",
            "integrationBehavior": (
                "A transactional email notification gateway receives booking confirmation, decline, reschedule, and "
                "cancellation messages. The vendor remains replaceable; SkillLink records each delivery attempt and keeps "
                "the canonical booking status visible when the gateway is unavailable."
            ),
            "apiContracts": (
                "POST /api/bookings uses operation ID createBookingRequest for an idempotent customer request; "
                "GET /api/bookings/{bookingId} uses operation ID getBookingStatus for authorized status visibility; "
                "POST /api/bookings/{bookingId}/decision uses operation ID decideBookingRequest for the professional's "
                "confirm-or-decline decision. These are the only confirmed first-release HTTP operations."
            ),
            "risks": (
                "The primary failure event is two CONFIRMED bookings for overlapping buffered time, which would send a "
                "customer to an unavailable professional. Conflict monitoring detects the event, the operations owner "
                "alerts support, preserves the audit trail, blocks the later confirmation, and recovers by returning the "
                "later request to a visible conflict state. Notification loss is detected from failed delivery attempts "
                "and recovered by retry without changing booking state."
            ),
            "qualityTargets": (
                "At least 95% of valid discovery searches respond within 2 seconds at the 95th percentile; 99.9% of valid "
                "booking commands produce a visible terminal or pending status within 5 seconds during each monthly window. "
                "The complete customer booking workflow meets WCAG 2.2 Level AA and supports keyboard-only operation."
            ),
            "constraints": (
                "The fixed first-release platform is a responsive web application due by 2026-12-15 with a delivery budget "
                "cap of USD 120000. Service booking is the fixed scope boundary; schedule may move only with project-owner "
                "approval, while excluded payment, subscription, analytics, AI, and complex admin capabilities may not enter release one."
            ),
            "metrics": (
                "For request-to-confirmation reliability, the numerator is valid booking requests reaching CONFIRMED, "
                "DECLINED, CONFLICT, or EXPIRED within 5 seconds; the denominator is all valid booking requests; the target "
                "is 99.9%, reviewed in a monthly window. For double-booking prevention, the numerator is overlapping "
                "confirmed bookings and the denominator is all confirmed bookings; the target is 0%, reviewed weekly."
            ),
        },
        profile=SrsProfileInput(
            key="STARTER",
            name="Starter SRS controls",
            controls=["Trace every requirement to confirmed project evidence."],
        ),
        evidence=[RetrievalHit(
            source_id=source_id,
            chunk_id=chunk_id,
            source_title="Confirmed SkillLink project context",
            content=(
                "SkillLink customers choose a service category and location, filter by postal district, radius, date, "
                "price range, and rating, and compare professional qualifications, rating average, rating count, "
                "contact-verification status, "
                "services, price units, and availability. Professionals own their profile, prices, IANA time zone, UTC-backed "
                "availability, service duration, buffers, and confirmation decision. Customers request a slot; a 5-minute "
                "Slot Hold protects it; one idempotency key identifies the request for 24 hours; and only the professional "
                "moves the request to CONFIRMED or DECLINED. A buffered overlap moves the later request to CONFLICT and blocks "
                "confirmation; a 5-minute hold without a decision moves to EXPIRED. Overlapping buffered CONFIRMED bookings are forbidden. The "
                "workflow preserves REQUESTED, CONFIRMED, DECLINED, CONFLICT, EXPIRED, CANCELLED, RESCHEDULED, and NO_SHOW "
                "status history, supports cancellation and rescheduling before a fixed 24-hour cutoff that professionals cannot "
                "override, and records customer, "
                "professional, support, notification-attempt, rating, booking, confirmation-decision, slot-hold, and audit data. "
                "The confirmed HTTP contracts are POST /api/bookings createBookingRequest, GET /api/bookings/{bookingId} "
                "getBookingStatus, and POST /api/bookings/{bookingId}/decision decideBookingRequest. "
                "Transactional email failures are retried without changing canonical booking state. Authorized booking data "
                "is retained for 24 months. A valid search has a supported category, non-empty location, and syntactically valid "
                "optional filters, including zero-result searches. Search has a 2-second 95th-percentile target for at least 95% of valid searches; "
                "99.9% of valid booking commands must expose a status within 5 seconds monthly; the double-booking target is "
                "0% weekly; and the workflow targets WCAG 2.2 Level AA keyboard operation. Release one is a responsive web app "
                "due 2026-12-15 within USD 120000. Payments, subscriptions, advanced analytics, AI matching, and complex admin "
                "tools are explicitly excluded."
            ),
            score=1.0,
        )],
        generation_mode="EXHAUSTIVE",
    )

    artifact, validation, model = await generate_srs(
        payload,
        _provider(),
        correlation_id="live-skilllink-exhaustive-regression",
    )

    configured_model = os.getenv("AI_SERVICE_MODEL", "gemini-3.1-flash-lite-preview")
    assert model == configured_model
    assert validation.valid
    assert artifact.generation_manifest["mode"] == "EXHAUSTIVE"
    assert artifact.generation_manifest["requested_model"] == configured_model
    assert artifact.generation_manifest["actual_model"] == configured_model
    assert [item["id"] for item in artifact.generation_manifest["workstreams"]] == [
        "product", "data_interfaces", "trust", "quality_operations",
    ]
    assert all(item["validation_status"] == "PASSED" for item in artifact.generation_manifest["workstreams"])
    assert all(item["raw_requirement_count"] >= 5 for item in artifact.generation_manifest["workstreams"])
    assert all(item["accepted_requirement_count"] >= 1 for item in artifact.generation_manifest["workstreams"])
    assert all(
        item["requested_model"] == item["actual_model"] == configured_model
        for item in artifact.generation_manifest["workstreams"]
    )
    assert not any(
        item["material"]
        for item in artifact.generation_manifest["open_question_classification"]
    )
    assert len(artifact.requirements) >= 20
    assert len({item.id for item in artifact.requirements}) == len(artifact.requirements)
    assert len({re.sub(r"\W+", " ", item.statement).strip().casefold() for item in artifact.requirements}) == len(artifact.requirements)
    assert sum(item.type == "FUNCTIONAL" for item in artifact.requirements) >= 5
    requirement_types = {item.type for item in artifact.requirements}
    assert requirement_types & {"BUSINESS", "FUNCTIONAL", "UX"}
    assert requirement_types & {"DATA", "API"}
    assert requirement_types & {"SECURITY", "PRIVACY", "ACCESSIBILITY"}
    assert requirement_types & {"NON_FUNCTIONAL", "OPERATIONS", "TEST"}
    api_requirements = [item for item in artifact.requirements if item.type == "API"]
    assert api_requirements
    assert all(item.api_operation is not None for item in api_requirements)
    assert {item.api_operation.operation_id for item in api_requirements if item.api_operation} & {
        "createBookingRequest", "getBookingStatus", "decideBookingRequest",
    }
    assert all(len(re.findall(r"\bshall\b", item.statement, re.IGNORECASE)) == 1 for item in artifact.requirements)
    assert all(item.status != "UNRESOLVED" for item in artifact.requirements)
    assert all(not re.search(r"(?i)\b([a-z][a-z'-]{1,})\s+\1\b", item.statement) for item in artifact.requirements)
    rendered = artifact.model_dump_json().casefold()
    assert "booking" in rendered
    assert "professional" in rendered
    assert sum(term in rendered for term in ("availability", "pricing", "ratings", "confirmation", "status")) >= 3
    assert any("payment" in item.casefold() for item in artifact.exclusions)
    assert any("subscription" in item.casefold() for item in artifact.exclusions)
    assert any("ai ranking" in item.casefold() for item in artifact.exclusions)
    applied_standard_ids = {item.id for item in artifact.standards_applied}
    assert not ({"STD-ISO_42001", "STD-NIST_AI_RMF", "STD-NIST_CSF", "STD-ISO_27001"} & applied_standard_ids)

    output_path = os.getenv("SKILLLINK_SRS_OUTPUT")
    if output_path:
        destination = Path(output_path).expanduser().resolve()
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(
                {
                    "artifact": artifact.model_dump(mode="json"),
                    "brief": payload.confirmed_brief,
                    "validation": validation.model_dump(mode="json"),
                    "model": model,
                },
                ensure_ascii=False,
                indent=2,
                default=str,
            ) + "\n",
            encoding="utf-8",
        )
