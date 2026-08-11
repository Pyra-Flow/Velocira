# Design QA

## Comparison target

- Source visual truth: the six attached reference boards supplied in this task:
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-e0c8877f-5d27-4550-83c8-bb1bc7aee6da.png` (public marketing)
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-7122f783-1c95-40d4-942d-e2c1b3e094d9.png` (authentication)
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-5693b240-251a-4e43-b12d-bf2fc6fa3cdb.png` (workspace dashboard)
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-998874c9-e3c2-4c73-a714-0c47403a19e3.png` (new project intake)
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-dcdfb36d-cebb-43e7-9835-abf9b1980dda.png` (project and documentation workspace)
  - `C:/Users/Tolis/AppData/Local/Temp/codex-clipboard-a49c1829-52eb-41a9-88a0-55e2e3379864.png` (admin and settings)
- Intended viewport: desktop, 1440 × 1024 CSS px, dark and light themes.
- Intended state: authenticated workspace with project data; public and authentication landing states.
- Implementation screenshots captured at 1440 × 1024:
  - `artifacts/ui-review/public-home-final.png`
  - `artifacts/ui-review/login-final.png`
- Density normalization: the public hero was widened to the desktop reference frame, with the brief-to-package board aligned to the top of the hero. The authentication form was moved into the reference hierarchy: email-first, primary action, then Google continuation and a bottom-anchored protocol.

## Evidence gathered

- `npm run typecheck`: passed.
- `npm run lint`: passed with two existing `@next/next/no-img-element` warnings in `DocumentationPackageWorkspace.tsx`.
- `docker compose build frontend`: passed; all 28 routes compiled successfully.
- The rebuilt Docker frontend is healthy and responding on `http://localhost:3000`.
- Static HTTP checks confirmed the deployed public workflow board and authentication protocol markup.
- `graphify update .`: retried after the final edits, but the graph command exceeded the local 60-second command limit before reporting completion.

## Findings

- [P1] Protected workspace routes still require an authenticated session for browser QA.
  - Location: dashboard, projects, intake, project-detail, documentation, admin, and settings.
  - Evidence: the current browser tab is logged out; no test credentials were supplied or used.
  - Impact: those screens cannot be compared in their populated state yet.
  - Fix: sign in through the visible local browser, then capture and complete the remaining route and light-theme review.

## Implementation checklist

- [x] Added a denser technical surface system and true light-theme tokens to the shared frontend stylesheet.
- [x] Updated the public four-stage flow, authentication, dashboard table, project library, intake, briefing workspace, admin users, and settings while preserving existing routes and behaviors.
- [x] Added the reference-style context, next-questions, and evidence rail to the existing new-project form.
- [x] Preserved pre-existing documentation workspace changes and supplied a dark-mode counterpart instead of overwriting them.
- [x] Passed static type, lint, and Docker production-build checks.
- [x] Captured and corrected the public-home and login layouts at desktop viewport.
- [ ] Capture the authenticated workspace routes and both light-theme states after sign-in.

## Follow-up polish

- Verify the generated reference density on populated project and admin states using a seeded local account.
- Replace the two documented image lint warnings only if the existing package render behavior permits `next/image` without changing output behavior.

final result: blocked
