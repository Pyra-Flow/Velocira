"""Grounded, information-value discovery planning.

The deterministic author is an explicit local/test provider. A configured live
planner is validated here but is never replaced with deterministic prose after
provider or schema failure.
"""

from __future__ import annotations

from dataclasses import dataclass
import re

from app.models import (
    DiscoveryCandidateQuestion,
    DiscoveryCandidateScore,
    DiscoveryChoiceOption,
    DiscoveryPlanningRequest,
    DiscoveryPlanningResponse,
    DiscoveryQuestion,
)


_QUESTION_CATALOG: tuple[DiscoveryQuestion, ...] = (
    DiscoveryQuestion(key="problem", category="PROBLEM", question_text="What problem must improve first?", why_we_ask="The first decision is the outcome the product must change, not a feature list.", risk_level="HIGH"),
    DiscoveryQuestion(key="users", category="USERS", question_text="Which roles take part in the core process?", why_we_ask="Actors, authority, and goals drive permissions, workflows, and UX.", risk_level="HIGH"),
    DiscoveryQuestion(key="stakeholders", category="STAKEHOLDERS", question_text="Who owns the outcome and approves consequential decisions?", why_we_ask="Decision ownership prevents approval and accountability gaps.", risk_level="MEDIUM"),
    DiscoveryQuestion(key="scope", category="SCOPE", question_text="What must the first release prove?", why_we_ask="A testable release boundary keeps requirements and estimates credible.", risk_level="HIGH"),
    DiscoveryQuestion(key="exclusions", category="EXCLUSIONS", question_text="Which tempting capabilities must wait?", why_we_ask="Explicit exclusions protect the release from silent scope growth.", risk_level="HIGH"),
    DiscoveryQuestion(key="workflows", category="WORKFLOWS", question_text="How should the core journey work, including failure recovery?", why_we_ask="State changes, hand-offs, and recovery paths anchor requirements and tests.", risk_level="HIGH"),
    DiscoveryQuestion(key="entities", category="ENTITIES", question_text="Which records require ownership and access rules?", why_we_ask="Data lifecycle and access decisions drive the database, APIs, and privacy controls.", risk_level="HIGH"),
    DiscoveryQuestion(key="integrations", category="INTEGRATIONS", question_text="Which external dependency changes the core journey?", why_we_ask="External data exchange introduces authentication, reliability, and operational boundaries.", risk_level="HIGH"),
    DiscoveryQuestion(key="quality", category="QUALITY_GOALS", question_text="Which measurable quality failure would be least acceptable?", why_we_ask="Prioritized targets turn vague quality expectations into verifiable requirements.", risk_level="HIGH"),
    DiscoveryQuestion(key="constraints", category="CONSTRAINTS", question_text="Which delivery boundary is fixed when trade-offs arise?", why_we_ask="Fixed boundaries determine feasible architecture, scope, and sequencing.", risk_level="HIGH"),
    DiscoveryQuestion(key="risks", category="RISKS", question_text="Which credible failure needs prevention or human recovery?", why_we_ask="A concrete failure and response produce useful controls and test cases.", risk_level="HIGH"),
    DiscoveryQuestion(key="business-rules", category="BUSINESS_RULES", question_text="Which decision must the product enforce consistently?", why_we_ask="Approval, eligibility, calculation, and access rules must come from an accountable owner.", risk_level="HIGH"),
    DiscoveryQuestion(key="metrics", category="METRICS", question_text="Which observable result will prove the release worked?", why_we_ask="A decision-linked measure makes success reviewable after launch.", risk_level="MEDIUM"),
)


_GENERIC_PATTERNS = tuple(re.compile(pattern, re.IGNORECASE) for pattern in (
    r"^who (?:are|will be|will use)",
    r"^(?:what|which) (?:is )?(?:the )?(?:primary|main|core)(?: operational)? (?:problem|bottleneck|challenge|pain point)",
    r"^what problem (?:does|should|must)",
    r"^what (?:are )?the (?:quality goals|constraints|risks|integrations)",
    r"^what quality goals matter",
    r"^what constraints must we respect",
    r"^what (?:is|are) (?:explicitly )?out of scope",
    r"^walk me through the most important user journey",
    r"^how will you know the project is successful",
))
_SHALLOW_OPTION_LABELS = {
    "yes", "no", "maybe", "users", "customers", "admins", "administrators",
    "customer booking first", "access only while needed", "detect quickly and recover",
    "platform or technology is fixed", "operational owner has final authority",
}
_STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "do", "does", "for", "from", "how", "in",
    "is", "it", "of", "on", "or", "should", "that", "the", "this", "to", "what", "when", "which",
    "who", "will", "with", "your",
}
_GENERIC_PROJECT_TERMS = {
    "app", "application", "build", "create", "described", "digital", "first", "platform", "product",
    "project", "release", "service", "software", "solution", "system", "tool", "user", "users", "web",
}

_DECISION_FOCUS_BY_KEY = {
    "manual-work": "reducing manual work",
    "availability-conflicts": "preventing confirmed booking conflicts",
    "slow-confirmation": "shortening booking confirmation time",
    "missed-follow-up": "preventing missed follow-up",
    "handoff-delay": "reducing care hand-off delays",
    "missing-context": "preventing missing care context",
    "unclear-ownership": "making ownership unambiguous",
    "failed-collection": "reducing failed collections",
    "approval-delay": "shortening approval time",
    "reconciliation-work": "reducing reconciliation work",
    "delay": "reducing avoidable delay",
    "errors": "preventing costly errors",
    "visibility": "making status and ownership visible",
    "closed-booking": "one complete booking journey",
    "request-decision": "one complete request-to-decision journey",
    "closed-handoff": "one closed-loop care hand-off",
    "payment-lifecycle": "one complete payment lifecycle",
    "reviewed-task": "one reviewed AI-assisted task",
    "one-way-sync": "one authoritative one-way synchronization",
    "complete-thin-slice": "one complete end-to-end journey",
    "operations-first": "an operations-first release",
    "customer-first": "a customer-booking-first release",
    "controls-first": "workspace controls before workflow breadth",
    "requester-first": "the requester experience before advanced administration",
    "clinical-team-first": "the clinical-team workflow before patient-facing features",
    "coordination-first": "coordination visibility before complex clinical editing",
    "no-double-booking": "preventing confirmed double-bookings",
    "protect-location": "protecting addresses and access details",
    "reliable-notices": "dependable status notifications",
    "fast-mobile": "responsive mobile booking",
    "acknowledgment-target": "a bounded hand-off acknowledgment time",
    "access-integrity": "preventing incorrect patient-record access",
    "traceable-changes": "making every care-record correction traceable",
    "security-first": "preventing unauthorized access",
    "integrity-first": "preventing lost or inconsistent work",
    "reliability-first": "keeping the core journey available",
    "speed-first": "keeping the core action responsive",
    "failure-rate": "the failure or rework rate as the primary success measure",
    "completion-time": "time to successful completion as the primary success measure",
    "successful-adoption": "successful repeated use as the primary success measure",
    "support-burden": "support and exception volume as the primary success measure",
}

@dataclass(frozen=True, slots=True)
class _Evaluation:
    question: DiscoveryQuestion
    score: int
    reasons: tuple[str, ...]


def unanswered_questions(payload: DiscoveryPlanningRequest) -> list[DiscoveryQuestion]:
    answered_keys = {answer.question_key for answer in payload.answers if answer.question_key}
    follow_up_keys = {
        item.key.removeprefix("incomplete-").split("-facet-", 1)[0]
        for item in payload.open_questions
        if item.material and item.key.startswith("incomplete-")
    }
    follow_up_categories = {
        item.category for item in payload.open_questions
        if item.material and item.key.startswith("contradiction-")
    }
    follow_up_categories.update(answer.category for answer in payload.answers
                                if answer.disposition == "ANSWERED"
                                and (_is_low_information(answer.answer_text)
                                     or "not-decided" in answer.selected_option_keys))
    if payload.candidate_questions:
        return [
            _candidate_question(candidate)
            for candidate in payload.candidate_questions
            if candidate.key not in answered_keys
            or candidate.key in follow_up_keys
            or candidate.category in follow_up_categories
        ]
    return [
        question for question in _QUESTION_CATALOG
        if question.key not in answered_keys
        or question.key in follow_up_keys
        or question.category in follow_up_categories
    ]


def plan_next_question(payload: DiscoveryPlanningRequest) -> DiscoveryPlanningResponse:
    """Choose and author one high-information question without manufacturing facts."""

    evaluations = _rank_candidates(payload)
    if not evaluations:
        return DiscoveryPlanningResponse(
            planner="deterministic-discovery-strategist-v3",
            model="deterministic",
            suggestions=["All required discovery areas are addressed; review open gaps before generation."],
        )
    selected = evaluations[0]
    question = _tailor_question(payload, selected.question)
    options = _decision_options(payload, question)
    question = question.model_copy(update={"options": options})
    _validate_quality(payload, question, options)
    sources = _source_anchors(payload, question)
    return DiscoveryPlanningResponse(
        planner="deterministic-discovery-strategist-v3",
        model="deterministic",
        next_question=question,
        selection_reason=_selection_reason(question, selected),
        missing_requirement=_missing_requirement(question.category),
        source_context=sources,
        confirmed_context_used=_confirmed_context(payload, sources),
        assumptions_to_validate=_assumptions_to_validate(payload, question),
        candidate_scores=_score_responses(evaluations),
        suggestions=[],
        assumptions=[],
    )


