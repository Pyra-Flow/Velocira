# Discovery interview sample transcripts

These transcripts are generated from the deterministic discovery strategist used when the Gemini planner is unavailable. They are regression fixtures for the same quality principles enforced on Gemini output. Options are decision patterns with consequences, not claims about the project. Each question also offers `Not decided yet` and a free-text answer.

## 1. Ordinary project: CleanSlot

**Input:** A booking and scheduling web app for small residential cleaning companies. Customers request appointments and owners assign cleaners. The confirmed team size is two.

### 1. Problem

**Question:** Where does the current booking process break down most - availability, confirmation, reassignment, or follow-up - and which outcome must improve first?

**Decision patterns:**

- **Prevent availability conflicts:** Prioritizes accurate availability and conflict prevention before convenience features.
- **Shorten confirmation time:** Prioritizes response ownership, deadlines, and automatic status updates.
- **Prevent missed follow-up:** Prioritizes reliable reminders, delivery status, and recovery when messages fail.
- **Not decided yet:** Keeps the outcome as an explicit open decision rather than an assumed fact.

**Sample founder answer:** Most bookings arrive through phone calls and WhatsApp. Availability changes are not reflected everywhere, so roughly 8% of requests conflict or need rework. Preventing confirmed double-bookings is the first outcome to improve.

### 2. Users and authority

**Question:** When a booking changes after it is requested, who may confirm, reassign, cancel, or override it, and who only needs to be informed?

**Decision patterns:**

- **Operator owns routine decisions:** Keeps daily work fast while reserving exceptions for an approver.
- **Approver confirms consequential actions:** Adds control and auditability but requires response deadlines and escalation.
- **Authority changes by booking state:** Supports realistic hand-offs but requires explicit permission for every transition.
- **Not decided yet:** Preserves role authority as an unresolved product decision.

**Sample founder answer:** Customers may request, reschedule, or cancel before the cutoff. The owner confirms bookings, assigns or replaces cleaners, and may override conflicts. Cleaners may accept assignments and update job status but cannot change price or customer terms. Everyone affected by a change receives a status update.

### 3. First-release scope

**Question:** For the first release, which complete booking journey from request through completion must work reliably, and which adjacent capability should deliberately wait?

**Decision patterns:**

- **Complete booking journey:** Covers request, confirmation, assignment, changes, completion, and visible recovery.
- **Owner scheduling first:** Proves availability and assignment controls before broader customer self-service.
- **Customer booking first:** Prioritizes request and status while owners handle unusual conflicts manually.
- **Not decided yet:** Keeps the release boundary visible and unresolved.

**Sample founder answer:** The first release must support service setup, availability, customer booking requests, owner confirmation, cleaner assignment, reminders, rescheduling, cancellation, and completion. Online payments, route optimization, subscriptions, and native mobile apps will wait.

### 4. Booking workflow and failure recovery

**Question:** The release focus is one complete booking journey. After a customer requests a slot, what should happen through completion, including recovery from a conflict, non-response, cancellation, or missed notification?

**Decision patterns:**

- **Owner resolves conflicts:** Keeps assignment authority clear but requires a response deadline and escalation.
- **Offer alternative slots automatically:** Reduces waiting but requires trustworthy availability and conflict prevention.
- **Expire and notify everyone:** Prevents indefinite pending bookings but needs a clear expiry and recovery path.
- **Not decided yet:** Leaves the exception policy open without inventing behavior.

**Sample founder answer:** A request begins as pending. The owner has 15 minutes to confirm it and assign a cleaner. If availability changed, the customer sees three alternative times. An unanswered request expires after 30 minutes. A customer cancellation releases the slot immediately. Failed reminders appear in an owner queue with a manual-contact action.

### 5. Booking rules added from the cutoff answer

**Question:** What business rules should apply when a customer attempts to reschedule or cancel a booking near the cutoff time?

**Decision patterns:**

