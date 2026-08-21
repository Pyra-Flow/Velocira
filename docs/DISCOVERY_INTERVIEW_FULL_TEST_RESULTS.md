# Discovery interview full test results

Date: 2026-08-16

## Test input

**Project:** CleanSlot

**Description:** A booking and scheduling web app for small residential cleaning companies. Customers request appointments and owners assign cleaners.

**Industry:** Home services

**Audience:** Cleaning-company owners, cleaners, and customers

**Team:** Two people

The test submitted a selected decision plus a realistic founder-written answer at every turn. It exercised live Gemini planning, provider fallback, deterministic safety fallback, adaptive interview depth, answer persistence, readiness, and the final canonical brief inputs.

## Weaknesses found and corrected

1. Gemini could paste the project name into a reusable generic question and pass validation.
2. Gemini saw every unanswered category and sometimes jumped ahead of the highest-ranked question.
3. Later questions did not consistently carry a confirmed prior decision forward.
4. Several deterministic answer options were generic role or quality patterns rather than domain decisions.
5. Generated options could invent unsupported numeric targets.
6. A second vague "undecided" option could appear beside the canonical `not-decided` option.
7. `not-decided` could be stored as an answered fact and could be combined with contradictory confirmed choices.
8. Mentioning payment only to exclude it could change the domain to fintech or inflate interview depth.
9. A material cancellation cutoff did not add the business-rule question needed by the SRS.
10. Category-relevant wording could still omit a required facet, such as a measurable quality threshold or metrics review window.

All ten issues are now covered by validation or integration tests.

## Final enforced interview

### 1. Problem

**Question:** Where does the current booking process break down most - availability, confirmation, reassignment, or follow-up - and which outcome must improve first?

**Options:** Prevent availability conflicts; Shorten confirmation time; Prevent missed follow-up; Not decided yet.

**Selected:** Prevent availability conflicts

**Founder answer:** Bookings arrive by phone and WhatsApp. Around 8% conflict because availability changes are not reflected everywhere; preventing confirmed double-bookings is the first priority.

**Planner outcome:** A broad "primary operational bottleneck" model draft was rejected. The booking-specific deterministic question was used.

### 2. Users and authority

**Question:** In the scheduling process, who will be responsible for resolving double-booking alerts and final availability overrides?

**Options:** Company owners only; Owners and cleaners share responsibility; Automated prevention with human exception ownership; Not decided yet.

**Founder answer:** Customers may request, reschedule, or cancel before a cutoff. The owner confirms and reassigns bookings. Cleaners accept assignments and update job status but cannot change prices.

**Planner outcome:** Accepted from the configured Gemini retry route (`gemini-3.5-flash`).

### 3. First-release scope

**Question:** To prevent the 8% booking conflict rate, what core workflow must the first release fully prove before adding extra features?

**Options:** End-to-end booking, confirmation, and assignment; Internal owner scheduling first; Customer booking requests with automated slot checks; Not decided yet.

**Founder answer:** The first release covers service setup, availability, request, owner confirmation, cleaner assignment, reminders, rescheduling, cancellation, and completion. Payment and route optimization are out of scope.

**Planner outcome:** Accepted from `gemini-3.5-flash`. The negative-scope detector prevents the excluded payment feature from changing the project domain or required depth.

### 4. Workflow and recovery

**Question:** When a customer requests a cleaning appointment that conflicts with an existing assignment, what should the immediate recovery or hold-state workflow be before the owner resolves it?

**Options:** Block the conflicting selection; Accept it as pending review with a warning; Offer the next available slots; Not decided yet.

**Founder answer:** Requests start pending. The owner has 15 minutes to confirm. Conflicts offer three alternatives; unanswered requests expire after 30 minutes; failed reminders enter an owner recovery queue.

**Planner outcome:** Accepted from `gemini-3.5-flash`.

### 5. Business rules added adaptively

