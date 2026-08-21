# Velocira documentation standards registry

Checked: 2026-08-16

This registry is an applicability and document-structure aid. It does not copy
paid standards, replace professional assessment, prove conformance, or grant a
certification. Velocira stores official-source metadata and original rules
derived from publicly visible summaries. A project owner must confirm legal,
regulatory, contractual, geographic, and certification obligations.

| Standard or practice | Current reference | Official source | Used for | Applicability and original Velocira rule |
|---|---:|---|---|---|
| ISO/IEC/IEEE 29148 | 2018; confirmed 2024 | [ISO](https://www.iso.org/standard/72089.html) | SRS, requirements, traceability | Baseline. Require uniquely identified, necessary, unambiguous, verifiable, source-linked requirements. |
| ISO/IEC/IEEE 15288 | 2023 | [ISO](https://www.iso.org/standard/81702.html) | Lifecycle, stakeholder, verification and transition plans | Recommended for system-level or multi-component products. Connect stakeholder needs through verification, validation, transition and operation. |
| ISO/IEC/IEEE 12207 | 2026 | [ISO](https://www.iso.org/standard/90219.html) | Software lifecycle and maintenance | Recommended for software products. Make development, delivery, operation, maintenance and retirement responsibilities visible. |
| ISO/IEC/IEEE 42010 | 2022 | [ISO](https://www.iso.org/standard/74393.html) | Architecture descriptions | Required when architecture is applicable. Record stakeholders, concerns, viewpoints, views, decisions and rationale without inventing topology. |
| ISO/IEC 25010 | 2023 | [ISO](https://www.iso.org/standard/78176.html) | Quality requirements | Baseline. Consider relevant product-quality characteristics and turn selected qualities into measurable scenarios or unresolved decisions. |
| ISO/IEC/IEEE 29119-2 | 2021 | [ISO](https://www.iso.org/standard/79428.html) | Test strategy and plans | Baseline for test artifacts. Connect test conditions, environments, evidence, entry/exit criteria and outcomes to requirements. |
| RFC 2119 and RFC 8174 | 1997 / 2017 | [IETF RFC 2119](https://www.rfc-editor.org/rfc/rfc2119), [IETF RFC 8174](https://www.rfc-editor.org/rfc/rfc8174) | Normative language | Baseline. Use uppercase MUST, SHOULD and MAY only for deliberate requirement strength and define the convention. |
| C4 model | Current site | [C4](https://c4model.com/) | Context, container and component views | Recommended for software architecture. Show only evidenced people, systems, containers and responsibilities at an appropriate abstraction level. |
| arc42 | Current site | [arc42](https://docs.arc42.org/home/) | Architecture document | Recommended for non-trivial systems. Structure goals, constraints, context, solution strategy, building blocks, runtime, deployment, decisions, risks and quality scenarios. |
| IREB CPRE | Current glossary and syllabus | [IREB](https://cpre.ireb.org/en/downloads-and-resources/glossary) | Requirements practice | Recommended. Separate sources, needs, assumptions, conflicts and validation; preserve a consistent project vocabulary. |
| BABOK | Version 3 | [IIBA](https://www.iiba.org/career-resources/a-business-analysis-professionals-foundation-for-success/babok/) | Business analysis | Recommended for stakeholder-heavy products. Trace business need, stakeholder value, requirements, decisions and solution evaluation. |
| Volere | Edition 20 public template | [Volere](https://www.volere.org/templates/volere-requirements-specification-template/) | Requirements discovery and structure | Optional structural inspiration. Record fit criteria, constraints, assumptions, risks and project drivers using original Velocira wording. |
| WCAG | 2.2 | [W3C](https://www.w3.org/TR/WCAG22/) | Accessibility requirements and testing | Recommended for user-facing products; owner confirms obligation and target level. Make relevant success criteria testable and include non-visual interaction and error recovery. |
| ISO 9241-210 | 2019; confirmed 2025 | [ISO](https://www.iso.org/standard/77520.html) | Human-centred design | Recommended for interactive systems. Connect user context, tasks, accessibility, evaluation and iterative design decisions. |
| OWASP ASVS | 5.0.0 | [OWASP](https://owasp.org/www-project-application-security-verification-standard/) | Application-security requirements | Recommended for web/API products. Select project-relevant verification controls; never imply ASVS verification without evidence. |
| OWASP SAMM | 2.0 | [OWASP](https://owaspsamm.org/model/) | Secure delivery maturity | Recommended for organizations formalizing secure development. Record governance, design, implementation, verification and operations practices as recommendations. |
| OWASP MASVS | Current site | [OWASP](https://mas.owasp.org/MASVS/) | Mobile application security | Conditional on mobile scope. Derive testable storage, cryptography, authentication, network, platform, code and resilience requirements. |
| NIST CSF | 2.0 | [NIST](https://www.nist.gov/cyberframework) | Cyber-risk organization | Recommended for material cyber risk. Map applicable outcomes across Govern, Identify, Protect, Detect, Respond and Recover. |
| NIST SSDF, SP 800-218 | 1.1 | [NIST](https://csrc.nist.gov/pubs/800/218/final) | Secure software lifecycle | Recommended for software delivery. Add reviewable preparation, software protection, secure production and vulnerability-response activities. |
| ISO/IEC 27001 | 2022 | [ISO](https://www.iso.org/standard/27001) | Information-security management | Conditional on sensitive data, enterprise controls or a confirmed obligation. Record applicability and ownership; do not claim certification. |
| ISO/IEC 27002 | 2022 | [ISO](https://www.iso.org/standard/75652.html) | Security control guidance | Conditional with security exposure. Use controls as a risk-based review catalog, not a copied checklist or certification claim. |
| ISO/IEC 27701 | 2025 | [ISO](https://www.iso.org/standard/27701) | Privacy information management | Conditional on personal data/privacy scope. Identify roles, lawful-basis decision owners, data-subject flows, processor boundaries and evidence gaps without inventing law. |
| ISO 31000 | 2018 | [ISO](https://www.iso.org/standard/65694.html) | Risk management | Baseline for material projects. Record cause, event, consequence, evidence, treatment, owner, trigger and residual decision; leave unsupported scores unresolved. |
| ISO 22301 | 2019 + Amd 1:2024 | [ISO](https://www.iso.org/standard/75106.html) | Business continuity | Conditional on critical operations or recovery obligations. Require business-impact review before setting recovery targets. |
| ISO/IEC 20000-1 | 2018 + Amd 1:2024 | [ISO](https://www.iso.org/standard/70636.html) | Service management and operations | Conditional on operated services. Cover service ownership, incidents, changes, configuration, suppliers and continual improvement without claiming conformity. |
| ISO/IEC 42001 | 2023 | [ISO](https://www.iso.org/standard/42001) | AI management systems | Conditional on AI-enabled products. Record AI purpose, oversight, impact, data, evaluation, monitoring, change and incident responsibilities. |
| NIST AI RMF | 1.0 | [NIST](https://www.nist.gov/itl/ai-risk-management-framework) | AI risk | Conditional on AI. Organize evidence and decisions across Govern, Map, Measure and Manage; distinguish model behavior from confirmed product guarantees. |
| OpenAPI Specification | 3.1.1 used by exporter | [OpenAPI Initiative](https://spec.openapis.org/oas/) | HTTP API contracts | Conditional on confirmed HTTP APIs. Emit operations only when method and path are evidenced; otherwise emit explicit unresolved API requirements. |
| AsyncAPI Specification | 3.x | [AsyncAPI Initiative](https://www.asyncapi.com/docs/reference/specification/latest) | Event and message contracts | Conditional on confirmed asynchronous interfaces. Define channels, operations, messages, schemas, delivery semantics and failure handling without inventing a broker. |
| JSON Schema | Draft 2020-12 | [JSON Schema](https://json-schema.org/draft/2020-12) | Data and payload schemas | Conditional on structured interfaces/data. Express validation, required properties and reusable vocabularies when fields are confirmed. |
| UML | 2.5.1 | [OMG](https://www.omg.org/spec/UML) | Use cases, sequences, states and components | Optional when a diagram reduces ambiguity. Every element must trace to confirmed or explicitly derived context. |
| BPMN | 2.0.2 | [OMG](https://www.omg.org/spec/BPMN/2.0.2/) | Cross-role business workflows | Conditional on workflow-heavy or multi-party processes. Model actors, events, decisions, exceptions and recovery only when supported. |

## Applicability decision model

The implementation in `ai-service/app/standards.py` evaluates project type,
industry, complexity, roles, evidence, integrations, data sensitivity,
accessibility, AI, payments, healthcare, children, geography, criticality and
deployment context. Each registry item produces a reviewable decision with its
reason, status, affected document types, recommendation, derived rules,
official source and checked date. Unknown regulatory exposure remains a
decision item; keyword matches never become a legal conclusion.