def plan_for_selected_key(
    payload: DiscoveryPlanningRequest,
    key: str,
    *,
    planner: str,
    model: str = "deterministic",
) -> DiscoveryPlanningResponse:
    """Accept only a selection from the server-owned catalog and tailor it safely."""

    question = next((item for item in unanswered_questions(payload) if item.key == key), None)
    if question is None:
        return plan_next_question(payload)
    evaluations = _rank_candidates(payload)
    selected = next(item for item in evaluations if item.question.key == key)
    question = _tailor_question(payload, question)
    options = _decision_options(payload, question)
    question = question.model_copy(update={"options": options})
    _validate_quality(payload, question, options)
    sources = _source_anchors(payload, question)
    return DiscoveryPlanningResponse(
        planner=planner,
        model=model,
        next_question=question,
        selection_reason=_selection_reason(question, selected),
        missing_requirement=_missing_requirement(question.category),
        source_context=sources,
        confirmed_context_used=_confirmed_context(payload, sources),
        assumptions_to_validate=_assumptions_to_validate(payload, question),
        candidate_scores=_score_responses(evaluations),
        suggestions=[],
        assumptions=[],
    )


def plan_for_generated_question(
    payload: DiscoveryPlanningRequest,
    generated: object,
    *,
    planner: str,
    model: str,
) -> DiscoveryPlanningResponse:
    """Apply strict grounding, originality, and option-quality gates to model output."""

    if not isinstance(generated, dict):
        raise ValueError("Planner output must be an object")
    key = str(generated.get("key", "")).strip()
    candidate = next((item for item in unanswered_questions(payload) if item.key == key), None)
    if candidate is None:
        raise ValueError("Planner selected an unavailable question")
    if str(generated.get("category", "")).strip() != candidate.category:
        raise ValueError("Planner changed the server-owned category")

    evaluations = _rank_candidates(payload)
    if not evaluations or evaluations[0].question.key != key:
        raise ValueError("Planner did not select the highest-information-value candidate")

    question_text = str(generated.get("question_text", "")).strip()
    why_we_ask = str(generated.get("why_we_ask", "")).strip()
    raw_options = generated.get("options", [])
    options, rejected_options = _generated_options_with_rejections(payload, raw_options)

    question = DiscoveryQuestion(
        key=candidate.key,
        category=candidate.category,
        question_text=question_text,
        why_we_ask=why_we_ask,
        risk_level=candidate.risk_level,
        allows_multiple=candidate.allows_multiple,
        options=options,
    )
    try:
        _validate_quality(payload, question, options)
    except ValueError as exc:
        if str(exc) == "Planner options do not provide useful decision support":
            reason = "; ".join(rejected_options[:6]) or "fewer than four choices were returned"
            raise ValueError(
                "Planner options did not provide useful decision support; "
                f"only {len(options)} passed semantic validation: {reason}"
            ) from exc
        raise

    allowed_anchors = set(_all_source_anchors(payload))
    requested_anchors = generated.get("source_context", [])
    source_context = [
        str(anchor) for anchor in requested_anchors
        if isinstance(anchor, str) and anchor in allowed_anchors
    ][:12]
    if not source_context:
        source_context = _source_anchors(payload, candidate)

    selected = next(item for item in evaluations if item.question.key == key)
    selection_reason = _safe_generated_text(
        generated.get("selection_reason"), _selection_reason(candidate, selected), 600
    )
    missing_requirement = _safe_generated_text(
        generated.get("missing_requirement"), _missing_requirement(candidate.category), 600
    )
    assumptions = _safe_list(generated.get("assumptions_to_validate"), 12)
    return DiscoveryPlanningResponse(
        planner=planner,
        model=model,
        next_question=question,
        selection_reason=selection_reason,
        missing_requirement=missing_requirement,
        source_context=source_context,
        # These descriptions are derived from validated anchors rather than
        # trusting the model to restate evidence as fact.
        confirmed_context_used=_confirmed_context(payload, source_context),
        assumptions_to_validate=assumptions or _assumptions_to_validate(payload, candidate),
        candidate_scores=_score_responses(evaluations),
        suggestions=[],
        assumptions=[],
    )


def _candidate_question(candidate: DiscoveryCandidateQuestion) -> DiscoveryQuestion:
    return DiscoveryQuestion(
        key=candidate.key,
        category=candidate.category,
        question_text=candidate.base_question,
        why_we_ask=candidate.why_we_ask,
        risk_level=candidate.risk_level,
        allows_multiple=candidate.allows_multiple,
        options=candidate.options,
    )


def _rank_candidates(payload: DiscoveryPlanningRequest) -> list[_Evaluation]:
    candidates = unanswered_questions(payload)
    evaluations = [_evaluate(payload, item, index) for index, item in enumerate(candidates)]
    return sorted(evaluations, key=lambda item: (-item.score, item.question.key))


def _evaluate(payload: DiscoveryPlanningRequest, question: DiscoveryQuestion, index: int) -> _Evaluation:
    score = 900 - index
    reasons: list[str] = []
    required = next((item.required for item in payload.candidate_questions if item.key == question.key), False)
    if required:
        score += 1_800
        reasons.append("required for the current complexity profile")
    if question.category == "PROBLEM" and not payload.answers:
        score += 5_000
        reasons.append("anchors every downstream decision before solution detail")
    if question.risk_level == "HIGH":
        score += 240
        reasons.append("high-impact documentation gap")
    if any(item.material and item.category == question.category for item in payload.open_questions):
        score += 1_100
        reasons.append("matches a visible unresolved gap")
    if any(answer.category == question.category and answer.disposition == "ANSWERED"
           and (_is_low_information(answer.answer_text) or "not-decided" in answer.selected_option_keys)
           for answer in payload.answers):
        score += 3_000
        reasons.append("reopens an explicitly undecided or incomplete answer")

    context = _context_text(payload)
    signals = {
        "USERS": ("role", "owner", "staff", "customer", "patient", "approver", "admin"),
        "STAKEHOLDERS": ("approval", "owner", "enterprise", "manager", "regulated"),
        "WORKFLOWS": ("booking", "request", "handoff", "payment", "approval", "sync"),
        "ENTITIES": ("data", "record", "address", "patient", "transaction", "document", "analytics"),
        "INTEGRATIONS": ("api", "webhook", "payment", "sms", "email", "crm", "erp", "device", "sync"),
        "QUALITY_GOALS": ("real-time", "sensitive", "private", "critical", "mobile", "availability"),
        "RISKS": ("health", "healthcare", "patient", "finance", "payment", "regulated", "ai", "security", "privacy", "human approval"),
        "BUSINESS_RULES": ("approval", "eligibility", "payment", "subscription", "compliance", "permission",
                           "cutoff", "threshold", "override", "cancellation"),
        "METRICS": ("reduce", "improve", "faster", "success", "conversion", "error"),
    }
    matched = [term for term in signals.get(question.category, ()) if _contains_signal(context, term)]
    if matched:
        score += min(900, 190 * len(matched))
        reasons.append("project context signals " + ", ".join(matched[:3]))

    # Once actors and scope are known, the concrete workflow normally unlocks
    # more useful detail than another broad inventory question.
    answered = {answer.category for answer in payload.answers if answer.disposition == "ANSWERED"}
    if question.category == "USERS" and "PROBLEM" in answered:
        score += 700
        reasons.append("the outcome is known, so authority and responsibility can now be defined")
    if question.category == "SCOPE" and {"PROBLEM", "USERS"}.issubset(answered):
        score += 600
        reasons.append("the outcome and actors are known, so a complete first-release slice can be chosen")
    if question.category == "WORKFLOWS" and "USERS" not in answered:
        score -= 900
        reasons.append("the workflow is less answerable until role authority is known")
    if question.category == "WORKFLOWS" and "SCOPE" not in answered:
        score -= 400
        reasons.append("the workflow is more useful after the first-release boundary is chosen")
    if question.category == "WORKFLOWS" and {"PROBLEM", "USERS", "SCOPE"}.issubset(answered):
        score += 700
        reasons.append("actors and release boundary are known, so the end-to-end flow is now answerable")
    if question.category == "QUALITY_GOALS" and "WORKFLOWS" not in answered:
        score -= 500
        reasons.append("measurable targets are more useful after the main flow is known")
    deep_required = {
        item.category for item in payload.candidate_questions
        if item.required and item.category in {
            "STAKEHOLDERS", "EXCLUSIONS", "ENTITIES", "INTEGRATIONS", "RISKS", "BUSINESS_RULES"
        }
    }
    if question.category == "METRICS" and "QUALITY_GOALS" in answered and deep_required.issubset(answered):
        score += 700
        reasons.append("the outcome and quality guardrail are known, so success can now be measured safely")
    if not reasons:
        reasons.append("next unanswered server-approved discovery area")
    return _Evaluation(question=question, score=score, reasons=tuple(reasons))


