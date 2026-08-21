# Enterprise documentation-generation upgrade

Date: 2026-08-16

## Outcome

Velocira now generates one governed SRS model and derives a synchronized
16-artifact package instead of asking a model for a short generic document.
The upgrade is designed to make quality measurable: grounding, requirement
quality, acceptance coverage, traceability, unresolved decisions, model
provenance, syntax validation and rendered-output checks are recorded. This is
not a claim that every output will outperform every document written by a
particular model or expert; approval still belongs to project stakeholders.

## Original pipeline weaknesses

- The governed SRS was drafted in one 4,096-token JSON request and stored mainly
  scope plus requirements, so long outputs could not be complete.
- Generic document generation and the five-artifact package lost requirement
  detail and did not cover architecture, security, testing, deployment,
  operations or user guidance adequately.
- Use cases used generic steps; ERDs carried entity names and placeholder IDs;
  the OpenAPI exporter fabricated requirement-derived paths.
- There was no standards applicability registry, workstream/section
  provenance, global requirement validator, cross-document package validator,
  or exhaustive-depth control.
- The backend AI read timeout was 30 seconds, too short for deliberate Pro
  generation.
- Markdown tables, blockquotes and emphasis were not interpreted by the visual
  export renderer, producing literal markup in DOCX/PDF.

## Implemented architecture

1. The canonical brief and owner-approved evidence remain the only project
   fact sources. Evidence is delimited as untrusted data.
2. A current-source registry creates a project-specific applicability profile
   and original structural rules without copying standards.
3. `STANDARD` uses one bounded Pro drafting pass. `EXHAUSTIVE` uses four Pro
   workstreams: product; data and interfaces; trust; quality and operations.
4. The deterministic compiler normalizes the workstreams into a versioned SRS
   with document control, manifest, 12 section contracts, requirements,
   workflows, quality scenarios, risks, decisions, standards and diagrams.
5. Validators reject malformed/duplicate IDs, duplicate or non-atomic
   statements, vague claims, unsupported numeric targets, secrets, missing
   sources, missing acceptance criteria and incomplete coverage.
6. Spring persists the immutable SRS, normalized requirements and evidence
   anchors. The package compiler derives 16 artifacts plus trace links from the
   same snapshot.
7. The renderer produces Markdown, DOCX, PDF, ZIP, OpenAPI JSON/YAML,
   PlantUML, Mermaid and styled SVG/PNG diagrams. Tables repeat headers in DOCX;
   pages use consistent typography, margins, headers, footers and numbering.
8. The frontend exposes Standard and Exhaustive depth and describes the
   four-workstream behavior. Final SRS generation remains on
   `gemini-3.1-pro-preview`; discovery remains on `gemini-3.6-flash` with
   `gemini-3.5-flash` as retryable discovery fallback only.

## Package catalog

The current canonical package contains SRS, BRD, architecture, use cases, C4
context, workflows, data dictionary, ERD, OpenAPI, security, test plan,
deployment, operations, user manual, risk register and traceability. The
manifest also records why a candidate document is required, review-required or
not applicable. Further specialized documents can be added as new compiler
views over the same canonical model rather than independent model calls.

## Representative verification fixture

`ClinicFlow` is a deterministic, owner-grounded clinic-booking fixture—not a
live Gemini benchmark. It deliberately leaves recovery targets, topology,
retention and unconfirmed integrations unresolved. The package includes 12
typed requirements, 100% acceptance-criteria coverage, 100% traceability
coverage, one workflow with alternate/failure/recovery content, one measurable
quality-scenario structure, risk and decision registers, a standards
applicability record, and C4/workflow/ER diagrams. The OpenAPI 3.1.1 artifact
contains zero invented endpoints and carries unresolved API requirements until
method/path evidence exists.

The Technical/Ocean/Compact PDF renders to 42 pages after the final renderer
fix (cover and contents included). The three editable and fixed-layout style
samples, canonical JSON, Markdown source and validation metrics are written to
`outputs/documentation-enterprise-upgrade/` by the integration test.

## Verification performed

- AI tests: 51 passed, covering the five requested project classes,
  applicability metadata, grounded compilation, requirement validation,
  prompt-injection resistance and exact Pro/Flash routing.
- Backend documentation integration tests: package composition, validation,
  traceability, non-fabricating OpenAPI, ZIP contents and three DOCX/PDF styles.
- Frontend: TypeScript `--noEmit` validation.
- PDF: every page of the representative technical export was rasterized and
  visually inspected. The first pass exposed literal Markdown; the shared
  DOCX/PDF renderer was corrected and the package was regenerated.
- DOCX: OOXML/package structure and content checks plus paired PDF visual QA.
  Native Word/LibreOffice visual rendering was unavailable in this environment,
  so a native DOCX pagination comparison remains a documented limitation.

## Remaining limitations and next improvements

- The AI service generates four resumable-in-principle workstreams, but a
  failed HTTP request still restarts the SRS operation because workstream
  checkpoints are not yet committed independently by Spring. Add a durable
  `srs_generation_checkpoints` table and one internal workstream endpoint before
  claiming section-level crash recovery.
- The 25,000–50,000 word SRS range is an exhaustive-mode target when evidence
  supports it, not a guaranteed minimum. The deterministic fixture is smaller
  by design and no live paid Gemini call was made during this verification.
- Container/component, sequence, state, deployment and threat-boundary diagrams
  should be promoted to first-class artifact types as their underlying facts
  become available.
- Add an automatic native Word rendering job in CI and regression-image checks
  for representative templates.
- Add a contradiction model pass with durable reviewer dispositions after the
  deterministic contradiction checks.

