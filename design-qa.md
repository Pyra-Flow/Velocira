# Design QA

## Comparison target

- Source visual truth: `artifacts/ui-recolor-redesign-2026-08-24/selected-direction.png`.
- Source raster: 1487 × 1058 px.
- Intended implementation viewport: 1440 × 1024 CSS px at device pixel ratio 1.
- Implementation capture: `artifacts/ui-recolor-redesign-2026-08-24/question-light-final.png` (1431 × 1018 browser-content pixels; the browser reserves the remaining pixels for its scrollbar/chrome).
- Matched state: authenticated project workspace, light theme, question flow, page scrolled to the top.
- Combined comparison evidence: `artifacts/ui-recolor-redesign-2026-08-24/comparison-pass-4.png`.
- Focused comparison reason: the question workspace is the selected direction's defining screen and contains the system-wide shell, navigation, stage tracker, typography, form rows, progress, theme palette, and primary action treatment.

## Comparison history

1. Pass 1 identified an extra project-context row that pushed the core question too far down.
2. Pass 2 merged project context and the lifecycle tracker into one desktop header band while retaining the stacked mobile layout.
3. Pass 3 exposed a scrolled browser capture rather than a true top-of-page state.
4. Pass 4 used the matched top-of-page state and confirmed the final layout, palette, continuous answer rows, rail proportions, typography hierarchy, and action treatment.

## Route and state coverage

- Public landing: desktop and mobile light theme.
- Authentication: desktop light theme, including corrected white-on-dark brand treatment.
- Project library: authenticated desktop light theme.
- New project: desktop and mobile light theme.
- Project briefing: authenticated desktop light theme.
- Guided questions: authenticated desktop and mobile, light and dark themes.
- Settings: authenticated desktop light theme, including the neutral card border with red status rail.
- Documentation, administration, review, and shared UI primitives inherit the same semantic token and component overrides.

## Interaction coverage

- Signed in through the visible local browser and reached the populated project library.
- Opened and closed mobile workspace navigation; verified Projects, New project, Settings, theme, and sign-out actions.
- Switched light ↔ dark through the shipped theme controls on desktop and mobile.
- Selected a suggested answer and confirmed the selected state remained controlled by the form.
- Expanded “Why we’re asking.”
- Opened the project briefing and returned through “Begin questions.”
- Verified the primary save action, defer menu actions, project brief action, and mobile controls remain available.
- No horizontal overflow at 1440 × 1024 or 390 × 844 in either theme.

## Accessibility and runtime checks

- Core controls remain semantic buttons, links, radios, details/summary disclosures, labeled inputs, and progress indicators.
- Focus styling uses a consistent red 3 px ring; reduced-motion behavior remains present in the existing components.
- Contrast checks for the new palette: `#080e16` on `#f81422` is 4.71:1, light-theme accent ink `#d80f1b` on `#f5f5f7` is 4.82:1, and `#f5f5f7` on `#080e16` is 17.78:1.
- The in-app browser's event API does not expose console or page-error events (only download and file-chooser events). Runtime QA therefore used rendered error/alert-state inspection plus container logs; no visible error boundary or active alert was present.
- Keyboard dispatch from the in-app browser failed because the resolved body target changed focus. Semantic markup and focus CSS were inspected, but a full manual tab-order pass remains an optional human follow-up.

## Findings

- [P3] The implementation keeps Velocira's real six-stage lifecycle (`Brief`, `Questions`, `Ready`, `Generate`, `Review`, `Approved`) rather than the five illustrative labels in the generated reference.
  - Impact: minor copy-density difference in the stage tracker; the hierarchy and visual behavior match.
  - Decision: preserved because the six stages reflect actual product state and prevent misleading navigation.
- [P3] “View project brief” remains visible above the progress band, adding a small amount of vertical space compared with the reference.
  - Impact: the question begins slightly lower, but project context remains directly accessible and the core form stays above the fold.
  - Decision: retained as a useful existing journey action.

## Build evidence

- `npm run typecheck`: passed.
- Focused ESLint run across all modified TypeScript/TSX files: passed with zero errors or warnings.
- `npm run build`: passed; all 28 routes compiled.
- `docker compose build frontend`: passed.
- Docker frontend rebuilt and restarted successfully.
- `git diff --check`: passed (only existing line-ending notices were reported).
- `graphify update .`: passed; graph rebuilt with 3710 nodes, 10563 edges, and 215 communities.

final result: passed