def _tailor_question(payload: DiscoveryPlanningRequest, question: DiscoveryQuestion) -> DiscoveryQuestion:
    category = question.category
    profile = _profile(payload)
    team = payload.project.team_size
    follow_up = next((item for item in payload.open_questions if item.material
                      and item.category == category
                      and item.key.startswith(("incomplete-", "contradiction-"))
                      and (item.key.startswith("contradiction-")
                           or _source_question_key(item.key) == question.key)), None)
    incomplete_answer = any(answer.category == category and answer.disposition == "ANSWERED"
                            and (_is_low_information(answer.answer_text)
                                 or "not-decided" in answer.selected_option_keys)
                            for answer in payload.answers)
    follow_up_key = follow_up.key if follow_up else (f"incomplete-{question.key}" if incomplete_answer else None)
    if follow_up and _safe_follow_up_text(follow_up.question_text):
        prompt = follow_up.question_text.strip()
    elif follow_up_key:
        prompt = _follow_up_question(profile, category, follow_up_key)
    else:
        prompt = {
        "PROBLEM": _problem_question(profile),
        "USERS": _users_question(profile),
        "STAKEHOLDERS": _stakeholder_question(profile),
        "SCOPE": _scope_question(profile),
        "EXCLUSIONS": _exclusions_question(profile),
        "WORKFLOWS": _workflow_question(profile),
        "ENTITIES": _entities_question(profile),
        "INTEGRATIONS": _integrations_question(profile),
        "QUALITY_GOALS": _quality_question(profile),
        "CONSTRAINTS": (
            f"With a confirmed team of {team}, which one first-release boundary is fixed: launch date, budget, platform, or scope?"
            if team else
            "Which one first-release boundary is fixed: launch date, budget, platform, or scope?"
        ),
        "RISKS": _risk_question(profile),
        "BUSINESS_RULES": _rules_question(profile),
        "METRICS": _metrics_question(profile),
        }[category]
    prompt = _ground_in_project_context(payload, prompt)
    why = {
        "PROBLEM": "This separates the outcome worth funding from possible features and gives the SRS a measurable purpose.",
        "USERS": "Authority and responsibility define roles, permissions, notifications, and exception ownership.",
        "STAKEHOLDERS": "The answer establishes who resolves conflicts and accepts consequential behavior before launch.",
        "SCOPE": "A complete thin slice produces testable acceptance criteria and a credible delivery plan.",
        "EXCLUSIONS": "Naming the nearest tempting capability prevents it from silently entering design and estimates.",
        "WORKFLOWS": "This supplies state changes, hand-offs, timeout behavior, recovery paths, and UX feedback.",
        "ENTITIES": "The answer drives database ownership, permission checks, audit history, retention, and deletion behavior.",
        "INTEGRATIONS": "This clarifies exchanged data, authentication, retries, degraded operation, and support ownership.",
        "QUALITY_GOALS": "Choosing a failure and target converts broad quality language into a verifiable non-functional requirement.",
        "CONSTRAINTS": "Knowing what cannot move makes scope and architecture trade-offs explicit rather than accidental.",
        "RISKS": "A concrete harm scenario produces prevention, detection, recovery, and human-escalation requirements.",
        "BUSINESS_RULES": "The product cannot safely invent who may decide, override, calculate, or approve a consequential action.",
        "METRICS": "A baseline, target, and review window make post-launch success observable and actionable.",
    }[category]
    return question.model_copy(update={"question_text": prompt, "why_we_ask": why})


def _ground_in_project_context(payload: DiscoveryPlanningRequest, question_text: str) -> str:
    """Ensure deterministic fallback questions use actual project information."""
    project_terms = _distinctive_project_terms(payload)
    if project_terms and len(_semantic_terms(question_text) & project_terms) >= min(2, len(project_terms)):
        return question_text
    context = _project_context_phrase(payload)
    if not context:
        return question_text
    lowered = question_text[0].lower() + question_text[1:]
    return f"For {context}, {lowered}"


def _project_context_phrase(payload: DiscoveryPlanningRequest) -> str:
    audience = _safe_context_value(payload.project.target_audience, 8)
    industry = _safe_context_value(payload.project.industry, 4)
    if audience and industry:
        return f"{audience} in {industry}"
    if audience:
        return audience
    if industry:
        return f"work in {industry}"
    description = _safe_context_value(payload.project.description, 12)
    if description:
        return f"the described {description}"
    project_type = _safe_context_value(payload.project.type.replace("_", " "), 5)
    return f"the described {project_type}" if project_type else ""


def _safe_context_value(value: str | None, word_limit: int) -> str:
    if not value or _looks_like_prompt_leak(value):
        return ""
    normalized = re.sub(r"\s+", " ", value).strip()
    normalized = re.split(r"[.!?;\r\n]", normalized, maxsplit=1)[0].strip(" -,:\"")
    normalized = re.sub(r"^(?:a|an|the)\s+", "", normalized, flags=re.IGNORECASE)
    return " ".join(normalized.split()[:word_limit])


def _distinctive_project_terms(payload: DiscoveryPlanningRequest) -> set[str]:
    project_values = " ".join((
        payload.project.description or "",
        payload.project.industry or "",
        payload.project.target_audience or "",
        payload.project.tech_stack or "",
    ))
    return _semantic_terms(project_values) - _GENERIC_PROJECT_TERMS - _semantic_terms(payload.project.name)


def _follow_up_question(profile: str, category: str, gap_key: str) -> str:
    if gap_key.startswith("contradiction-"):
        if category == "CONSTRAINTS":
            return "Which boundary should govern the first release: the external integration or the deployment constraint?"
        return "Which one of the conflicting decisions should govern the first release?"
    return {
        "PROBLEM": "Which single delay, error, or harmful result must improve first?",
        "USERS": "Which named actor has authority for the unresolved action?",
        "SCOPE": "Which single journey must work end to end in the first release?",
        "WORKFLOWS": "What exact recovery state follows the unresolved workflow failure?",
        "ENTITIES": "Which named role or system owns the unresolved record decision?",
        "INTEGRATIONS": "Your earlier answer did not identify an authoritative dependency; which external system, if any, owns the status used by the core workflow?",
        "QUALITY_GOALS": "What measurable threshold governs the least acceptable launch failure?",
        "CONSTRAINTS": "What exact value makes the unresolved delivery boundary non-negotiable?",
        "RISKS": _risk_question(profile),
        "BUSINESS_RULES": _rules_question(profile),
        "METRICS": _metrics_question(profile),
        "STAKEHOLDERS": _stakeholder_question(profile),
        "EXCLUSIONS": _exclusions_question(profile),
    }[category]


def _source_question_key(finding_key: str) -> str:
    if not finding_key.startswith("incomplete-"):
        return finding_key
    return finding_key.removeprefix("incomplete-").split("-facet-", 1)[0]


def _safe_follow_up_text(value: str) -> bool:
    text = value.strip()
    return 20 <= len(text) <= 420 and text.endswith("?") and text.count("?") == 1 \
        and not _looks_like_prompt_leak(text)


def _profile(payload: DiscoveryPlanningRequest) -> str:
    project_context = " ".join((
        payload.project.name,
        payload.project.description or "",
        payload.project.type,
        payload.project.industry or "",
        payload.project.target_audience or "",
        payload.project.tech_stack or "",
    )).casefold()
    established = _detect_profile(project_context)
    return established if established != "general" else _detect_profile(_context_text(payload))


def _detect_profile(context: str) -> str:
    if any(_contains_signal(context, term) for term in ("clinic", "health", "patient", "medical", "hospital", "care team")):
        return "healthcare"
    if any(_contains_signal(context, term) for term in ("payment", "bank", "wallet", "invoice", "transaction", "fintech")):
        return "payment"
    if any(_contains_signal(context, term) for term in ("ai", "llm", "assistant", "agent", "model", "copilot")):
        return "ai"
    if any(_contains_signal(context, term) for term in ("booking", "appointment", "schedule", "professional", "reservation")):
        return "booking"
    if any(_contains_signal(context, term) for term in ("api", "webhook", "etl", "warehouse", "sync", "connector", "integration")):
        return "data-integration"
    if any(_contains_signal(context, term) for term in ("saas", "multi-tenant", "workspace", "subscription", "enterprise")):
        return "saas"
    return "general"


def _problem_question(profile: str) -> str:
    return {
        "booking": "Which booking failure creates the greatest cost or loss of trust today?",
        "healthcare": "Which care-coordination failure creates the greatest harm today?",
        "payment": "Which money-movement failure creates the greatest cost today?",
        "ai": "Which AI-assisted task failure creates the greatest user harm today?",
        "data-integration": "Which broken data hand-off creates the greatest rework today?",
        "saas": "Which team-workflow failure creates the greatest delay today?",
        "general": "Which current-process failure creates the greatest avoidable delay or error today?",
    }[profile]


def _users_question(profile: str) -> str:
    return {
        "booking": "Which named role has authority to confirm a booking request?",
        "healthcare": "Which named role accepts responsibility for a care hand-off?",
        "payment": "Which named role has authority to approve a payment?",
        "ai": "Which named role accepts an AI-assisted result?",
        "data-integration": "Which named role owns correction of a rejected record?",
        "saas": "Which named role has final approval authority in a workspace?",
        "general": "Which named role has final authority over the core result?",
    }[profile]


