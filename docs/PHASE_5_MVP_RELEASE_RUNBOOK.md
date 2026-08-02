# Phase 5 MVP release runbook

This runbook covers the linked-documentation MVP: an approved discovery brief and evidence-grounded SRS become one reviewable package containing use cases, ERD source, an OpenAPI contract, traceability, and immutable exports.

## Local startup

1. Copy `.env.example` to `.env` at the repository root and fill the database, JWT, internal-service-token, Gemini, and Qdrant values. Do not commit this file.
2. From the repository root, start the complete stack:

   ```powershell
   docker compose up --build --wait
   ```

   This starts PostgreSQL, Qdrant, the internal AI service, Spring API, and browser client. The expected health URL is `http://localhost:8000/ready` and Qdrant is available at `http://localhost:6333/dashboard`.

Open `http://localhost:3000`. The public API is on `http://localhost:8080`.

## MVP user journey

1. Create a project and complete the discovery interview. Confirm the generated project brief.
2. Upload and approve project evidence, then generate an SRS using an internal standards profile.
3. Review the SRS. Only an **approved** SRS can seed a linked package.
4. On the project page, open **Linked documentation package**, choose the approved SRS, and generate the package.
5. Review the generated SRS, use-case narratives and PlantUML source, ERD and Mermaid source, OpenAPI JSON, and traceability matrix. Resolve issues in the source brief/SRS and create a new package version when needed.
6. Approve the package. Export ZIP, Markdown, PDF, DOCX, OpenAPI JSON/YAML, UML source, or ERD source. Each export is persisted with its checksum and remains immutable.

## Release gates

Do not call the MVP released until all of these are evidenced in the release record:

- Run backend tests, including `DocumentationPackageIntegrationTest`; it checks approved-SRS generation, trace links, OpenAPI YAML, a readable PDF, a readable DOCX, and an immutable ZIP.
- Run `npm run lint`, `npx tsc --noEmit`, and a browser accessibility smoke test. Check keyboard access, visible focus, labels, headings, error messages, and download actions on the project page.
- Deploy the exact backend, frontend, AI-service, PostgreSQL, and Qdrant versions to a staging environment with staging-only secrets. Check `/actuator/health`, `/health`, `/ready`, database migration status, and an authenticated export download.
- Execute the full browser path: create project -> interview -> approve brief -> approve evidence -> generate and approve SRS -> generate package -> review -> approve -> download ZIP. Record the correlation IDs and package/export checksums.
- Run project-isolation checks with two accounts: one account must never list, read, generate, approve, export, or download another account's packages.
- Have pilot users review at least one package. Log findings as requirement, traceability, wording, diagram, OpenAPI, or export defects; fix release blockers before launch.
- Publish a real support channel and ownership rotation before inviting external users. Until then, keep the existing pre-release support page rather than claiming that messages are delivered.

## Operational response

- If a package is invalid, do not approve or export it. Correct the approved SRS/brief and generate a new immutable package version.
- If an export fails, retain the correlation ID, package ID, artifact checksums, and export checksum in the incident record. Do not replace a successful export's bytes.
- If another tenant's data is observed, revoke affected sessions, preserve audit records, block the release, and investigate the owner/project query boundary before resuming.
- Back up PostgreSQL and Qdrant before every staging cutover; rehearse restore using a non-production copy.

## Scope boundary

This is the MVP release path. Live collaboration, code import, broad third-party integrations, billing, and compliance-pack expansion remain later phases.