**Question:** What business rules should apply when a customer attempts to reschedule or cancel a booking near the cutoff time?

**Options:** Strict self-service lockout; Allow self-service with a warning; Convert late changes to pending owner review; Not decided yet.

**Founder answer:** Customers may cancel until 24 hours before service. Later changes require an owner override with a reason; availability is always rechecked before confirmation.

**Planner outcome:** Accepted from `gemini-3.5-flash`. This question was added only after the earlier answer revealed a material cutoff.

### 6. Measurable quality target

**Question:** With preventing confirmed booking conflicts already prioritized, at launch, which failure is least acceptable - a double-booking, exposed address, missed notification, or slow mobile booking - and what measurable target should prevent it?

**Options:** Prevent confirmed double-bookings; Protect address and access details; Make notifications dependable; Keep mobile booking responsive; Not decided yet.

**Founder answer:** Two simultaneous requests must never confirm the same cleaner and slot. Addresses are visible only to the owner and assigned cleaner; normal mobile screens should load within two seconds at p95.

**Planner outcome:** The model draft selected a failure but omitted the measurable threshold. It was rejected and replaced by the safe booking-specific question.

### 7. Success metrics

**Question:** To measure progress on preventing confirmed double-bookings, which single result should prove the first release worked, and what baseline, target, and review period should be used?

**Options:** Confirmed booking-conflict rate; Request-to-confirmation time; Manual recovery volume; Successful repeat booking; Not decided yet.

**Founder answer:** Reduce booking-conflict rework from 8% to below 1% in eight weeks and median confirmation time from 20 minutes to under five.

**Planner outcome:** The model draft omitted the complete baseline/target/review-window decision. The complete deterministic question was used.

### 8. Delivery constraints

**Question:** The release focus is preventing confirmed booking conflicts. With a confirmed team of two, which boundary is fixed for the first release - launch date, budget, platform, or scope - and which may move?

**Options:** Launch date is fixed; Budget and team are fixed; Platform is fixed; Required scope is fixed; Not decided yet.

**Founder answer:** The two-person team, twelve-week window, and responsive web platform are fixed. Scope may shrink, but conflict prevention and privacy controls may not.

**Planner outcome:** The model draft asked only what was non-negotiable. It was rejected because it did not also ask what may move.

## Resulting discovery output

- **Problem:** Confirmed booking conflicts caused by fragmented availability updates; current rework baseline is approximately 8%.
- **Actors:** Customers request and change bookings, owners confirm and override, cleaners accept assignments and update job status.
- **Release boundary:** Responsive-web booking journey through completion; payment and route optimization are excluded.
- **Workflow:** Pending request, 15-minute owner confirmation, alternative slots on conflict, 30-minute expiry, visible reminder-recovery queue.
- **Business rules:** 24-hour customer cancellation cutoff; later changes require a reasoned owner override; availability is rechecked before confirmation.
- **Quality:** No two simultaneous requests may confirm the same cleaner and slot; address visibility is restricted; ordinary mobile p95 load target is two seconds.
- **Success:** Conflict rework below 1% and median confirmation below five minutes within eight weeks.
- **Constraints:** Two-person team, twelve-week window, responsive web; scope may shrink while conflict prevention and privacy controls remain.

The backend reached generation readiness with eight targeted categories. Payment being explicitly out of scope did not add fintech, integration, entity, risk, or stakeholder questions.

## Final verification

- AI-service discovery and routing suite: 42 tests passed.
- Backend database-backed interview suite: 12 tests passed, covering adaptive seven-question and eight-question paths, durable readiness, revisions, no duplicates, and undecided-answer blocking.
- Frontend verification: TypeScript typecheck and ESLint passed.
- Live Gemini route exercised with the configured `gemini-3.6-flash` primary and `gemini-3.5-flash` retry fallback.
- Invalid model output was rejected without reaching the founder; deterministic domain-specific output remained available as the final safety fallback.
