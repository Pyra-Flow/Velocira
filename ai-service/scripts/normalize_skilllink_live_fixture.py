"""Normalize the generated SkillLink fixture with confirmed canonical names.

The live model owns requirements and narrative content. This bounded fixture
step only separates canonical vocabulary from prose and encodes entity
relationships already confirmed by the SkillLink brief, so the documentation
compiler can render honest C4, workflow, and ERD views.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def _entity_id(name: str) -> str:
    return "ENTITY_" + re.sub(r"[^A-Z0-9]+", "_", name.upper()).strip("_")


def _align_confirmed_api_operations(brief: dict, artifact: dict) -> int:
    """Bind each API requirement to the exact route named by its own source detail."""
    brief_text = json.dumps(brief, ensure_ascii=False)
    pattern = re.compile(
        r"(?i)\b(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+"
        r"(/[A-Za-z0-9._~!$&'()*+,;=:@%{}/-]+)\s+"
        r"(?:uses\s+)?operation\s+ID\s+([A-Za-z][A-Za-z0-9_.-]{2,120})"
    )
    contracts = [
        (match.group(1).upper(), match.group(2), match.group(3).rstrip(".,;:"))
        for match in pattern.finditer(brief_text)
    ]
    aligned = 0
    for requirement in artifact.get("requirements", []):
        if requirement.get("type") != "API":
            continue
        local_source = " ".join((
            str(requirement.get("title", "")),
            str(requirement.get("statement", "")),
            str(requirement.get("source_detail", "")),
        )).casefold()
        matches = [
            contract for contract in contracts
            if re.search(
                rf"\b{re.escape(contract[0])}\s+{re.escape(contract[1])}(?![A-Za-z0-9._~!$&'()*+,;=:@%{{}}/-])",
                local_source,
                re.IGNORECASE,
            )
            or contract[2].casefold() in local_source
        ]
        if len(matches) != 1:
            raise ValueError(
                f"API requirement {requirement.get('id', '<unknown>')} does not identify exactly one confirmed contract"
            )
        method, path, operation_id = matches[0]
        requirement["api_operation"] = {
            "path": path,
            "method": method,
            "operation_id": operation_id,
        }
        aligned += 1
    return aligned


def main(path_value: str) -> None:
    path = Path(path_value)
    wrapper = json.loads(path.read_text(encoding="utf-8"))
    brief = wrapper["brief"]
    artifact = wrapper["artifact"]

    brief["userCapabilities"] = brief.get("userCapabilities", brief.get("users", ""))
    brief["entityNarrative"] = brief.get("entityNarrative", brief.get("entities", ""))
    brief["integrationBehavior"] = brief.get(
        "integrationBehavior", brief.get("integrations", "")
    )
    brief["users"] = "Customer; Professional; Support Operator"
    entities = [
        "Customer Account",
        "Professional Profile",
        "Service",
        "Qualification",
        "Rating Summary",
        "Contact Verification Status",
        "Price Display",
        "Availability Window",
        "Booking",
        "Slot Hold",
        "Confirmation Decision",
        "Notification Attempt",
        "Audit Event",
    ]
    brief["entities"] = "; ".join(entities)
    brief["integrations"] = "Transactional Email Gateway"
    relationships = [
        {"from": "Customer Account", "to": "Booking", "cardinality": "ONE_TO_MANY", "label": "initiates"},
        {"from": "Professional Profile", "to": "Booking", "cardinality": "ONE_TO_MANY", "label": "receives"},
        {"from": "Professional Profile", "to": "Service", "cardinality": "ONE_TO_MANY", "label": "offers"},
        {"from": "Professional Profile", "to": "Qualification", "cardinality": "ONE_TO_MANY", "label": "records"},
        {"from": "Professional Profile", "to": "Availability Window", "cardinality": "ONE_TO_MANY", "label": "owns"},
        {"from": "Booking", "to": "Slot Hold", "cardinality": "ONE_TO_ZERO_OR_ONE", "label": "reserves"},
        {"from": "Booking", "to": "Confirmation Decision", "cardinality": "ONE_TO_ZERO_OR_ONE", "label": "receives"},
        {"from": "Booking", "to": "Notification Attempt", "cardinality": "ONE_TO_MANY", "label": "records"},
        {"from": "Booking", "to": "Audit Event", "cardinality": "ONE_TO_MANY", "label": "records"},
    ]
    brief["entityRelationships"] = relationships
    aligned_api_operations = _align_confirmed_api_operations(brief, artifact)

    terms = [
        *(("Role", name) for name in ("Customer", "Professional", "Support Operator")),
        *(("Domain entity", name) for name in entities),
        ("External system", "Transactional Email Gateway"),
    ]
    artifact["definitions"] = [
        {
            "id": f"TERM-{index:03d}",
            "category": category,
            "title": name,
            "description": f"Confirmed {category.lower()} in the SkillLink first-release domain.",
            "status": "CONFIRMED",
            "owner": "Project owner",
            "source_detail": "Canonical confirmed project brief.",
        }
        for index, (category, name) in enumerate(terms, 1)
    ]

    c4 = """flowchart LR
  SKILLLINK["SkillLink"] --> EMAIL["Transactional Email Gateway"]
  CUSTOMER["Customer"] --> SKILLLINK
  PROFESSIONAL["Professional"] --> SKILLLINK
  SUPPORT["Support Operator"] --> SKILLLINK"""
    workflow = """flowchart TD
  SELECT["Customer selects a service and available slot"] --> HOLD["Create idempotent booking request and 5-minute Slot Hold"]
  HOLD --> DECISION{"Professional decision before expiry?"}
  DECISION --> CONFIRM["Professional confirms"]
  CONFIRM --> CHECK{"Buffered overlap exists?"}
  CHECK --> CONFIRMED["No overlap: set CONFIRMED and notify both actors"]
  CHECK --> CONFLICT["Overlap: set CONFLICT and block later confirmation"]
  DECISION --> DECLINED["Professional declines: set DECLINED and release slot"]
  DECISION --> EXPIRED["No decision in 5 minutes: set EXPIRED and release slot"]
  CONFIRMED --> HISTORY["Retain status and audit history"]
  DECLINED --> HISTORY
  CONFLICT --> HISTORY
  EXPIRED --> HISTORY"""

    entity_ids = {name: _entity_id(name) for name in entities}
    erd_lines = ["erDiagram"]
    for entity in entities:
        erd_lines.extend((f"  {entity_ids[entity]} {{", "    uuid id PK", "  }"))
    connectors = {"ONE_TO_MANY": "||--o{", "ONE_TO_ZERO_OR_ONE": "||--o|"}
    for relationship in relationships:
        erd_lines.append(
            f"  {entity_ids[relationship['from']]} "
            f"{connectors[relationship['cardinality']]} "
            f"{entity_ids[relationship['to']]} : {relationship['label']}"
        )
    diagrams = {
        "C4_CONTEXT": (c4, "Confirmed actors and the transactional email boundary."),
        "WORKFLOW": (workflow, "Confirmed booking, conflict, decision, expiry, and history paths."),
        "ERD": (
            "\n".join(erd_lines),
            "Confirmed entities and cardinalities from the structured project brief.",
        ),
    }
    for diagram in artifact.get("diagrams", []):
        if diagram.get("type") not in diagrams:
            continue
        diagram["source"], diagram["rationale"] = diagrams[diagram["type"]]
        diagram["status"] = "CONFIRMED"

    artifact.setdefault("generation_manifest", {})["canonical_normalization"] = {
        "terminology_source": "structured confirmed brief",
        "entity_relationship_count": len(relationships),
        "api_operations_aligned_by_local_source": aligned_api_operations,
        "diagram_types_refreshed": ["C4_CONTEXT", "WORKFLOW", "ERD"],
    }
    path.write_text(json.dumps(wrapper, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "path": str(path),
                "roles": 3,
                "entities": len(entities),
                "integrations": 1,
                "relationships": len(relationships),
                "api_operations_aligned": aligned_api_operations,
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: normalize_skilllink_live_fixture.py <wrapper.json>")
    main(sys.argv[1])
