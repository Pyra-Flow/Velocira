"""Public standards metadata and conservative project applicability rules.

The registry stores only original summaries and public metadata. It never embeds
copyrighted standard text and never claims that generated documentation is a
certification or legal compliance assessment.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any


@dataclass(frozen=True, slots=True)
class StandardReference:
    key: str
    name: str
    version: str
    official_url: str
    purpose: str
    applies_to: tuple[str, ...]
    always: bool = False
    document_types: tuple[str, ...] = ("SRS",)
    recommendation: str = "RECOMMENDED"
    rules: tuple[str, ...] = ()


CHECKED_ON = date(2026, 8, 16).isoformat()

REGISTRY: tuple[StandardReference, ...] = (
    StandardReference(
        "ISO_29148", "ISO/IEC/IEEE 29148", "2018 (confirmed 2024; revision in development)",
        "https://www.iso.org/standard/72089.html",
        "Requirements engineering processes, information-item content, and requirement quality.",
        ("all",), True,
        ("SRS", "BRD", "requirements registry", "traceability matrix"),
        "BASELINE",
        ("Give every requirement a stable identifier and source.", "Validate necessity, clarity, consistency, verifiability, and traceability."),
    ),
    StandardReference(
        "ISO_15288", "ISO/IEC/IEEE 15288", "2023",
        "https://www.iso.org/standard/81702.html",
        "System life-cycle processes spanning conception, development, use, support, and retirement.",
        ("system", "hardware", "embedded", "iot", "device", "complex", "enterprise"),
        False,
        ("life-cycle plan", "architecture", "deployment", "operations", "handover"),
        "CONDITIONAL",
        ("Cover applicable life-cycle stages and stakeholder responsibilities.", "Record tailoring decisions instead of assuming one delivery methodology."),
    ),
    StandardReference(
        "ISO_12207", "ISO/IEC/IEEE 12207", "2026",
        "https://www.iso.org/standard/90219.html",
        "Software life-cycle processes for acquisition, development, operation, maintenance, and disposal.",
        ("software", "web", "mobile", "api", "saas", "platform", "app"),
        False,
        ("software development plan", "test plan", "release plan", "operations", "maintenance"),
        "CONDITIONAL",
        ("Connect requirements, implementation, verification, release, operation, and retirement work products.",),
    ),
    StandardReference(
        "ISO_42010", "ISO/IEC/IEEE 42010", "2022",
        "https://www.iso.org/standard/74393.html",
        "Architecture descriptions, stakeholders, concerns, viewpoints, views, and rationale.",
        ("all",), True,
        ("architecture description", "C4 views", "architecture decision records"),
        "BASELINE",
        ("Identify stakeholders, concerns, viewpoints, views, correspondences, and rationale.",),
    ),
    StandardReference(
        "ISO_25010", "ISO/IEC 25010", "2023",
        "https://www.iso.org/standard/78176.html",
        "Product-quality characteristics and measurable quality scenarios.",
        ("all",), True,
        ("SRS", "quality scenarios", "test strategy"),
        "BASELINE",
        ("Express applicable product-quality characteristics as measurable scenarios.",),
    ),
    StandardReference(
        "ISO_29119", "ISO/IEC/IEEE 29119-2", "2021",
        "https://www.iso.org/standard/79428.html",
        "Risk-based software test processes and test documentation coverage.",
        ("all",), True,
        ("test strategy", "test plan", "traceability matrix"),
        "BASELINE",
        ("Use risk-based test planning and preserve requirement-to-test evidence.",),
    ),
    StandardReference(
        "RFC_2119_8174", "RFC 2119 and RFC 8174", "1997 / 2017",
        "https://www.rfc-editor.org/rfc/rfc8174.html",
        "Consistent interpretation of normative requirement keywords.",
        ("all",), True,
        ("SRS", "API specification", "acceptance criteria"),
        "BASELINE",
        ("Use normative keywords only where their interpretation is defined.",),
    ),
    StandardReference(
        "C4", "C4 model", "Current public model",
        "https://c4model.com/",
        "Hierarchical system-context, container, component, dynamic, and deployment views.",
        ("all",), True,
        ("architecture description", "diagram register"),
        "BASELINE",
        ("Use context, container, component, deployment, or dynamic views only when they answer a stakeholder concern.",),
    ),
    StandardReference(
        "ARC42", "arc42", "Current public template",
        "https://docs.arc42.org/home/",
        "Pragmatic architecture documentation and measurable quality scenarios.",
        ("all",), True,
        ("architecture description", "quality scenarios", "risk register"),
        "BASELINE",
        ("Keep architecture goals, constraints, building blocks, runtime, deployment, decisions, risks, and quality linked.",),
    ),
    StandardReference(
        "IREB_CPRE", "IREB CPRE requirements engineering guidance", "Glossary 2.2.0 / syllabus 3.3.0",
        "https://cpre.ireb.org/en/downloads-and-resources/glossary",
        "Consistent requirements terminology, context boundaries, work products, quality, validation, and traceability.",
        ("all",), True,
        ("SRS", "glossary", "requirements registry", "traceability matrix"),
        "BASELINE",
        ("Maintain one controlled glossary and explicit system/context boundaries.", "Trace requirements backward to sources and forward to design and tests."),
    ),
    StandardReference(
        "IIBA_BABOK", "IIBA BABOK Guide", "Version 3",
        "https://www.iiba.org/career-resources/a-business-analysis-professionals-foundation-for-success/babok/",
        "Business-analysis planning, elicitation, requirements life-cycle management, strategy analysis, and solution evaluation.",
        ("business", "product", "enterprise", "saas", "platform", "service"),
        False,
        ("executive brief", "stakeholder analysis", "BRD", "roadmap"),
        "CONDITIONAL",
        ("Connect needs, stakeholders, value, context, change, and solution outcomes.",),
    ),
    StandardReference(
        "VOLERE", "Volere Requirements Specification Template", "Edition 20 public structure",
        "https://www.volere.org/templates/volere-requirements-specification-template/",
        "Requirements discovery structure, atomic requirement attributes, terminology, constraints, and fit criteria.",
        ("all",), True,
        ("SRS", "stakeholder analysis", "glossary", "requirements registry"),
        "REFERENCE",
        ("Give applicable requirements a measurable fit criterion and preserve motivation and source.",),
    ),
    StandardReference(
        "WCAG_22", "Web Content Accessibility Guidelines", "2.2",
        "https://www.w3.org/TR/WCAG22/",
        "Testable web accessibility requirements and conformance levels.",
        ("web", "mobile", "frontend", "portal", "site", "app"),
        False,
        ("accessibility specification", "UX requirements", "test plan", "user manual"),
        "CONDITIONAL",
        ("Create testable accessibility requirements and record the intended conformance target as a project decision.",),
    ),
    StandardReference(
        "OWASP_ASVS", "OWASP Application Security Verification Standard", "5.0.0",
        "https://owasp.org/www-project-application-security-verification-standard/",
        "Versioned application-security verification requirements.",
        ("web", "mobile", "api", "saas", "portal", "app", "payment", "health"),
        False,
        ("security specification", "threat model", "test plan"),
        "CONDITIONAL",
        ("Select risk-appropriate verification requirements and trace them to security tests.",),
    ),
    StandardReference(
        "OWASP_SAMM", "OWASP Software Assurance Maturity Model", "2.0",
        "https://owaspsamm.org/model/",
        "Software-security governance and improvement across design, implementation, verification, and operations.",
        ("enterprise", "regulated", "payment", "health", "finance", "security", "sensitive"),
        False,
        ("secure development plan", "security specification", "test strategy", "operations"),
        "CONDITIONAL",
        ("Cover governance, design, implementation, verification, and operations security practices proportionate to risk.",),
    ),
    StandardReference(
        "OWASP_MASVS", "OWASP Mobile Application Security Verification Standard", "Current continuously maintained release",
        "https://mas.owasp.org/MASVS/",
        "Mobile-specific security and privacy verification requirements and test coverage.",
        ("mobile", "android", "ios"),
        False,
        ("mobile security specification", "mobile test plan"),
        "CONDITIONAL",
        ("Cover storage, cryptography, authentication, network, platform, code, resilience, and privacy as applicable.",),
    ),
    StandardReference(
        "NIST_CSF", "NIST Cybersecurity Framework", "2.0",
        "https://www.nist.gov/cyberframework",
        "Cybersecurity governance and risk outcomes across Govern, Identify, Protect, Detect, Respond, and Recover.",
        ("sensitive", "regulated", "payment", "health", "finance", "government", "enterprise"),
        False,
        ("security strategy", "risk register", "incident response", "operations"),
        "CONDITIONAL",
        ("Organize cybersecurity outcomes across Govern, Identify, Protect, Detect, Respond, and Recover.",),
    ),
    StandardReference(
        "NIST_SSDF", "NIST Secure Software Development Framework", "SP 800-218 v1.1",
        "https://csrc.nist.gov/pubs/sp/800/218/final",
        "Secure-software-development practices and verifiable delivery controls.",
        ("sensitive", "regulated", "payment", "health", "finance", "government", "enterprise"),
        False,
        ("secure development plan", "release plan", "security test plan"),
        "CONDITIONAL",
        ("Define secure-development practices and the evidence required to verify them.",),
    ),
    StandardReference(
        "ISO_27001", "ISO/IEC 27001", "2022",
        "https://www.iso.org/standard/27001",
        "Information-security management and risk treatment context.",
        ("sensitive", "regulated", "payment", "health", "finance", "government", "enterprise"),
        False,
        ("security plan", "risk register", "operations"),
        "CONDITIONAL",
        ("Treat information-security management and risk treatment as organizational decisions; never claim certification from generated text.",),
    ),
    StandardReference(
        "ISO_27002", "ISO/IEC 27002", "2022",
        "https://www.iso.org/standard/75652.html",
        "Information-security control guidance supporting risk treatment.",
        ("sensitive", "regulated", "payment", "health", "finance", "government", "enterprise"),
        False,
        ("security specification", "security plan", "operations"),
        "CONDITIONAL",
        ("Derive project-specific control objectives from risk; do not reproduce or imply conformance to paid control text.",),
    ),
    StandardReference(
        "ISO_27701", "ISO/IEC 27701", "2025",
        "https://www.iso.org/standard/27701",
        "Privacy information management for organizations acting as PII controllers or processors.",
        ("personal data", "pii", "privacy", "patient", "customer", "employee", "children", "health", "finance"),
        False,
        ("privacy impact analysis", "data lifecycle", "privacy requirements"),
        "CONDITIONAL",
        ("Record controller/processor roles, purposes, data-subject obligations, privacy risk, and accountable decisions where applicable.",),
    ),
    StandardReference(
        "ISO_31000", "ISO 31000", "2018 (confirmed 2023; revision in development)",
        "https://www.iso.org/standard/65694.html",
        "Risk identification, analysis, evaluation, treatment, monitoring, and communication.",
        ("complex", "regulated", "payment", "health", "finance", "government", "enterprise"),
        False,
        ("risk register", "project plan", "operations"),
        "CONDITIONAL",
        ("Record risk context, owner, cause, consequence, treatment, triggers, and review status without invented scores.",),
    ),
    StandardReference(
        "ISO_22301", "ISO 22301", "2019 with Amendment 1:2024 (revision in development)",
        "https://www.iso.org/standard/75106.html",
        "Business-continuity planning, disruption response, recovery, and continual improvement.",
        ("critical", "regulated", "payment", "health", "finance", "government", "enterprise", "availability"),
        False,
        ("business continuity plan", "disaster recovery", "incident response", "operations"),
        "CONDITIONAL",
        ("Define continuity priorities, recovery dependencies, exercises, and evidence only after impact and target decisions are confirmed.",),
    ),
    StandardReference(
        "ISO_9241_210", "ISO 9241-210", "2019 (confirmed 2025)",
        "https://www.iso.org/standard/77520.html",
        "Human-centred design activities across the life cycle of interactive systems.",
        ("web", "mobile", "frontend", "portal", "site", "app", "interactive"),
        False,
        ("UX requirements", "research plan", "user manual"),
        "CONDITIONAL",
        ("Base design on context of use, user needs, iterative evaluation, and multidisciplinary review.",),
    ),
    StandardReference(
        "ISO_20000_1", "ISO/IEC 20000-1", "2018 with Amendment 1:2024 (confirmed 2023)",
        "https://www.iso.org/standard/70636.html",
        "Service-management planning, transition, delivery, measurement, and improvement.",
        ("service", "saas", "platform", "enterprise", "managed", "support", "sla"),
        False,
        ("service management plan", "operations", "support guide", "change management"),
        "CONDITIONAL",
        ("Document service requirements, ownership, transition, delivery, measurement, and continual improvement where relevant.",),
    ),
    StandardReference(
        "OPENAPI", "OpenAPI Specification", "3.1.x / current supported release",
        "https://spec.openapis.org/oas/",
        "Machine-readable HTTP API contracts with reusable schemas and operation identifiers.",
        ("api", "integration", "webhook", "saas", "platform"),
        False,
        ("API specification", "integration specification"),
        "CONDITIONAL",
        ("Generate only confirmed operations; include reusable schemas, stable operation identifiers, errors, security, and requirement trace links.",),
    ),
    StandardReference(
        "ASYNCAPI", "AsyncAPI Specification", "3.x",
        "https://www.asyncapi.com/docs/reference/specification/latest",
        "Machine-readable event-driven and message-based API contracts.",
        ("event", "message", "queue", "stream", "webhook", "kafka", "mqtt"),
        False,
        ("event specification", "webhook specification"),
        "CONDITIONAL",
        ("Define channels, operations, messages, correlation, errors, security, and delivery semantics only when supported by evidence.",),
    ),
    StandardReference(
        "JSON_SCHEMA", "JSON Schema", "Draft 2020-12",
        "https://json-schema.org/draft/2020-12",
        "Machine-verifiable JSON data contracts and validation vocabularies.",
        ("api", "json", "integration", "event", "data"),
        False,
        ("API specification", "event specification", "data contracts"),
        "CONDITIONAL",
        ("Use machine-verifiable schemas with explicit required fields, constraints, and vocabulary selection.",),
    ),
    StandardReference(
        "UML", "OMG Unified Modeling Language", "2.5.1",
        "https://www.omg.org/spec/UML",
        "Standardized structural and behavioral modeling where a UML view communicates a real stakeholder concern.",
        ("complex", "workflow", "state", "sequence", "component", "domain"),
        False,
        ("use cases", "sequence diagrams", "state models", "domain model"),
        "CONDITIONAL",
        ("Select the smallest diagram type that answers the concern and maintain identifiers across text and diagrams.",),
    ),
    StandardReference(
        "BPMN", "OMG Business Process Model and Notation", "2.0.2",
        "https://www.omg.org/spec/BPMN/2.0.2/",
        "Business-process and collaboration modeling for multi-role or multi-organization workflows.",
        ("workflow", "approval", "handoff", "business process", "multi-role", "orchestration"),
        False,
        ("workflow specification", "process diagrams"),
        "CONDITIONAL",
        ("Represent events, activities, decisions, handoffs, exceptions, and compensation only when the process evidence supports them.",),
    ),
    StandardReference(
        "ISO_42001", "ISO/IEC 42001", "2023",
        "https://www.iso.org/standard/42001",
        "AI management-system governance, risks, opportunities, and continual improvement.",
        ("ai", "machine learning", "model", "llm", "prediction", "generative"),
        False,
        ("AI governance plan", "AI risk register", "operations"),
        "CONDITIONAL",
        ("Record AI objectives, accountability, risk, data/model lifecycle, monitoring, incidents, and continual improvement without claiming certification.",),
    ),
    StandardReference(
        "NIST_AI_RMF", "NIST AI Risk Management Framework", "1.0 (revision in progress)",
        "https://www.nist.gov/itl/ai-risk-management-framework",
        "Trustworthy and responsible AI risk management across the AI lifecycle.",
        ("ai", "machine learning", "model", "llm", "prediction", "generative"),
        False,
        ("AI risk assessment", "test strategy", "monitoring plan"),
        "CONDITIONAL",
        ("Organize AI risk work across Govern, Map, Measure, and Manage and state unresolved harms or evaluation thresholds.",),
    ),
)


def applicable_standards(project: Any, confirmed_brief: dict[str, Any]) -> list[dict[str, str]]:
    """Return reviewable applicability decisions without asserting compliance."""
    project_values = (
        getattr(project, "name", ""), getattr(project, "description", ""),
        getattr(project, "type", ""),
    )
    brief_values = _context_values(confirmed_brief)
    context = " ".join((*map(str, project_values), *brief_values)).lower()
    selected: list[dict[str, str]] = []
    for standard in REGISTRY:
        matched = [term for term in standard.applies_to if term != "all" and term in context]
        if not standard.always and not matched:
            continue
        reason = (
            "Baseline documentation quality control for every generated package."
            if standard.always
            else "Applicable context signal(s): " + ", ".join(sorted(set(matched))) + "."
        )
        selected.append({
            "key": standard.key,
            "name": standard.name,
            "version": standard.version,
            "official_url": standard.official_url,
            "purpose": standard.purpose,
            "applicability_reason": reason,
            "checked_on": CHECKED_ON,
            "claim": "Guidance applied; no certification or legal-compliance claim.",
            "document_types": ", ".join(standard.document_types),
            "recommendation": standard.recommendation,
            "rules": " | ".join(standard.rules),
        })
    return selected


def registry_payload() -> list[dict[str, str]]:
    return [
        {
            "key": item.key,
            "name": item.name,
            "version": item.version,
            "official_url": item.official_url,
            "purpose": item.purpose,
            "checked_on": CHECKED_ON,
            "document_types": ", ".join(item.document_types),
            "conditions": ", ".join(item.applies_to),
            "recommendation": item.recommendation,
            "rules": " | ".join(item.rules),
        }
        for item in REGISTRY
    ]


def _context_values(value: Any) -> list[str]:
    """Flatten bounded brief values for applicability without treating them as instructions."""
    if isinstance(value, dict):
        return [text for item in value.values() for text in _context_values(item)]
    if isinstance(value, list):
        return [text for item in value[:100] for text in _context_values(item)]
    if isinstance(value, (str, int, float, bool)):
        return [str(value)]
    return []