- **Changes follow a clear cutoff:** Defines when customers may self-serve and when an owner decides the exception.
- **Availability is rechecked before confirmation:** Prevents stale schedules from creating a confirmed conflict.
- **Cleaner acceptance has a deadline:** Keeps assignments from waiting indefinitely and defines reassignment after silence.
- **Owner overrides require a reason:** Allows exceptional handling while preserving who changed the rule and why.
- **Not decided yet:** Leaves the cutoff and exception policy visible instead of inventing one.

**Sample founder answer:** Customers may cancel until 24 hours before service. Later changes require an owner override with a reason, and availability is always rechecked before confirmation.

### 6. Quality target

**Question:** At launch, which failure is least acceptable - a double-booking, exposed address, missed notification, or slow mobile booking - and what measurable target should prevent it?

**Decision patterns:**

- **Prevent confirmed double-bookings:** Prioritizes atomic availability checks and conflict tests.
- **Protect address and access details:** Prioritizes state-based visibility, auditing, and prompt access removal.
- **Make notifications dependable:** Prioritizes delivery tracking, retry limits, and manual recovery.
- **Keep mobile booking responsive:** Prioritizes a measured completion-time target on ordinary connections.
- **Not decided yet:** Keeps the quality priority open for validation.

**Sample founder answer:** A confirmed double-booking is least acceptable. Acceptance tests must prove that two simultaneous requests cannot confirm the same cleaner and slot. Customer addresses should be visible only to the owner and assigned cleaner, and ordinary mobile screens should load within two seconds at the 95th percentile.

### 7. Success metric

**Question:** Which single result should prove the first release worked - such as fewer booking conflicts or faster confirmed bookings - and what baseline, target, and review period should be used?

**Decision patterns:**

- **Time to successful confirmation:** Measures whether the core booking job becomes faster.
- **Conflict or rework rate:** Measures whether the release prevents its target errors.
- **Successful repeated use:** Measures whether companies complete bookings and return.
- **Support and exception volume:** Exposes operational burden that usage alone can hide.
- **Not decided yet:** Records the missing measurement decision explicitly.

**Sample founder answer:** Reduce requests requiring conflict resolution from the current 8% to below 1% during the first eight weeks, while reducing median confirmation time from 20 minutes to under five minutes.

### 8. Delivery constraints

**Question:** With a confirmed team of 2, which boundary is truly fixed for the first release - launch date, budget, platform, or scope - and which may move?

**Decision patterns:**

- **Launch date is fixed:** Scope shrinks before quality or critical controls are compromised.
- **Budget and team are fixed:** Favors a smaller thin slice and managed services.
- **Platform is fixed:** Architecture must fit an existing environment.
- **Required scope is fixed:** Staffing, time, or phased delivery absorbs uncertainty.
- **Not decided yet:** Keeps the trade-off policy open rather than implied.

**Sample founder answer:** The two-person team, twelve-week delivery window, and responsive-web platform are fixed. Scope may shrink. Conflict prevention, address privacy, and visible notification recovery may not be removed.

## 2. Complex multi-role project: ApproveHub

**Input:** A multi-tenant SaaS workspace where employees submit purchasing requests, managers approve them, and administrators manage policies and audit activity. The confirmed team size is two.

### 1. Problem

**Question:** Which team workflow loses the most time or control today, and what result must the first release improve before adding broader features?

**Decision patterns:**

- **Reduce avoidable delay:** Prioritizes hand-offs, status visibility, and response deadlines.
- **Prevent costly errors:** Prioritizes validation, ownership, and recoverable failure handling.
- **Make work visible:** Prioritizes trustworthy status and exception reporting.
- **Not decided yet:** Keeps the primary outcome unresolved without inventing it.

**Sample founder answer:** Purchase requests are spread across email and spreadsheets. Employees chase managers for status, and approval takes a median of three business days. The first goal is to make every request owned and reduce approval time.

### 2. Workspace roles and permissions

**Question:** Within each customer workspace, who may configure access, perform the core work, approve it, and inspect activity across the team?

**Decision patterns:**

- **Operator owns routine decisions:** Keeps routine work fast while escalating consequential actions.
- **Approver controls consequential actions:** Adds control but needs deadlines and escalation.
- **Authority changes by workflow state:** Supports hand-offs but requires explicit transition permissions.
- **Not decided yet:** Preserves role design as an open decision.