def _stakeholder_question(profile: str) -> str:
    subject = {"healthcare": "safety or privacy", "payment": "financial control", "ai": "AI safety", "data-integration": "data ownership"}.get(profile, "scope or operational")
    return f"Which named owner has final authority when a {subject} decision conflicts with delivery speed?"


def _scope_question(profile: str) -> str:
    journey = {"booking": "booking journey from request through completion", "healthcare": "care hand-off from creation through acknowledgment", "payment": "payment journey from initiation through final status", "ai": "AI-assisted task from input through human acceptance", "data-integration": "record journey from source through validated destination", "saas": "team workflow from submission through approval"}.get(profile, "core user outcome from start through completion")
    return f"Which single completed {journey} belongs in the first release?"


def _exclusions_question(profile: str) -> str:
    examples = {"booking": "online payment, route optimization, or native mobile apps", "healthcare": "diagnosis, treatment recommendations, or broad record-system replacement", "payment": "credit decisions, cross-border settlement, or automated dispute judgment", "ai": "autonomous action, model training on customer data, or unsupported content types", "data-integration": "historical migration, real-time sync, or bidirectional updates", "saas": "advanced analytics, custom workflows, or enterprise identity"}.get(profile, "advanced automation, historical migration, or non-essential integrations")
    return f"Which nearby capability must be explicitly excluded from the first release - for example {examples} - even if users request it?"


def _workflow_question(profile: str) -> str:
    return {
        "booking": "What exact event moves a requested booking into the confirmed state?",
        "healthcare": "What exact event moves a care hand-off into the accepted state?",
        "payment": "What exact event moves a payment into its final status?",
        "ai": "What exact event moves AI-assisted work into the accepted state?",
        "data-integration": "What exact event moves a source record into the accepted destination state?",
        "saas": "What exact event moves submitted work into the approved state?",
        "general": "What exact event moves the core work into its successful state?",
    }[profile]


def _entities_question(profile: str) -> str:
    return {
        "booking": "Which booking record is authoritative for confirmation status?",
        "healthcare": "Which care hand-off record is authoritative for acceptance status?",
        "payment": "Which payment record is authoritative for final status?",
        "ai": "Which AI-assisted work record is authoritative for acceptance status?",
        "data-integration": "Which shared record is authoritative for destination status?",
        "saas": "Which workspace record is authoritative for approval status?",
        "general": "Which named record is authoritative for the core workflow?",
    }[profile]


def _integrations_question(profile: str) -> str:
    return {
        "booking": "Which external service is essential to the first-release booking outcome?",
        "healthcare": "Which external clinical or identity service is essential to the care hand-off?",
        "payment": "Which external provider is authoritative for final payment status?",
        "ai": "Which model or retrieval service is essential to the AI-assisted task?",
        "data-integration": "Which source system is authoritative for the highest-value data exchange?",
        "saas": "Which external service is essential to the first team workflow?",
        "general": "Which external service is essential to the core outcome?",
    }[profile]


def _quality_question(profile: str) -> str:
    return {
        "booking": "Which booking-quality failure is least acceptable at launch?",
        "healthcare": "Which care-quality failure is least acceptable at launch?",
        "payment": "Which payment-quality failure is least acceptable at launch?",
        "ai": "Which AI-quality failure is least acceptable at launch?",
        "data-integration": "Which data-quality failure is least acceptable at launch?",
        "saas": "Which workspace-quality failure is least acceptable at launch?",
        "general": "Which core-workflow quality failure is least acceptable at launch?",
    }[profile]


def _risk_question(profile: str) -> str:
    return {
        "healthcare": "Which realistic care failure could create the greatest harm?",
        "payment": "Which realistic payment failure could create the greatest loss?",
        "ai": "Which realistic AI failure could create the greatest harm?",
        "data-integration": "Which silent data failure could create the worst downstream decision?",
        "booking": "Which realistic booking failure could create the greatest customer harm?",
        "saas": "Which realistic workspace failure could invalidate the release?",
        "general": "Which credible failure could most harm users or invalidate the release?",
    }[profile]


def _rules_question(profile: str) -> str:
    return {
        "booking": "Which booking decision must the product enforce with an explicit rule?",
        "healthcare": "Which care hand-off decision must the product enforce with an explicit rule?",
        "payment": "Which money-movement decision must the product enforce with an explicit rule?",
        "ai": "Which AI-assisted decision must the product enforce with an explicit rule?",
        "data-integration": "Which record-acceptance decision must the product enforce with an explicit rule?",
        "saas": "Which workspace decision must the product enforce with an explicit rule?",
        "general": "Which consequential decision must the product enforce with an explicit rule?",
    }[profile]


def _metrics_question(profile: str) -> str:
    measure = {"booking": "fewer booking conflicts or faster confirmed bookings", "healthcare": "faster acknowledged hand-offs without safety or privacy incidents", "payment": "more successful payments with less reconciliation effort", "ai": "more accepted outputs without increasing unsafe or incorrect results", "data-integration": "fresher accepted data with fewer manual corrections", "saas": "faster completed team work with fewer approval delays"}.get(profile, "faster successful completion with fewer errors")
    return f"Which event should the metric numerator count to measure {measure}?"


def _decision_options(payload: DiscoveryPlanningRequest, question: DiscoveryQuestion) -> list[DiscoveryChoiceOption]:
    profile = _profile(payload)
    category = question.category
    options_by_category: dict[str, list[tuple[str, str, str]]] = {
        "PROBLEM": {
            "booking": [("availability-conflicts", "Prevent availability conflicts", "Prioritizes accurate availability and conflict prevention before convenience features."), ("slow-confirmation", "Shorten confirmation time", "Prioritizes response ownership, deadlines, and automatic status updates."), ("missed-follow-up", "Prevent missed follow-up", "Prioritizes reliable reminders, delivery status, and recovery when messages fail.")],
            "healthcare": [("handoff-delay", "Reduce hand-off delays", "Prioritizes acknowledgment, escalation, and visibility of unaccepted work."), ("missing-context", "Prevent missing clinical context", "Prioritizes required information, validation, and correction ownership."), ("unclear-ownership", "Make ownership unambiguous", "Prioritizes role responsibility, status, and escalation rules.")],
            "payment": [("failed-collection", "Reduce failed collection", "Prioritizes provider status, retry behavior, and customer recovery."), ("approval-delay", "Shorten approval time", "Prioritizes authority, limits, expiry, and escalation."), ("reconciliation-work", "Reduce reconciliation work", "Prioritizes authoritative statuses, audit history, and exception queues.")],
        }.get(profile, [("delay", "Reduce avoidable delay", "Prioritizes hand-offs, status visibility, and response deadlines."), ("errors", "Prevent costly errors", "Prioritizes validation, ownership, and recoverable failure handling."), ("visibility", "Make work visible", "Prioritizes trustworthy status, responsibility, and exception reporting.")]),
        "USERS": [("operator-decides", "Operator owns routine decisions", "Keeps day-to-day work fast while reserving exceptions for an approver."), ("approver-controls", "Approver confirms consequential actions", "Adds control and auditability but requires response deadlines and escalation."), ("shared-responsibility", "Responsibility changes by state", "Supports realistic hand-offs but requires explicit permissions for every transition.")],
        "STAKEHOLDERS": [("product-owner", "Product owner decides scope while named specialists sign off defined risks", "Keeps scope authority explicit and prevents specialist acceptance from being implied."), ("operational-owner", "Named operational owner decides routine policy while a risk owner may block unsafe release", "Separates day-to-day accountability from the authority to stop a consequential release."), ("joint-approval", "Named business and risk owners must both approve consequential releases", "Adds protection for consequential releases but requires a response deadline and escalation path.")],
        "SCOPE": [("complete-thin-slice", "One complete end-to-end journey", "Delivers a usable outcome including errors and recovery before adding breadth."), ("operator-first", "Internal operation first", "Validates process and controls before exposing a customer-facing experience."), ("self-service-first", "User self-service first", "Prioritizes the external experience while keeping complex exceptions manual.")],
        "EXCLUSIONS": [("advanced-automation", "Advanced automation waits", "Keeps consequential decisions human-controlled in the first release."), ("historical-migration", "Historical migration waits", "Reduces data-cleaning risk by starting with new or essential records."), ("nonessential-integrations", "Non-essential integrations wait", "Protects the core journey from external dependency and support risk."), ("native-apps", "Native mobile applications wait", "Uses a responsive web experience before funding separate platform builds.")],
        "WORKFLOWS": [("manual-exception", "Send exceptions to a human queue", "Keeps edge cases visible and recoverable without pretending they are automated."), ("bounded-auto-retry", "Retry automatically, then escalate", "Handles temporary failures quickly while preventing silent infinite retries."), ("stop-and-explain", "Stop and explain the next action", "Avoids uncertain state changes and tells the responsible person how to recover.")],
        "ENTITIES": [("least-privilege", "Role- and state-based access ends when workflow responsibility ends", "Limits sensitive record visibility and requires an auditable removal event at the state transition."), ("owner-controlled", "Named record owner approves sharing and an administrator handles documented recovery", "Makes ownership explicit without granting silent administrative access."), ("policy-controlled", "Versioned organization policy controls access and every grant or denial is audited", "Provides consistency and traceability across users and teams."), ("immutable-history", "Corrections append to immutable history while the current record remains clearly identified", "Supports disputes and audits without silently overwriting prior values.")],
        "INTEGRATIONS": [("required-live", "Required for the live journey", "The core action waits or fails clearly when the external service is unavailable."), ("queued-degraded", "Queue work during an outage", "Users may continue, with visible pending status and controlled retry."), ("manual-fallback", "Provide a manual fallback", "Preserves essential work but requires later reconciliation."), ("defer-integration", "Defer it from the first release", "Keeps the initial product self-contained until the core workflow is proven.")],
        "QUALITY_GOALS": [("security-first", "Prevent unauthorized access", "Prioritizes permission tests, secure defaults, and auditable access failures."), ("integrity-first", "Prevent lost or inconsistent work", "Prioritizes validation, idempotency, backups, and visible recovery."), ("reliability-first", "Keep the core journey available", "Prioritizes monitoring, graceful degradation, and recovery targets."), ("speed-first", "Keep the core action responsive", "Prioritizes a measured response-time target on realistic devices and load.")],
        "CONSTRAINTS": [("date-fixed", "Keep the confirmed launch date fixed and reduce scope before weakening controls", "Requires the exact date to be confirmed before this becomes complete."), ("budget-fixed", "Keep the confirmed budget and team fixed and deliver a smaller complete journey", "Requires the exact budget and team boundary to be recorded."), ("platform-fixed", "Keep the confirmed platform fixed and move scope or schedule before replacing it", "Requires the exact platform or technology constraint to be named."), ("scope-fixed", "Keep the named required scope fixed and change staffing, schedule, or phasing instead", "Requires the non-negotiable scope items to be listed explicitly.")],
        "RISKS": [("prevent", "Block the named failure with validation and safe defaults before harm occurs", "Requires the failure event, impact, and accountable control owner to be recorded."), ("detect-recover", "Named operations owner detects alerts, contains impact, and runs tested recovery", "Requires an observable detection signal and a specific recovery result."), ("human-review", "Accountable reviewer decides every consequential or ambiguous case before commit", "Requires the routed event, response deadline, and escalation behavior to be explicit."), ("limit-impact", "Isolate affected users or records and restore them through a tested recovery path", "Requires the failure boundary, detection event, and recovery owner to be named.")],
        "BUSINESS_RULES": [("strict-rule", "Always enforce the rule", "Consistency and control take priority; exceptions require a separate governed process."), ("role-override", "Allow a named role to override", "Supports unusual cases but requires a reason and audit event."), ("threshold-review", "Review only above a threshold", "Keeps routine work fast while escalating consequential cases."), ("manual-first", "Keep the decision manual initially", "Avoids invented automation until enough real examples define a safe rule.")],
        "METRICS": [("completion-time", "Time to successful completion", "Measures whether the core job becomes meaningfully faster."), ("failure-rate", "Failure or rework rate", "Measures whether the release prevents the errors it was designed to reduce."), ("successful-adoption", "Successful repeated use", "Measures whether intended users complete the workflow and return."), ("support-burden", "Support and exception volume", "Measures operational complexity that user activity alone can hide.")],
    }
    raw = list(_profile_options(profile, category) or options_by_category[category])
    raw.append(("not-decided", "Not decided yet", "Keep this as an explicit open decision instead of turning a suggestion into a project fact."))
    options = [DiscoveryChoiceOption(key=key, label=label, description=description) for key, label, description in raw]
    return _contextualize_options(payload, options)


