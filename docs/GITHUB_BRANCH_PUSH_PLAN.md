# GitHub Branch and Push Plan

This historical plan covers the full working tree as inspected on 2026-07-26. Its proposed split was executed on 2026-08-02 using focused commits and branch pushes; no pull requests were opened.

## Current inventory

| Status | Count | Scope |
| --- | ---: | --- |
| Modified | 52 | 19 backend files and 33 frontend files |
| Deleted | 1 | `frontend/src/i18n/en.json.bak` |
| Untracked | 173 | 136 backend, 22 AI-service, 7 frontend, 4 docs, 3 GitHub workflow, and `.gitignore` |
| Staged | 0 | None |

The current branch is `main`; the configured remote is `origin` (`Pyra-Flow/Velocira`).

## Proposed branch sequence

Create short-lived branches from the latest `origin/main` and push each focused branch. Merge in this order so every later branch can be rebased on its dependency.

| Order | Branch | Commit scope | Depends on |
| ---: | --- | --- | --- |
| 1 | `chore/repository-hygiene-ci` | Root `.gitignore`, backend/AI Docker ignore files, Dockerfiles, and `.github` CI/security/dependency workflows | None |
| 2 | `feat/ai-generation-service` | Entire `ai-service/` application, tests, requirements, README, and environment template | 1 |
| 3 | `feat/backend-generation-platform` | Generation domain, AI client, observability, project lifecycle updates, security/config changes, and migrations `V3`–`V5` | 1, 2 |
| 4 | `feat/discovery-interview` | Interview domain, discovery client/planner, integration test, and migration `V6` | 3 |
| 5 | `feat/governed-rag-srs` | Knowledge/SRS domain, retrieval integration, tests, and migrations `V7`–`V8` | 4 |
| 6 | `feat/documentation-packages` | Documentation package/export domain, tests, operations runbooks, and migration `V9` | 5 |
| 7 | `feat/frontend-mvp-workspaces` | Frontend workspace UI, API/store integration, dependency updates, shared UI/page changes, and removal of `en.json.bak` | 3–6 |
| 8 | `docs/mvp-product-and-release-guide` | Product blueprint, expert-review/RAG/release docs, and this push plan | 6, 7 |

## File allocation rules

- Keep each migration with the backend feature that requires it; do not reorder or squash the `V3`–`V9` migration sequence.
- Keep `.env.example` files only in the relevant feature branch after confirming they contain placeholders, never real credentials.
- Include `frontend/package.json` and `frontend/package-lock.json` in the same frontend branch.
- Include the deletion of `frontend/src/i18n/en.json.bak` in the frontend branch only after verifying it is obsolete and no imports reference it.
- Do not add ignored local files such as `backend/.env`, `ai-service/.env`, `frontend/.env.local`, build output, or `tmp/` exports.

## Pre-commit checks for every branch

1. Start from an updated `main` and create the named branch.
2. Stage only the files allocated to that branch; review `git diff --cached --check` and `git diff --cached --name-status`.
3. Scan staged content for credentials and private keys. Stop and remove/redact any secret found before committing.
4. Run the relevant verification:
   - AI service: `pytest` from `ai-service/`.
   - Backend: Maven tests from `backend/` (at least the affected unit/integration tests).
   - Frontend: `npm run lint` and `npm run build` from `frontend/`.
   - CI/config/docs: validate YAML and review links/commands.
5. Commit using one focused conventional message and push the branch. Do not open a PR unless explicitly requested.
6. Record validation results and known follow-ups in the delivery summary. Merge only after checks pass.

## Safe execution pattern (for later use)

Because all changes currently coexist in one working tree, create each branch, selectively stage its allocated paths, commit, and then reset only the staged index before preparing the next branch. Do not use a destructive working-tree reset. After a branch merges, rebase each dependent branch onto the updated `main` before opening or updating its PR.

Suggested commit subjects:

- `chore: add repository hygiene and CI workflows`
- `feat(ai): add governed generation service`
- `feat(backend): add generation platform`
- `feat(interview): add discovery interview workflow`
- `feat(knowledge): add governed RAG and SRS workflow`
- `feat(docs): add documentation package exports`
- `feat(frontend): add MVP project workspaces`
- `docs: add MVP operating and release guides`

## Final release gate

Before merging the final branch, run the full backend, AI-service, and frontend suites together; verify migration compatibility on a clean database; confirm the frontend can reach the configured backend and AI-service endpoints; and confirm no `.env` or generated export was accidentally staged.