**Sample founder answer:** Employees create and edit their own draft requests. Managers approve within their spending limit. Finance approvers decide requests above that limit. Workspace administrators manage members and policy configuration but cannot approve their own purchases. Auditors receive read-only access to requests and history.

### 3. First-release scope

**Question:** For the first release, which complete team workflow from submission through approval must work reliably, and which adjacent capability should deliberately wait?

**Decision patterns:**

- **Request-to-decision journey:** Covers submission, policy validation, approval, rejection, history, and completion.
- **Workspace controls first:** Proves membership, permissions, and policy enforcement before broad workflow features.
- **Requester experience first:** Prioritizes submission and status while complex administration remains manual.
- **Not decided yet:** Records the release boundary as unresolved.

**Sample founder answer:** The first release covers request creation, attachments, policy validation, manager or finance approval, rejection with reasons, delegation, notifications, status tracking, and audit history. Vendor onboarding, purchase-order generation, invoice matching, and payment execution are excluded.

### 4. Approval workflow and exceptions

**Question:** From submission to approval and completion, what should happen when an approver is absent, rejects the work, or the deadline passes?

**Decision patterns:**

- **Delegate to a backup approver:** Keeps work moving but requires delegation scope, expiry, and history.
- **Escalate after a response deadline:** Preserves authority while preventing indefinite waiting.
- **Return the request to its owner:** Makes the required correction explicit and avoids silent changes.
- **Not decided yet:** Leaves escalation behavior visibly unresolved.

**Sample founder answer:** Submission validates required fields and routes to the approver determined by policy. A rejection returns the request to the employee with a mandatory reason. An approver may delegate for a dated absence. After 24 hours without action, the request escalates to the approver's manager and remains visible in both queues.

### 5. Workspace data ownership

**Question:** Which records belong to a customer workspace, which may cross workspace boundaries, and what must happen to them when access or a subscription ends?

**Decision patterns:**

- **Access only while needed:** Limits visibility by role and workflow state.
- **Record owner controls sharing:** Provides user control but needs administrative recovery.
- **Organization policy controls access:** Provides consistent permissions and auditability.
- **Keep immutable change history:** Supports disputes and audits with added retention considerations.
- **Not decided yet:** Keeps lifecycle decisions open rather than assumed.

**Sample founder answer:** Requests, attachments, policies, memberships, and audit events belong exclusively to one workspace. No business record may cross workspace boundaries. Removing a member ends access immediately without deleting organizational records. Subscription cancellation produces an administrator export and then follows a retention period that the customer must choose during contracting.

### 6. Business rules

**Question:** Which actions require workspace-level permission, approval, or an immutable audit event, and who may override them?

**Decision patterns:**

- **Always enforce the rule:** Exceptions require a separate governed process.
- **Allow a named role to override:** Supports unusual cases but requires a reason and audit event.
- **Review only above a threshold:** Keeps routine work fast while escalating consequential cases.
- **Keep the decision manual initially:** Avoids invented automation until real cases define a safe rule.
- **Not decided yet:** Leaves the rule owner visible and unresolved.

**Sample founder answer:** Requests above $5,000 require finance approval. Requesters may never approve their own requests. Only a finance administrator may override a policy block, and every override requires a reason, timestamp, before-and-after values, and an immutable audit event.

### 7. Stakeholder authority

**Question:** When a scope or operational decision conflicts with delivery speed, who has final authority, and who must approve the release?

**Decision patterns:**

- **Product owner has final authority:** Centralizes scope while specialists sign off on defined risks.
- **Operational owner has final authority:** Prioritizes practical process ownership.
- **Business and risk owners approve jointly:** Adds protection but lengthens decisions.
- **Not decided yet:** Keeps governance open instead of implied.

**Sample founder answer:** The product owner owns scope and priority. The procurement lead accepts workflow behavior. Finance approves spending controls. Security may block release for tenant-isolation or access-control failures. The product owner and procurement lead jointly accept the pilot.

### 8. Quality target

**Question:** Which quality failure would most damage trust - cross-workspace access, lost work, unavailable approval, or slow core screens - and what launch target is required?