def _profile_options(profile: str, category: str) -> list[tuple[str, str, str]]:
    specific: dict[tuple[str, str], list[tuple[str, str, str]]] = {
        ("booking", "USERS"): [
            ("owner-controls", "Owner controls confirmation and overrides", "Keeps schedule authority with the business owner and requires a response deadline for pending requests."),
            ("customer-cutoff", "Customer controls changes before a cutoff", "Enables self-service while requiring an explicit cutoff and clear handling after that point."),
            ("professional-assignment", "Assigned professionals accept or decline work without changing customer terms", "Gives the assigned professional control over availability while price and customer terms remain owner-controlled."),
            ("state-based-authority", "Authority changes with booking status", "Fits real hand-offs but requires permissions for pending, confirmed, in-progress, and completed states."),
        ],
        ("saas", "USERS"): [
            ("requester-drafts", "Requester owns drafts and corrections", "Keeps submission efficient while preventing requesters from approving their own consequential work."),
            ("manager-threshold", "Manager approves within a limit", "Speeds routine decisions but requires a documented threshold and escalation above it."),
            ("specialist-approval", "Specialist approves exceptional work", "Adds control for high-risk cases but requires routing rules and response deadlines."),
            ("admin-no-self-approval", "Administrator configures but cannot self-approve", "Separates workspace administration from consequential business decisions."),
            ("auditor-read-only", "Auditor receives read-only history", "Supports oversight without granting authority to alter the underlying work."),
        ],
        ("healthcare", "USERS"): [
            ("sender-accountable", "Sending clinician owns complete hand-off data", "Makes the originator accountable for required context and corrections before acceptance."),
            ("receiver-acknowledges", "Receiving clinician accepts responsibility", "Creates a clear transfer point and requires a deadline plus escalation for silence."),
            ("coordinator-escalates", "Coordinator resolves routing exceptions", "Keeps clinical decisions with clinicians while giving operational failures a visible owner."),
            ("emergency-override", "Named clinician may use an emergency override", "Supports urgent care only with a reason, expiry, and complete audit history."),
        ],
        ("payment", "USERS"): [
            ("initiator-no-approval", "Initiator cannot approve the same payment", "Enforces separation of duties for consequential money movement."),
            ("threshold-approver", "Approver authority has a monetary limit", "Keeps routine work fast while escalating larger payments to stronger authority."),
            ("operations-reversal", "Operations may reverse but not erase history", "Enables recovery while preserving the original transaction and accountable reason."),
            ("investigator-read-only", "Investigator reviews provider evidence", "Supports reconciliation without silently changing financial status."),
        ],
        ("ai", "USERS"): [
            ("user-submits", "User supplies the task and source material", "Makes input ownership explicit and supports permission checks on supplied data."),
            ("reviewer-accepts", "Human reviewer accepts or corrects output", "Keeps final responsibility human-owned and creates feedback for evaluation."),
            ("specialist-escalation", "Specialist decides high-risk cases", "Routes ambiguous or consequential outputs to a role with the right expertise."),
            ("admin-policy-only", "Administrator configures policy, not outcomes", "Separates system settings from individual content decisions."),
        ],
        ("data-integration", "USERS"): [
            ("source-owner", "Source owner defines authoritative fields", "Prevents the connector from inventing ownership when systems disagree."),
            ("operator-corrects", "Exception operator corrects rejected records", "Creates a visible recovery path with reasons and validation before replay."),
            ("data-steward-approves", "Data steward approves consequential mappings", "Adds control for changes that could affect many downstream records."),
            ("consumer-read-only", "Data consumer sees freshness and failures", "Provides trust signals without granting correction authority."),
        ],
        ("booking", "SCOPE"): [
            ("closed-booking", "Complete booking journey", "Covers request, confirmation, assignment, changes, completion, and visible recovery."),
            ("operations-first", "Owner scheduling first", "Proves availability and assignment controls before broader customer self-service."),
            ("customer-first", "Customers request and track bookings while owners resolve exceptional conflicts", "Keeps the customer journey self-service while a named owner handles unusual scheduling conflicts manually."),
        ],
        ("saas", "SCOPE"): [
            ("request-decision", "Request-to-decision journey", "Covers submission, policy validation, approval, rejection, history, and completion."),
            ("controls-first", "Workspace controls first", "Proves membership, permissions, and policy enforcement before broad workflow features."),
            ("requester-first", "Requester experience first", "Prioritizes submission and status while complex administration remains manual."),
        ],
        ("healthcare", "SCOPE"): [
            ("closed-handoff", "Closed-loop care hand-off", "Covers creation, validation, acknowledgment, escalation, correction, and traceability."),
            ("clinical-team-first", "Clinical-team workflow first", "Proves safety and accountability before any patient-facing experience."),
            ("coordination-first", "Coordination visibility first", "Prioritizes queues and ownership while complex clinical edits remain manual."),
        ],
        ("payment", "SCOPE"): [
            ("payment-lifecycle", "One complete payment lifecycle", "Covers initiation, provider response, final status, failure, reversal, and reconciliation."),
            ("operations-first", "Operations controls first", "Proves approval and reconciliation before exposing broad customer self-service."),
            ("collection-first", "Successful collection first", "Prioritizes payment completion while complex disputes remain manual."),
        ],
        ("ai", "SCOPE"): [
            ("reviewed-task", "One reviewed AI-assisted task", "Covers input, grounding, output, uncertainty, human acceptance, and correction."),
            ("draft-only", "Drafting support only", "Keeps final decisions human-owned while validating usefulness and safety."),
            ("evaluation-first", "Evaluation workflow first", "Builds evidence about quality and risk before automating a user-facing action."),
        ],
        ("data-integration", "SCOPE"): [
            ("one-way-sync", "One authoritative one-way sync", "Covers extraction, validation, acceptance, rejection, replay, and observability."),
            ("validation-first", "Validation and exception handling first", "Proves data quality controls before increasing connector breadth."),
            ("new-records-first", "New records first", "Avoids historical cleanup until the live path is reliable."),
        ],
        ("booking", "WORKFLOWS"): [
            ("owner-resolves", "Owner resolves conflicts", "Keeps assignment authority clear but requires a response deadline and escalation."),
            ("offer-alternatives", "Offer alternative slots automatically", "Reduces waiting while requiring trustworthy availability and conflict prevention."),
            ("expire-request", "Expire and notify everyone", "Prevents indefinite pending bookings but needs a clear expiry window and recovery path."),
        ],
        ("booking", "QUALITY_GOALS"): [
            ("no-double-booking", "Prevent confirmed double-bookings", "Prioritizes atomic availability checks and conflict tests before convenience features."),
            ("protect-location", "Protect address and access details", "Prioritizes state-based visibility, auditability, and prompt access removal."),
            ("reliable-notices", "Make status notifications dependable", "Prioritizes delivery tracking, retry limits, and a visible manual recovery path."),
            ("fast-mobile", "Keep mobile booking responsive", "Prioritizes a measured completion-time target on ordinary mobile connections."),
        ],
        ("booking", "METRICS"): [
            ("booking-conflict-rate", "Confirmed booking-conflict rate", "Measures whether the release eliminates the scheduling error it was funded to prevent."),
            ("confirmation-time", "Request-to-confirmation time", "Measures whether ownership and deadlines make bookings faster for customers."),
            ("recovery-volume", "Manual recovery volume", "Reveals notification, conflict, and assignment failures hidden by completion counts."),
            ("repeat-booking", "Successful repeat booking", "Tests whether customers and owners trust the new process enough to reuse it."),
        ],
        ("booking", "BUSINESS_RULES"): [
            ("change-cutoff", "Changes follow a clear cutoff", "Defines when customers may self-serve and when an owner must decide the exception."),
            ("availability-before-confirm", "Availability is rechecked before confirmation", "Prevents stale schedules from creating a confirmed conflict during simultaneous requests."),
            ("assignment-acceptance", "Professional acceptance expires at a confirmed deadline and then reassigns", "Keeps bookings from waiting indefinitely and makes reassignment after silence an explicit state transition."),
            ("override-reason", "Owner overrides require a reason", "Allows exceptional handling while preserving who changed the normal rule and why."),
        ],
        ("healthcare", "WORKFLOWS"): [
            ("escalate-unacknowledged", "Escalate an unacknowledged hand-off", "Prevents silent delay by assigning a deadline, backup recipient, and visible owner."),
            ("reject-for-correction", "Return incomplete information for correction", "Protects record quality while preserving urgency and an accountable correction path."),
            ("emergency-path", "Use a governed urgent path", "Allows faster handling only with named authority and complete audit history."),
        ],
        ("healthcare", "QUALITY_GOALS"): [
            ("acknowledgment-target", "Bound acknowledgment time", "Prioritizes a measurable deadline, escalation, and visibility of unaccepted hand-offs."),
            ("access-integrity", "Prevent incorrect patient-record access", "Prioritizes least privilege, permission tests, and auditable denial events."),
            ("traceable-changes", "Make every correction traceable", "Prioritizes immutable history, correction reasons, and accountable ownership."),
        ],
        ("healthcare", "ENTITIES"): [
            ("minimum-handoff", "Minimum necessary hand-off record", "Limits the record to information needed for safe coordination and requires explicit validation."),
            ("role-and-state-access", "Access depends on role and hand-off state", "Removes broad visibility after responsibility changes while preserving traceability."),
            ("correction-history", "Corrections append to immutable history", "Preserves what changed, why, and who authorized it without silently rewriting care context."),
            ("retention-owner", "Retention follows an accountable policy owner", "Keeps deletion and retention decisions outside ad hoc user behavior."),
        ],
        ("healthcare", "RISKS"): [
            ("missed-escalation", "Unacknowledged urgent hand-off", "Prioritizes deadlines, backup recipients, and evidence that escalation actually occurred."),
            ("wrong-patient-access", "Wrong-patient or wrong-role access", "Prioritizes identity checks, least privilege, denial testing, and auditable access events."),
            ("silent-correction", "Untraceable clinical-context correction", "Prioritizes immutable history and clear ownership of corrected information."),
            ("outage-continuity", "Care coordination during an outage", "Requires a safe continuity path and later reconciliation without inventing clinical status."),
        ],
        ("healthcare", "BUSINESS_RULES"): [
            ("acknowledgment-deadline", "Acknowledgment has a governed deadline", "Defines when responsibility transfers and when an unanswered hand-off escalates."),
            ("required-context", "Required context before routine acceptance", "Prevents incomplete hand-offs while preserving a separately governed urgent path."),
            ("correction-approval", "Consequential corrections require a reason", "Maintains accountability without blocking obvious clerical fixes indefinitely."),
            ("emergency-break-glass", "Emergency access is time-limited", "Permits urgent access only with named authority, expiry, and immediate audit review."),
        ],
        ("healthcare", "METRICS"): [
            ("acknowledgment-time", "Time to acknowledged hand-off", "Measures whether ownership transfers promptly instead of remaining silently pending."),
            ("overdue-rate", "Overdue unacknowledged hand-offs", "Measures the exact coordination failure the workflow must expose and escalate."),
            ("correction-rate", "Hand-offs returned for correction", "Shows whether required context and validation improve information quality."),
            ("safety-guardrail", "Safety and privacy incident guardrail", "Prevents a faster workflow from being declared successful if harm increases."),
        ],
        ("saas", "WORKFLOWS"): [
            ("delegate-backup", "Delegate to a backup approver", "Keeps work moving but requires delegation scope, expiry, and audit history."),
            ("escalate-deadline", "Escalate after a response deadline", "Preserves primary authority while preventing requests from waiting indefinitely."),
            ("return-requester", "Return the item to its requester", "Avoids silent state changes and makes the required correction explicit."),
        ],
        ("saas", "ENTITIES"): [
            ("workspace-owned", "Every work item belongs to one workspace", "Creates a hard tenant boundary for queries, permissions, exports, and deletion."),
            ("membership-scoped", "Access follows active membership", "Removes visibility when membership ends while preserving governed audit history."),
            ("policy-versioned", "Decisions retain the policy version used", "Makes later review explainable when approval rules change."),
            ("audit-immutable", "Decision history cannot be overwritten", "Preserves who acted, when, under which authority, and why."),
        ],
        ("saas", "QUALITY_GOALS"): [
            ("tenant-isolation", "Prevent cross-workspace access", "Prioritizes tenant-bound queries, permission tests, and auditable denial events."),
            ("decision-integrity", "Never lose or duplicate an approval", "Prioritizes idempotent transitions and visible recovery from partial failure."),
            ("approval-availability", "Keep submission and approval available", "Prioritizes graceful degradation and a defined recovery target for the core journey."),
            ("core-screen-speed", "Keep core screens responsive", "Requires a measured response-time target at realistic workspace size and load."),
        ],
        ("saas", "BUSINESS_RULES"): [
            ("no-self-approval", "Requester cannot approve their own work", "Enforces separation of duties and defines reassignment when no approver is available."),
            ("approval-threshold", "Approval authority follows a threshold", "Routes consequential requests upward while keeping routine work fast."),
            ("delegation-expiry", "Delegation has scope and expiry", "Prevents permanent authority drift and preserves who delegated each decision."),
            ("override-audited", "Overrides require reason and audit history", "Allows exceptional handling without erasing the normal policy decision."),
        ],
        ("saas", "METRICS"): [
            ("approval-cycle-time", "Request-to-decision time", "Measures whether clearer ownership and escalation reduce approval delay."),
            ("overdue-request-rate", "Overdue request rate", "Shows whether work still becomes stuck despite visible status."),
            ("policy-exception-rate", "Policy exception volume", "Reveals whether the configured rules fit real work or create manual burden."),
            ("successful-workspaces", "Workspaces completing the journey repeatedly", "Measures adoption through successful outcomes rather than account creation alone."),
        ],
        ("payment", "WORKFLOWS"): [
            ("pending-reconcile", "Keep a visible pending state", "Avoids false success while retries or provider reconciliation are still unresolved."),
            ("idempotent-retry", "Retry with duplicate protection", "Recovers temporary provider failure without creating another charge."),
            ("manual-investigation", "Route to financial investigation", "Preserves auditability when provider and internal statuses disagree."),
        ],
        ("data-integration", "WORKFLOWS"): [
            ("quarantine-invalid", "Quarantine invalid records", "Prevents contaminated downstream data while preserving evidence for correction."),
            ("retry-idempotently", "Retry safely from a checkpoint", "Recovers partial failure without duplicating accepted records."),
            ("operator-replay", "Require an operator-approved replay", "Adds control for consequential corrections and records who authorized them."),
        ],
    }
    return specific.get((profile, category), [])


