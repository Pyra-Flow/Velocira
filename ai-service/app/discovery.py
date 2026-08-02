"""Risk-aware, typed discovery-question planning with a deterministic dev fallback."""

from __future__ import annotations

from app.models import (
    DiscoveryPlanningRequest,
    DiscoveryPlanningResponse,
    DiscoveryQuestion,
)


_QUESTION_CATALOG: tuple[DiscoveryQuestion, ...] = (
    DiscoveryQuestion(
        key="problem", category="PROBLEM",
        question_text="What problem are you trying to solve, and why does it matter now?",
        why_we_ask="This anchors the goal and prevents features from being mistaken for the problem.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="users", category="USERS",
        question_text="Who will use this, and what does each main user need to accomplish?",
        why_we_ask="User groups define actors, permissions, and the workflows we need to document.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="stakeholders", category="STAKEHOLDERS",
        question_text="Who can make decisions, approve work, or be affected by the system?",
        why_we_ask="Stakeholders reveal approvals, ownership, and conflicting priorities early.",
        risk_level="MEDIUM",
    ),
    DiscoveryQuestion(
        key="scope", category="SCOPE",
        question_text="What must the first release do? Describe the smallest successful outcome.",
        why_we_ask="A clear scope keeps requirements testable and limits accidental expansion.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="exclusions", category="EXCLUSIONS",
        question_text="What is explicitly out of scope for this release?",
        why_we_ask="Explicit exclusions protect the team from assumptions and scope creep.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="workflows", category="WORKFLOWS",
        question_text="Walk me through the most important user journey from start to finish.",
        why_we_ask="Core workflows become the backbone for requirements, tests, and UX flows.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="entities", category="ENTITIES",
        question_text="What important information will the system store or manage?",
        why_we_ask="Key entities reveal data ownership, records, and validation needs.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="integrations", category="INTEGRATIONS",
        question_text="Which systems, APIs, files, devices, or services must it connect to?",
        why_we_ask="Integrations add security, reliability, and delivery risks that must be explicit.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="quality", category="QUALITY_GOALS",
        question_text="What quality goals matter most—for example security, accessibility, uptime, privacy, or speed?",
        why_we_ask="Quality targets make non-functional requirements measurable instead of implied.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="constraints", category="CONSTRAINTS",
        question_text="What constraints must we respect: budget, deadline, technology, compliance, team capacity, or platform?",
        why_we_ask="Constraints shape feasible options and expose delivery trade-offs.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="risks", category="RISKS",
        question_text="What could make this project fail, harm users, or block delivery?",
        why_we_ask="Named risks can be mitigated; hidden risks cannot be assessed.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="business-rules", category="BUSINESS_RULES",
        question_text="Are there policies, calculations, eligibility rules, or approvals the system must enforce?",
        why_we_ask="Business rules must come from you; the planner will not invent them.",
        risk_level="HIGH",
    ),
    DiscoveryQuestion(
        key="metrics", category="METRICS",
        question_text="How will you know the project is successful after launch?",
        why_we_ask="Success metrics make the brief measurable and help prioritize later decisions.",
        risk_level="MEDIUM",
    ),
)


def unanswered_questions(payload: DiscoveryPlanningRequest) -> list[DiscoveryQuestion]:
    answered_categories = {answer.category for answer in payload.answers}
    return [question for question in _QUESTION_CATALOG if question.category not in answered_categories]


def plan_next_question(payload: DiscoveryPlanningRequest) -> DiscoveryPlanningResponse:
    """Choose one unanswered high-value category without inventing requirements."""

    next_question = next(iter(unanswered_questions(payload)), None)
    suggestions: list[str] = []
    if next_question is None:
        suggestions.append("All baseline categories were addressed. Review visible open questions before confirming the brief.")
    return DiscoveryPlanningResponse(
        planner="deterministic-risk-aware-v1",
        next_question=next_question,
        suggestions=suggestions,
        assumptions=[],
    )


def plan_for_selected_key(
    payload: DiscoveryPlanningRequest, key: str, *, planner: str
) -> DiscoveryPlanningResponse:
    """Accept only a selection from the server-owned catalog."""

    question = next((item for item in unanswered_questions(payload) if item.key == key), None)
    if question is None:
        return plan_next_question(payload)
    return DiscoveryPlanningResponse(
        planner=planner,
        next_question=question,
        suggestions=[],
        assumptions=[],
    )