**Decision patterns:**

- **Prevent cross-workspace access:** Prioritizes isolation tests and secure defaults.
- **Prevent lost or inconsistent work:** Prioritizes validation, idempotency, backups, and recovery.
- **Keep approvals available:** Prioritizes monitoring and graceful degradation.
- **Keep core actions responsive:** Prioritizes measured latency under realistic load.
- **Not decided yet:** Preserves uncertainty without weakening the quality gate.

**Sample founder answer:** Cross-workspace access is the release blocker. Automated tests must prove isolation for every read, write, export, search, and attachment path. Core request and approval screens should remain below two seconds at the 95th percentile for the expected pilot load.

### 9. Success metric

**Question:** Which single result should prove the first release worked - such as faster completed team work with fewer approval delays - and what baseline, target, and review period should be used?

**Decision patterns:**

- **Time to approval:** Measures whether the central workflow is faster.
- **Failure or rework rate:** Measures policy and submission quality.
- **Successful repeated use:** Measures adoption beyond account creation.
- **Support and exception volume:** Reveals operational burden hidden by usage counts.
- **Not decided yet:** Records the missing measurement decision.

**Sample founder answer:** Reduce median approval time from three business days to under eight working hours during the first 60 days, with at least 90% of requests completed without manual status chasing.

### 10. Delivery constraints

**Question:** With a confirmed team of 2, which boundary is truly fixed for the first release - launch date, budget, platform, or scope - and which may move?

**Decision patterns:**

- **Launch date is fixed:** Scope shrinks before quality or controls.
- **Budget and team are fixed:** Favors managed services and a smaller slice.
- **Platform is fixed:** Architecture must fit the existing environment.
- **Required scope is fixed:** Staffing, time, or phases absorb uncertainty.
- **Not decided yet:** Keeps the delivery policy explicit and open.

**Sample founder answer:** Two developers and a sixteen-week pilot date are fixed. Responsive web and managed hosting are fixed. Reporting depth and policy-template breadth may shrink; tenant isolation, auditability, and the complete approval journey may not.

## 3. Regulated project: ClinicRelay

**Input:** A healthcare care-coordination web app for patient hand-offs between nurses, physicians, and clinic coordinators using private patient records. The confirmed team size is two. No specific law or certification has been asserted by the input.

### 1. Problem

**Question:** Which care-coordination failure causes the most harmful delay or uncertainty today, and what observable outcome must improve first?

**Decision patterns:**

- **Reduce hand-off delays:** Prioritizes acknowledgment, escalation, and visibility.
- **Prevent missing care context:** Prioritizes required information, validation, and correction ownership.
- **Make ownership unambiguous:** Prioritizes responsibility, status, and escalation rules.
- **Not decided yet:** Keeps the care outcome open instead of assumed.

**Sample founder answer:** Shift hand-offs rely on calls and free-text messages. Urgent items can remain unacknowledged for up to two hours, and the sender cannot see who owns the next action. The first goal is reliable acknowledgment and ownership.

### 2. Clinical roles and authority

**Question:** During the core care hand-off, which roles may create, approve, correct, and view the record, and who owns unresolved exceptions?

**Decision patterns:**

- **Operator handles routine decisions:** Keeps normal work fast while escalating exceptions.
- **Approver confirms consequential changes:** Adds control but requires response deadlines.
- **Authority changes by hand-off state:** Supports clinical hand-offs but requires explicit permissions.
- **Not decided yet:** Preserves clinical authority as an open decision.

**Sample founder answer:** A sending nurse creates and may correct a hand-off until acknowledgment. The receiving nurse acknowledges and accepts ownership. A physician approves corrections to clinical instructions. Coordinators monitor deadlines and reassign operational ownership but cannot alter clinical content. Patients do not use the first release directly.

### 3. First-release scope

**Question:** For the first release, which complete care hand-off from creation through acknowledgment must work reliably, and which adjacent capability should deliberately wait?

**Decision patterns:**