def _validated_generated_options(
    payload: DiscoveryPlanningRequest,
    raw_options: object,
) -> list[DiscoveryChoiceOption]:
    options, _ = _generated_options_with_rejections(payload, raw_options)
    return options


def _generated_options_with_rejections(
    payload: DiscoveryPlanningRequest,
    raw_options: object,
) -> tuple[list[DiscoveryChoiceOption], list[str]]:
    """Return accepted choices and safe, precise rejection reasons for repair."""
    if not isinstance(raw_options, list):
        return [], ["options was not an array"]
    options: list[DiscoveryChoiceOption] = []
    rejections: list[str] = []
    seen_keys: set[str] = set()
    seen_labels: set[str] = set()
    project_terms = _distinctive_project_terms(payload)
    for index, raw in enumerate(raw_options[:6], start=1):
        if not isinstance(raw, dict):
            rejections.append(f"choice {index} was not an object")
            continue
        try:
            option = DiscoveryChoiceOption.model_validate(raw)
        except (TypeError, ValueError):
            rejections.append(f"choice {index} did not match the key/label/description contract")
            continue
        normalized_label = option.label.casefold().strip()
        if option.key in seen_keys or normalized_label in seen_labels:
            rejections.append(f"choice {index} duplicated another key or label")
            continue
        if normalized_label in _SHALLOW_OPTION_LABELS:
            rejections.append(f"choice {index} used a shallow answer label")
            continue
        if not _is_operational_option(option):
            rejections.append(
                f"choice {index} did not state a complete actor/action/boundary/consequence decision"
            )
            continue
        if option.key != "not-decided" and re.search(
            r"\b(?:not decided|not yet decided|undecided|unknown|unsure|maybe)\b", normalized_label
        ):
            rejections.append(f"choice {index} disguised uncertainty under a different key")
            continue
        if _has_unsupported_numeric_claim(payload, option.label + " " + option.description):
            rejections.append(f"choice {index} invented a numeric threshold or duration")
            continue
        if _has_unsupported_domain_term(payload, option.label + " " + option.description):
            rejections.append(f"choice {index} introduced an unsupported domain role")
            continue
        if _looks_like_prompt_leak(option.label + " " + option.description):
            rejections.append(f"choice {index} contained internal-instruction language")
            continue
        if option.key != "not-decided" and project_terms \
                and not (_semantic_terms(option.label + " " + option.description) & project_terms):
            rejections.append(f"choice {index} was not grounded in a distinctive project term")
            continue
        seen_keys.add(option.key)
        seen_labels.add(normalized_label)
        options.append(option)
    if len(raw_options) > 6:
        rejections.append("more than six choices were returned")
    return options[:6], rejections


def _is_operational_option(option: DiscoveryChoiceOption) -> bool:
    """Require a choice to remain meaningful after it becomes confirmed evidence."""
    rendered = f"{option.label}. {option.description}".casefold()
    if option.key == "not-decided":
        return True
    return len(rendered.split()) >= 12 and any(term in rendered for term in (
        " must ", " may ", " only ", " when ", " before ", " after ", " while ",
        " requires ", " keeps ", " prevents ", " routes ", " owns ", " remains ",
        " expires ", " retries ", " records ", " blocks ", " confirms ", " prioritizes ",
    ))


def _contextualize_options(
    payload: DiscoveryPlanningRequest,
    options: list[DiscoveryChoiceOption],
) -> list[DiscoveryChoiceOption]:
    """Keep answer choices tied to the same project context as the question."""
    context = _project_context_phrase(payload)
    project_terms = _distinctive_project_terms(payload)
    if not context:
        return options
    contextualized: list[DiscoveryChoiceOption] = []
    for option in options:
        combined_terms = _semantic_terms(option.label + " " + option.description)
        if project_terms and combined_terms & project_terms:
            contextualized.append(option)
            continue
        description = f"For {context}, {option.description[0].lower() + option.description[1:]}"
        if len(description) > 300:
            description = description[:300].rsplit(" ", 1)[0].rstrip(" ,;:")
        contextualized.append(option.model_copy(update={"description": description}))
    return contextualized


def _has_unsupported_numeric_claim(payload: DiscoveryPlanningRequest, value: str) -> bool:
    context = _context_text(payload)
    claims = re.findall(
        r"\b\d+(?:\.\d+)?\s*(?:%|percent|seconds?|minutes?|hours?|days?|weeks?|months?|years?|users?|requests?|records?)(?![a-z0-9])",
        value.casefold(),
    )
    return any(claim not in context for claim in claims)


def _has_unsupported_domain_term(payload: DiscoveryPlanningRequest, value: str) -> bool:
    context = _context_text(payload)
    lowered = value.casefold()
    return "cleaner" in lowered and "cleaner" not in context


def _validate_quality(
    payload: DiscoveryPlanningRequest,
    question: DiscoveryQuestion,
    options: list[DiscoveryChoiceOption],
) -> None:
    text = question.question_text.strip()
    why = question.why_we_ask.strip()
    if len(text) < 35 or len(text) > 420 or not text.endswith("?") or text.count("?") != 1:
        raise ValueError("Planner question is not concise and interrogative")
    if len(text.split()) > 55 or len(why) < 25 or len(why) > 500:
        raise ValueError("Planner question or rationale failed the clarity gate")
    if _looks_like_prompt_leak(text + " " + why):
        raise ValueError("Planner output contained internal-instruction language")
    project_name = payload.project.name.strip().casefold()
    if len(project_name) >= 3 and project_name in text.casefold():
        raise ValueError("Planner mechanically inserted the project title instead of using its context")
    project_terms = _distinctive_project_terms(payload)
    specificity_matches = len(_semantic_terms(text) & project_terms)
    if "roles within" in text.casefold() or (
        any(pattern.search(text) for pattern in _GENERIC_PATTERNS)
        and specificity_matches < min(2, len(project_terms))
    ):
        raise ValueError("Planner question is generic or mechanically interpolated")
    _validate_atomic_question(text)
    _validate_category_coverage(question.category, text)
    required_matches = min(2, len(project_terms))
    if required_matches and specificity_matches < required_matches:
        raise ValueError("Planner question did not use distinctive project description or project-field context")
    if _is_semantic_duplicate(payload, question.key, text):
        raise ValueError("Planner question repeats an earlier question")
    _reject_unsupported_claims(payload, text + " " + why)
    if len(options) < 4:
        raise ValueError("Planner options do not provide useful decision support")
    if not any(option.key == "not-decided" for option in options):
        raise ValueError("Planner options must preserve legitimate uncertainty")
    if any(option.label.casefold().strip() in _SHALLOW_OPTION_LABELS for option in options):
        raise ValueError("Planner supplied shallow answer labels")


def _validate_category_coverage(category: str, question_text: str) -> None:
    lowered = question_text.casefold()
    if category == "QUALITY_GOALS" and not re.search(
        r"(?:\bwhat\b[^?]{0,90}\b(?:measurable\s+)?(?:target|threshold|maximum|minimum)\b|"
        r"\bhow\s+(?:fast|quickly|often|many|reliably)\b|\bhow\b[^?]{0,50}\bmeasur|"
        r"\bwithin\s+how\b|\bp95\b|\bfailure\b)",
        lowered,
    ):
        raise ValueError("Planner question omitted a measurable quality threshold")
    requirements: dict[str, tuple[tuple[str, ...], ...]] = {
        "SCOPE": (
            ("first release", "initial release", "mvp", "launch"),
        ),
        "WORKFLOWS": (
            ("state", "status", "trigger", "start", "after", "when", "fail", "conflict", "recover", "complete"),
        ),
        "QUALITY_GOALS": (
            ("failure", "measur", "target", "threshold", "maximum", "minimum", "within", "p95", "percent"),
        ),
        "METRICS": (
            ("baseline", "target", "numerator", "denominator", "rate", "window", "measure", "%"),
        ),
        "CONSTRAINTS": (
            ("fixed", "non-negotiable", "strictly", "cannot move", "govern", "constraint", "boundary"),
        ),
    }
    for alternatives in requirements.get(category, ()):
        if not any(term in lowered for term in alternatives):
            raise ValueError(f"Planner question omitted a required {category.lower()} decision facet")


def _validate_atomic_question(question_text: str) -> None:
    """Reject the recurring actor/access/retention/lifecycle inventory question."""
    lowered = question_text.casefold()
    axes = (
        ("actor", "role", "who", "customer", "operator", "professional"),
        ("permission", "access", "view", "authorize", "approval", "override"),
        ("retention", "retain", "delete", "deletion", "archive"),
        ("lifecycle", "state", "status", "expire", "transition", "correction"),
    )
    matched_axes = sum(any(term in lowered for term in axis) for axis in axes)
    if matched_axes >= 3:
        raise ValueError("Planner question combines unrelated decision facets")


def _is_semantic_duplicate(
    payload: DiscoveryPlanningRequest,
    question_key: str,
    question_text: str,
) -> bool:
    current = _semantic_terms(question_text)
    for answer in payload.answers:
        if answer.question_key == question_key:
            # Material open facets deliberately revisit the same server-owned
            # question key. Compare against other decisions, not the answer
            # currently being refined.
            continue
        if not answer.question_text:
            continue
        prior = _semantic_terms(answer.question_text)
        union = current | prior
        if union and len(current & prior) / len(union) >= 0.62:
            return True
    return False