- **Closed-loop care hand-off:** Covers creation, validation, acknowledgment, escalation, correction, and traceability.
- **Clinical-team workflow first:** Proves safety and accountability before patient-facing access.
- **Coordination visibility first:** Prioritizes queues while complex clinical edits remain manual.
- **Not decided yet:** Keeps the release boundary unresolved and reviewable.

**Sample founder answer:** The pilot covers hand-off creation, required-field validation, recipient selection, urgent flagging, acknowledgment, rejection for correction, deadline escalation, status visibility, and immutable history. Diagnosis, treatment recommendations, broad record-system replacement, and a patient portal are excluded.

### 4. Hand-off workflow and escalation

**Question:** From the first hand-off entry to confirmed receipt, what should each role see and do when information is incomplete, urgent, rejected, or never acknowledged?

**Decision patterns:**

- **Escalate an unacknowledged hand-off:** Assigns a deadline, backup recipient, and visible owner.
- **Return incomplete information for correction:** Protects quality while preserving an accountable path.
- **Use a governed urgent path:** Permits faster handling with named authority and complete history.
- **Not decided yet:** Keeps exception handling open instead of fabricated.

**Sample founder answer:** Required information is validated before sending. The receiver has ten minutes to acknowledge an urgent hand-off. After ten minutes the coordinator and backup receiver are alerted; after another five minutes the on-call physician is alerted. Rejection requires a reason and returns ownership to the sender. Every transition remains visible and timestamped.

### 5. Patient-data ownership and lifecycle

**Question:** Which patient and hand-off details are essential, who owns corrections, and when must access, retention, or deletion differ by role?

**Decision patterns:**

- **Access only while needed:** Limits sensitive record visibility by role and state.
- **Record owner controls sharing:** Adds control but requires administrative recovery.
- **Organization policy controls access:** Gives consistent permissions and auditing.
- **Keep immutable change history:** Supports investigation with added retention considerations.
- **Not decided yet:** Preserves lifecycle questions without inventing a policy.

**Sample founder answer:** Essential fields are patient identifier, sending and receiving roles, urgency, concise situation summary, pending tasks, status, and timestamps. Corrections create a new version and retain the original. Only the active care team and authorized coordinators may view a hand-off. The organization must supply the retention and deletion period before production; the interview must not invent one.

### 6. Safety and privacy risk

**Question:** Which realistic failure could delay care, expose patient information, or hide accountability, and where must prevention or human escalation occur?

**Decision patterns:**

- **Prevent the failure by design:** Uses validation, permissions, and safe defaults.
- **Detect quickly and recover:** Uses monitoring, history, alerts, and a tested owner.
- **Require human review:** Routes consequential ambiguity to an accountable person.
- **Limit the blast radius:** Prevents one failure from spreading across records or teams.
- **Not decided yet:** Leaves risk priority explicit and unresolved.

**Sample founder answer:** The highest risk is an urgent hand-off appearing delivered without a responsible clinician acknowledging it. The system must never equate message delivery with clinical acceptance. It must display ownership separately, monitor the deadline, escalate to humans, and preserve the complete event history.

### 7. Quality target

**Question:** Which launch failure is least acceptable - missed acknowledgment, incorrect access, unavailable hand-off data, or an untraceable edit - and what target would be safe enough?

**Decision patterns:**

- **Bound acknowledgment time:** Prioritizes a measurable deadline and escalation.
- **Prevent incorrect patient-record access:** Prioritizes least privilege and denial tests.
- **Make every correction traceable:** Prioritizes immutable history and accountable ownership.
- **Not decided yet:** Keeps the final target open for clinical approval.

**Sample founder answer:** Incorrect patient-record access is an absolute release blocker. Every role and record-state combination requires an automated authorization test and an auditable denial event. During the pilot, at least 95% of urgent hand-offs should be acknowledged or escalated within 15 minutes.

### 8. Clinical business rules

**Question:** Which hand-off actions require acknowledgment, escalation, correction approval, or restricted access, and who may override those rules in an emergency?

**Decision patterns:**

- **Always enforce the rule:** Exceptions use a separate governed process.
- **Allow a named role to override:** Requires a reason and immutable event.
- **Review only above a risk threshold:** Keeps routine work fast while escalating consequential cases.
- **Keep the decision manual initially:** Avoids unsafe automation before real cases define rules.
- **Not decided yet:** Records missing rule authority visibly.

**Sample founder answer:** The sender cannot acknowledge their own hand-off. Urgent hand-offs always use the escalation clock. Only the on-call physician may use an emergency recipient override, and the system requires a reason and immutable event. Clinical-content corrections after acknowledgment require physician approval.

### 9. Release authority

**Question:** When a safety or privacy decision conflicts with delivery speed, who has final authority, and who must approve the release?

**Decision patterns:**

- **Product owner has final authority:** Centralizes scope while specialists sign off on defined risks.
- **Operational owner has final authority:** Prioritizes practical clinical operations.
- **Business and risk owners approve jointly:** Adds protection but lengthens decisions.
- **Not decided yet:** Keeps governance open rather than implied.

**Sample founder answer:** The product owner owns scope. The clinical safety lead has final authority over hand-off and escalation safety. The privacy officer approves access and data handling. Clinic operations accepts workflow fit. All three must approve the production pilot.

### 10. Explicit exclusions

**Question:** Which nearby capability must be explicitly excluded from the first release - for example diagnosis, treatment recommendations, or broad record-system replacement - even if users request it?

**Decision patterns:**

- **Advanced automation waits:** Keeps consequential decisions human-controlled.
- **Historical migration waits:** Reduces data-cleaning and attribution risk.
- **Non-essential integrations wait:** Protects the core journey from dependency risk.
- **Native mobile applications wait:** Proves a responsive workflow before separate builds.
- **Not decided yet:** Keeps exclusions visible when leadership has not decided.

**Sample founder answer:** Diagnosis, treatment recommendations, patient-facing advice, automatic clinical prioritization, historical record migration, and full record-system replacement are excluded. The product coordinates a human-owned hand-off only.

### 11. Success metric

**Question:** Which single result should prove the first release worked - such as faster acknowledged hand-offs without safety or privacy incidents - and what baseline, target, and review period should be used?

**Decision patterns:**

- **Time to acknowledgment:** Measures whether ownership becomes faster and clearer.
- **Failure or rework rate:** Measures missing information and rejected hand-offs.
- **Successful repeated use:** Measures adoption by real care teams.
- **Support and exception volume:** Exposes operational burden hidden by usage.
- **Not decided yet:** Records the measurement gap explicitly.

**Sample founder answer:** Reduce median acknowledgment from 45 minutes to under ten minutes and achieve 95% acknowledgment or escalation within 15 minutes during the eight-week pilot, with zero unauthorized-access incidents.

### 12. Delivery constraints

**Question:** With a confirmed team of 2, which boundary is truly fixed for the first release - launch date, budget, platform, or scope - and which may move?

**Decision patterns:**

- **Launch date is fixed:** Scope shrinks before safety or controls.
- **Budget and team are fixed:** Favors a smaller pilot and managed infrastructure.
- **Platform is fixed:** Architecture must fit the approved environment.
- **Required scope is fixed:** Staffing, time, or phases absorb uncertainty.
- **Not decided yet:** Keeps the delivery policy open and reviewable.

**Sample founder answer:** The two-person team and approved web platform are fixed. The pilot may move if clinical safety and privacy reviews are incomplete. Reporting depth and non-essential integrations may shrink; authorization, acknowledgment, escalation, and audit history may not.

## Critical review

- A minimal ordinary flow asks seven questions. This example expands to eight because the founder introduced a material cancellation cutoff that the SRS cannot safely invent.
- The multi-role flow expands to ten questions because tenant isolation, approval authority, business rules, and workspace data ownership materially affect design.
- The regulated flow expands to twelve questions and explicitly asks about human escalation, access, traceability, governance, exclusions, and safety measurement without inventing a specific legal obligation.
- Each option expresses a design pattern and consequence. None is stored as a project fact until the founder selects it or supplies a custom answer.
- `Not decided yet` remains mutually exclusive in the UI. Tentative and contradictory answers stay visible and can trigger a precise revision question.