def _semantic_terms(value: str) -> set[str]:
    return {
        token for token in re.findall(r"[a-z0-9]+", value.casefold())
        if len(token) > 2 and token not in _STOP_WORDS
    }


def _reject_unsupported_claims(payload: DiscoveryPlanningRequest, value: str) -> None:
    context = _context_text(payload)
    lowered = value.casefold()
    for term in ("hipaa", "gdpr", "pci dss", "soc 2", "ferpa"):
        if term in lowered and term not in context and not re.search(rf"\b(?:if|whether|might|could)\b[^.?]*\b{re.escape(term)}\b", lowered):
            raise ValueError("Planner asserted an unsupported compliance fact")
    if "you mentioned" in lowered:
        # This phrase too easily launders an invented detail into confirmed
        # context. The model should refer directly to a supplied fact instead.
        raise ValueError("Planner used an unsafe confirmation claim")
    for phrase in ("as you said", "as confirmed", "the project requires", "your requirement for"):
        if phrase in lowered:
            raise ValueError("Planner presented a possible detail as confirmed")
    for term in (
        "stripe", "twilio", "salesforce", "hubspot", "sap", "aws", "azure", "google cloud",
        "postgresql", "mongodb", "kafka", "oauth", "single sign-on",
    ):
        if term in lowered and term not in context and not re.search(
            rf"\b(?:if|whether|might|could|example|such as)\b[^.?]*\b{re.escape(term)}\b", lowered
        ):
            raise ValueError("Planner asserted an unsupported implementation fact")


def _selection_reason(question: DiscoveryQuestion, evaluation: _Evaluation) -> str:
    reason = evaluation.reasons[0]
    return f"Selected {question.category.lower().replace('_', ' ')} because it is {reason}; resolving it has the highest current information value."


def _score_responses(evaluations: list[_Evaluation]) -> list[DiscoveryCandidateScore]:
    return [
        DiscoveryCandidateScore(
            key=item.question.key,
            category=item.question.category,
            score=item.score,
            reasons=list(item.reasons),
        )
        for item in evaluations
    ]


def _context_text(payload: DiscoveryPlanningRequest) -> str:
    parts = [
        payload.project.name,
        payload.project.description or "",
        payload.project.type,
        payload.project.industry or "",
        payload.project.target_audience or "",
        payload.project.tech_stack or "",
        f"{payload.project.team_size} people" if payload.project.team_size else "",
    ]
    parts.extend(answer.answer_text or "" for answer in payload.answers if answer.disposition == "ANSWERED")
    parts.extend(" ".join(answer.selected_option_keys) for answer in payload.answers if answer.selected_option_keys)
    parts.extend(item.excerpt for item in payload.evidence)
    return " ".join(parts).casefold()


def _all_source_anchors(payload: DiscoveryPlanningRequest) -> list[str]:
    anchors = ["project:title", "project:description", "project:type"]
    if payload.project.industry:
        anchors.append("project:industry")
    if payload.project.target_audience:
        anchors.append("project:target-audience")
    if payload.project.tech_stack:
        anchors.append("project:tech-stack")
    if payload.project.team_size:
        anchors.append("project:team-size")
    anchors.extend(f"answer:{answer.question_key or answer.category.lower()}" for answer in payload.answers)
    anchors.extend(f"open-question:{item.key}" for item in payload.open_questions)
    anchors.extend(f"evidence:{item.source_id}" for item in payload.evidence)
    return list(dict.fromkeys(anchors))


def _source_anchors(payload: DiscoveryPlanningRequest, question: DiscoveryQuestion) -> list[str]:
    del question
    anchors = _all_source_anchors(payload)
    relevant = [anchor for anchor in anchors if anchor.startswith(("answer:", "open-question:", "evidence:"))]
    project_anchors = [anchor for anchor in anchors if anchor.startswith("project:")]
    return [*project_anchors[:5], *relevant[-6:]][:10]


def _confirmed_context(payload: DiscoveryPlanningRequest, anchors: list[str]) -> list[str]:
    facts: list[str] = []
    for anchor in anchors:
        if anchor == "project:title":
            facts.append(f"Project title: {payload.project.name}")
        elif anchor == "project:description" and payload.project.description:
            facts.append("Project description supplied by the owner")
        elif anchor == "project:type":
            facts.append(f"Project type: {payload.project.type}")
        elif anchor == "project:industry" and payload.project.industry:
            facts.append(f"Industry: {payload.project.industry}")
        elif anchor == "project:target-audience" and payload.project.target_audience:
            facts.append(f"Target audience: {payload.project.target_audience}")
        elif anchor == "project:team-size" and payload.project.team_size:
            facts.append(f"Confirmed team size: {payload.project.team_size}")
        elif anchor.startswith("answer:"):
            answer_key = anchor.removeprefix("answer:")
            answer = next((item for item in payload.answers
                           if (item.question_key or item.category.lower()) == answer_key), None)
            decisions = [] if answer is None else [
                _DECISION_FOCUS_BY_KEY[key]
                for key in answer.selected_option_keys
                if key in _DECISION_FOCUS_BY_KEY
            ]
            if decisions:
                facts.append(
                    f"Owner-confirmed {answer.category.lower().replace('_', ' ')} decision: "
                    + "; ".join(decisions[:2])
                )
            else:
                facts.append(f"Owner answer: {answer_key.replace('-', ' ')}")
        elif anchor.startswith("evidence:"):
            facts.append("Owner-approved project evidence")
        elif anchor.startswith("open-question:"):
            facts.append(f"Visible unresolved gap: {anchor.removeprefix('open-question:').replace('-', ' ')}")
    return list(dict.fromkeys(facts))[:12]


def _assumptions_to_validate(payload: DiscoveryPlanningRequest, question: DiscoveryQuestion) -> list[str]:
    context = _context_text(payload)
    assumptions: list[str] = []
    if question.category == "INTEGRATIONS" and not any(_contains_signal(context, item) for item in ("api", "integration", "sms", "email", "webhook", "provider")):
        assumptions.append("Whether any external service is essential to the first release remains unconfirmed.")
    if question.category in {"USERS", "STAKEHOLDERS", "BUSINESS_RULES"}:
        assumptions.append("The proposed authority patterns are decision options, not confirmed role assignments.")
    if question.category == "QUALITY_GOALS":
        assumptions.append("The example failure modes and targets remain choices until the owner confirms them.")
    return assumptions


def _missing_requirement(category: str) -> str:
    return {
        "PROBLEM": "A prioritized problem, affected outcome, urgency, and observable improvement signal.",
        "USERS": "Actors, responsibilities, permission boundaries, override authority, and notification needs.",
        "STAKEHOLDERS": "Final decision authority, specialist sign-off, release acceptance, and escalation ownership.",
        "SCOPE": "One testable end-to-end first-release slice plus the nearest deferred capability.",
        "EXCLUSIONS": "Explicit non-goals that constrain architecture, estimates, and acceptance criteria.",
        "WORKFLOWS": "State transitions, hand-offs, timeouts, exception paths, recovery, and user feedback.",
        "ENTITIES": "Authoritative records, ownership, access by state, correction, retention, deletion, and audit history.",
        "INTEGRATIONS": "System authority, exchanged data, authentication, idempotency, outage behavior, and reconciliation ownership.",
        "QUALITY_GOALS": "A prioritized failure mode with a measurable security, integrity, reliability, accessibility, or latency target.",
        "CONSTRAINTS": "The fixed delivery boundary and the explicit trade-off policy when plans conflict.",
        "RISKS": "A credible harm scenario with prevention, detection, containment, recovery, and accountable escalation.",
        "BUSINESS_RULES": "Authoritative approval, override, eligibility, calculation, validation, and access rules.",
        "METRICS": "A baseline, target, measurement source, review period, and decision triggered by the result.",
    }.get(category, "A material unanswered requirement needed by downstream documents.")


def _safe_generated_text(value: object, fallback: str, maximum: int) -> str:
    text = str(value or "").strip()
    return fallback if not text or len(text) > maximum or _looks_like_prompt_leak(text) else text


def _safe_list(value: object, maximum: int) -> list[str]:
    if not isinstance(value, list):
        return []
    result = []
    for item in value[:maximum]:
        text = str(item).strip()
        if 5 <= len(text) <= 400 and not _looks_like_prompt_leak(text):
            result.append(text)
    return result


def _looks_like_prompt_leak(value: str) -> bool:
    lowered = value.casefold()
    return any(term in lowered for term in (
        "system prompt", "internal prompt", "ignore previous", "ignore all previous",
        "gemini_api_key", "developer message", "hidden instruction",
    ))


def _contains_signal(context: str, signal: str) -> bool:
    return re.search(rf"(?<![a-z0-9]){re.escape(signal)}(?![a-z0-9])", context) is not None


def _is_low_information(value: str | None) -> bool:
    if value is None or not value.strip():
        return True
    normalized = value.strip().casefold()
    return len(normalized) < 12 or re.fullmatch(
        r"(?:tbd|unknown|not sure|unsure|maybe|not decided(?: yet)?|to be decided|n/?a)[.!]?", normalized
    ) is not None
