# Project Plan & Flow

## Velocira — Product Development Roadmap

| Field             | Details                                      |
|-------------------|----------------------------------------------|
| **Project Name**  | Velocira                                     |
| **Version**       | 1.0 (MVP)                                    |
| **Launch Target** | Q2 2026                                      |
| **Development Duration** | ~16 Weeks (V1 MVP)                    |
| **Team Size**     | 4–6 Members (cross-functional)               |

---

## Table of Contents

1. [Project Timeline Overview](#1-project-timeline-overview)
2. [Phase Breakdown](#2-phase-breakdown)
3. [Sprint Plan (Agile)](#3-sprint-plan-agile)
4. [Task Breakdown by Role](#4-task-breakdown-by-role)
5. [Workflow & Process](#5-workflow--process)
6. [Flow Diagrams](#6-flow-diagrams)
7. [Risk Management](#7-risk-management)
8. [Milestones & Deliverables](#8-milestones--deliverables)
9. [Tools & Resources](#9-tools--resources)
10. [Weekly Progress Tracking Template](#10-weekly-progress-tracking-template)

---

## 1. Project Timeline Overview

### 16-Week Gantt Chart (Text View)

```
Week:   1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16
        ├────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤

Phase 1: Planning & Design
        ████████████████
        W1──────────W4

Phase 2: Backend Foundation
                  ████████████████
                  W3──────────W6

Phase 3: ML Service Development
                            ████████████████
                            W5──────────W8

Phase 4: Frontend Development
                                      ████████████████████
                                      W7────────────W11

Phase 5: Integration & Testing
                                                    ████████████████
                                                    W10─────────W13

Phase 6: Polish & Documentation
                                                                ████████████
                                                                W13─────W15

Phase 7: Final Presentation
                                                                        ████
                                                                        W16
```

### Phase Summary

| Phase | Name                      | Weeks   | Duration | Key Output & Goals                         |
|-------|---------------------------|---------|----------|---------------------------------------------|
| 1     | Product Strategy & Design | 1 – 3   | 3 weeks  | Product roadmap, SRS, system architecture  |
| 2     | Backend MVP Foundation    | 2 – 5   | 4 weeks  | Auth, project CRUD, DB, billing setup      |
| 3     | ML Service MVP            | 4 – 7   | 4 weeks  | AI generation for core 4 doc types (v1.0)  |
| 4     | Frontend MVP              | 6 – 9   | 4 weeks  | Landing page, auth, wizard, viewer         |
| 5     | Integration & Alpha Test  | 8 – 11  | 4 weeks  | End-to-end flows, internal testing         |
| 6     | Beta Launch & Hardening   | 11 – 14 | 4 weeks  | Fix issues, optimize, prepare launch       |
| 7     | Production Launch         | 15      | 1 week   | Go live, monitor, gather customer feedback |

> **Note:** Phases overlap intentionally. While the backend team works on Phase 2, the ML team can start Phase 3 research, and the frontend team can build static pages.

---

## 2. Phase Breakdown

### Phase 1: Product Strategy & Design (Weeks 1–3)

**Goal:** Define the product vision, establish technical foundation, validate market assumptions, and design the MVP.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 1    | Define product vision, target users, and MVP scope                           | Product PRD v1.0               |
| 1    | Set up GitHub repository, branching strategy, CI/CD pipeline skeleton        | Repository + CI/CD ready       |
| 2    | Write SRS document (MVP features + roadmap for v1.1, v2.0)                   | SRS v1.0                       |
| 2    | Design ERD and database schema for MVP                                        | ERD diagram + SQL script       |
| 3    | Design system architecture, deployment topology, scaling strategy             | Architecture document          |
| 3    | Design API endpoints (REST) and OpenAPI/Swagger spec                         | API specification              |
| 3    | Create wireframes/mockups for MVP pages (Figma)                              | MVP Wireframes                 |
| 3    | Set up development environment, Docker setup, local DB                        | Dev environment ready          |

**Team Allocation (MVP):**
| Role                    | Focus                                          |
|-------------------------|------------------------------------------------|
| Product Manager/Lead    | Product vision, PRD, stakeholder communication  |
| 2x Backend Developer    | Auth, CRUD, DB schema, API, integrations       |
| Frontend Developer      | UI, responsive design, state management        |
| ML/AI Engineer          | LLM research, prompt engineering, quality      |
| DevOps/Infrastructure   | CI/CD, Docker, deployment, monitoring          |

---

### Phase 2: Backend MVP Foundation (Weeks 2–5)

**Goal:** Build the Spring Boot backend with authentication, project management, billing integration, and database setup.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 2    | Initialize Spring Boot project with Maven, set up package structure           | Project skeleton               |
| 2    | Configure PostgreSQL, JPA, Flyway migrations                                 | Database connected             |
| 3    | Implement User entity + repository + service + subscription model             | User CRUD + subscription ready |
| 3    | Implement JWT authentication (register, login, refresh)                      | Auth working end-to-end        |
| 4    | Implement Spring Security config + JWT filter + role-based access            | Secured endpoints + roles      |
| 4    | Implement Project entity + CRUD endpoints                                    | Projects API working           |
| 5    | Implement GeneratedDocument + DocumentSection entities                       | Document models ready          |
| 5    | Implement ML Service Client (RestTemplate) + error handling                  | ML integration ready           |
| 5    | Write unit + integration tests for services                                  | Test coverage > 70%            |

**Key Deliverables:**
- ✅ User registration/login with JWT tokens
- ✅ Role-based access control (User, Admin roles)
- ✅ Project CRUD operations with ownership tracking
- ✅ Database schema with migrations
- ✅ API rate limiting configured

---

### Phase 3: ML Service MVP (Weeks 4–7)

**Goal:** Build the Python FastAPI service that generates 4 core document types using LLMs. Quality over quantity for v1.0.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 4    | Initialize FastAPI project, set up LLM integration (Ollama)                  | ML project skeleton            |
| 4    | Write prompt templates for SRS + Use Case generation with validation         | 2 generators working           |
| 5    | Implement response parser (extract structured data from LLM output)          | Parser & validator working     |
| 6    | Write prompt templates for ERD + API Structure generation                    | 4 generators working           |
| 6    | Test quality of outputs, refine prompts based on user feedback patterns      | Quality benchmarks met         |
| 7    | Implement health check, error handling, logging, retry logic                 | Production-ready for MVP       |
| 7    | Performance testing and optimization (latency < 30s for generation)          | Performance validated          |

**MVP Scope (V1.0):**
- ✅ SRS Document generation
- ✅ Use Cases generation
- ✅ ERD generation  
- ✅ API Structure generation
- 🔜 Architecture & Roadmap (v1.1)
- 🔜 Multi-language support (v1.1)
- ✅ `POST /generate/{docType}` (single doc type)

---

### Phase 4: Frontend MVP (Weeks 6–9)

**Goal:** Build essential Next.js pages and components with full API integration. Focus on core user flows.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 6    | Initialize Next.js project with Tailwind + shadcn/ui + TypeScript            | Frontend skeleton              |
| 6    | Build layout components (Navbar, Footer, Loading states)                     | Layout ready                   |
| 7    | Build landing page (marketing, hero, pricing table for v1.1)                 | Landing page live              |
| 7    | Build authentication pages (login, register, password reset)                 | Auth UI working                |
| 8    | Implement auth context (Zustand) + Axios interceptors with JWT               | Auth flow complete             |
| 8    | Build dashboard page (project list, create project button)                   | Dashboard working              |
| 9    | Build Project Wizard (multi-step: description → type → stack → confirm)     | Wizard complete                |
| 9    | Build Document Viewer (Markdown rendering, section navigation)               | Viewer working                 |
| 9    | Build Export functionality (PDF/DOCX/Markdown download)                      | Export working                 |

**MVP Frontend Pages:**
- ✅ Landing page (marketing)
- ✅ Login/Register pages
- ✅ Dashboard (project management)
- ✅ Project Wizard (generation flow)
- ✅ Document Viewer (read-only v1.0)
- 🔜 Document Editor (v1.1)
- 🔜 Admin Panel (v1.1)

---

### Phase 5: Integration & Alpha Testing (Weeks 8–11)

**Goal:** Connect all services end-to-end, conduct internal testing, gather feedback, and fix critical bugs before beta.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 8    | Integrate Frontend ↔ Backend (auth flow, project CRUD)                      | Auth + CRUD integrated         |
| 9    | Integrate Backend ↔ ML Service (document generation flow)                   | Generation integrated          |
| 9    | End-to-end testing: create project → generate → view → export               | E2E flow working                |
| 10   | Alpha testing with internal users (team members, friends)                    | Feedback collected             |
| 10   | Fix critical bugs from alpha feedback                                        | Major blockers resolved        |
| 11   | Performance testing (latency, API response times)                            | Performance benchmarked        |
| 11   | Security review (auth, rate limiting, CORS, data validation)                 | Security audit passed          |

**Acceptance Criteria:**
- ✅ Create project → Generate document flow completes in < 45 seconds
- ✅ All critical bugs fixed
- ✅ No data loss during generation
- ✅ API rate limiting in place
- ✅ All edge cases handled

---

### Phase 6: Beta Launch & Hardening (Weeks 11–14)

**Goal:** Optimize performance, harden infrastructure, fix remaining issues, and prepare for public launch.

| Week | Tasks                                                                        | Deliverable                    |
|------|------------------------------------------------------------------------------|--------------------------------|
| 11   | Deploy to staging (Vercel + Railway)                                        | Staging environment live       |
| 12   | UI/UX polish: responsive design, loading states, error messages              | Polished UI                    |
| 12   | Set up monitoring, logging, error tracking (Sentry)                          | Monitoring configured          |
| 13   | Set up email notifications (auth, generation complete)                       | Email system working           |
| 13   | Document API (OpenAPI/Swagger), setup guide, architecture docs              | Documentation complete         |
| 13   | Set up CI/CD pipeline (GitHub Actions for tests, deploy)                    | Automated deployment ready     |
| 14   | Beta launch: invite early users, gather feedback                             | Beta users engaged             |
| 14   | Monitor bugs, fix critical issues, optimize based on usage                   | Beta issues fixed              |
| 14   | Prepare for GA: security audit, scalability review                           | Ready for public launch        |

**Pre-Launch Checklist:**
- ✅ All critical bugs fixed
- ✅ Performance meets targets
- ✅ Infrastructure scalable to 1K+ concurrent users
- ✅ Monitoring & alerting configured
- ✅ Backup & disaster recovery plan ready
- ✅ Terms of Service & Privacy Policy drafted

---

### Phase 7: Production Launch (Week 15)

| Task                                  | Details                                         |
|---------------------------------------|-------------------------------------------------|
| Final deployment to production        | Cut over from staging, monitor closely           |
| Send launch announcement              | Email users, social media, Product Hunt         |
| Monitor production 24/7                | Watch error logs, performance metrics, support  |
| Celebrate! 🎉                         | Team retrospective, reflect on learnings        |

---

## 3. Release Roadmap Beyond MVP

### V1.1 (Q3 2026 — 4–6 weeks after MVP launch)
- Team collaboration (shared projects, permissions)
- Document versioning & rollback
- Subscription/payment system (Stripe)
- Architecture & Implementation Roadmap generation
- Multi-language support (Spanish, Chinese, French)
- Email export & sharing

### V2.0 (Q4 2026 — 8–12 weeks after MVP launch)
- Real-time collaborative editing
- AI-powered document refinement (feedback loop)
- Integration with Jira, GitHub, Linear
- Mobile-responsive web app
- Advanced analytics dashboard
- Custom branding (for enterprise)
- On-premise deployment option

---

## 3. Sprint Plan (Agile)

The project follows **Agile Scrum** with **2-week sprints** (7–8 sprints for MVP).

### Sprint Calendar (MVP)

| Sprint | Weeks | Phase                          | Sprint Goal                                  |
|--------|-------|--------------------------------|----------------------------------------------|
| 1      | 1–2   | Product Strategy & Design      | PRD complete, SRS done, architecture agreed  |
| 2      | 2–3   | Backend + ML Kickoff           | Auth implemented, DB schema ready            |
| 3      | 4–5   | Backend Core + ML Generators   | Project CRUD done, 2 generators working      |
| 4      | 6–7   | ML Complete + Frontend Start   | All 4 generators done, frontend scaffolded   |
| 5      | 8–9   | Frontend Core                  | Dashboard, wizard, viewer complete           |
| 6      | 10–11 | Integration & Alpha Testing    | E2E flow working, internal testing done      |
| 7      | 12–13 | Hardening & Optimization       | Performance tuned, bugs fixed                |
| 8      | 14–15 | Beta & Launch Prep             | Beta feedback incorporated, ready to launch  |

### Sprint Ceremonies

| Ceremony              | Frequency      | Duration   | Purpose                                |
|-----------------------|----------------|------------|----------------------------------------|
| Sprint Planning       | Start of sprint| 1 hour     | Define sprint goals and tasks          |
| Daily Standup         | Daily          | 15 minutes | What I did, what I'll do, blockers     |
| Sprint Review         | End of sprint  | 30 minutes | Demo completed work                    |
| Sprint Retrospective  | End of sprint  | 30 minutes | What went well, what to improve        |

> **Tip:** Use **GitHub Projects** (free Kanban board) or **Linear** for task tracking. Create columns: Backlog → To Do → In Progress → Review → Done.

---

## 4. Team Structure & Responsibilities

### 4.1 Cross-Functional Team Roles (MVP Phase)

| Role                     | Headcount | Primary Responsibilities                          |
|--------------------------|-----------|---------------------------------------------------|
| **Product Manager/Lead** | 1         | Product vision, PRD, stakeholder comms, metrics   |
| **Backend Engineers**    | 2         | API, auth, integrations, database, scaling        |
| **Frontend Engineer**    | 1         | UI/UX, responsive design, state management        |
| **ML/AI Engineer**       | 1         | LLM integration, prompt engineering, quality      |
| **DevOps/Infrastructure**| 1 (Part)  | CI/CD, monitoring, deployment, cloud infra        |

> **Flexible:** Can start with 3–4 person team and grow. One person can wear multiple hats initially.

### 4.2 Team-Based Task Breakdown

#### Product Manager/Lead
| # | Task                                         | Sprint | Priority |
|---|----------------------------------------------|--------|----------|
| 1 | Write Product Requirements Document (PRD)    | 1      | High     |
| 2 | Finalize SRS document + get stakeholder sign-off | 1  | High     |
| 3 | Design system architecture and decision records | 1–2 | High     |
| 4 | Set up GitHub repo, branching strategy, CI/CD skeleton | 1 | High |
| 5 | Coordinate sprints, run daily standups, unblock team | 1–8 | High  |
| 6 | Define success metrics and KPIs                | 1      | High     |
| 7 | Manage roadmap, prioritize backlog              | 1–8    | Medium   |
| 8 | Prepare beta launch strategy                   | 6–7    | Medium   |
| 9 | Plan go-to-market and user acquisition         | 7–8    | Medium   |

#### Backend Engineers (Team)
| # | Task                                         | Sprint | Assigned |
|---|----------------------------------------------|--------|----------|
| 1 | Set up Spring Boot project + Maven configs    | 2      | BE1      |
| 2 | Configure PostgreSQL + Flyway migrations      | 2      | BE1      |
| 3 | Implement User entity + repository + auth     | 2–3    | BE1      |
| 4 | Implement JWT tokens (access + refresh)       | 2–3    | BE1      |
| 5 | Configure Spring Security + role-based access | 3      | BE1      |
| 6 | Implement Project entity + CRUD endpoints     | 3–4    | BE2      |
| 7 | Implement Document models + repositories      | 3–4    | BE2      |
| 8 | Implement ML Service Client (HTTP caller)     | 3–4    | BE2      |
| 9 | Implement generation orchestration flow       | 4–5    | BE2      |
| 10| Implement export module (PDF/DOCX)            | 6      | BE2      |
| 11| Build admin API endpoints                     | 6–7    | BE1      |
| 12| Set up error handling, logging, monitoring    | 5–7    | BE1      |
| 13| Write unit + integration tests (>70% coverage)| 2–7    | Both     |
| 14| Performance tuning + database optimization    | 7      | Both     |

#### Frontend Developer
| # | Task                                         | Sprint | Priority |
|---|----------------------------------------------|--------|----------|
| 1 | Initialize Next.js + Tailwind + shadcn/ui    | 4      | High     |
#### Frontend Engineer
| # | Task                                         | Sprint | Priority |
|---|----------------------------------------------|--------|----------|
| 1 | Initialize Next.js + Tailwind + shadcn/ui    | 4      | High     |
| 2 | Build layout components (Navbar, Footer)     | 4      | High     |
| 3 | Build landing page (hero, features, CTA)     | 4–5    | High     |
| 4 | Build auth pages (login, register)           | 5      | High     |
| 5 | Implement auth context (Zustand) + JWT flow  | 5      | High     |
| 6 | Build dashboard (projects list, CRUD)        | 5      | High     |
| 7 | Build Project Wizard (multi-step form)       | 5–6    | High     |
| 8 | Build Document Viewer (Markdown rendering)   | 6      | High     |
| 9 | Implement Document Export modal              | 6      | High     |
| 10| Build responsive design + mobile support     | 6–7    | Medium   |
| 11| Build admin pages (user management, stats)   | 7      | Medium   |
| 12| UI polish (loading states, error messages)   | 7      | Medium   |
| 13| Browser compatibility testing                | 7      | Medium   |

#### ML/AI Engineer  
| # | Task                                         | Sprint | Priority |
|---|----------------------------------------------|--------|----------|
| 1 | Research LLM options (Ollama, HF, API-based) | 1      | High     |
| 2 | Evaluate prompt quality & generation times   | 1–2    | High     |
| 3 | Set up FastAPI project structure             | 2      | High     |
| 4 | Integrate Ollama locally (dev environment)   | 2–3    | High     |
| 5 | Write SRS generation prompt + implementation | 3–4    | High     |
| 6 | Write Use Case generation prompt            | 3–4    | High     |
| 7 | Write ERD generation prompt                  | 4      | High     |
| 8 | Write API Structure generation prompt        | 4      | High     |
| 9 | Implement response parsing & validation      | 4      | High     |
| 10| Prompt tuning based on quality feedback      | 4–6    | High     |
| 11| Performance optimization (response time)     | 6      | Medium   |
| 12| Write tests + error handling                 | 5–6    | Medium   |
| 13| Deploy ML service (Railway/Render)           | 7      | Medium   |
| 14| Monitor production ML performance            | 8      | Medium   |

---

## 5. Workflow & Process

### 5.1 Git Branching Strategy

```
main (production)
 │
 ├── develop (integration branch)
 │    │
 │    ├── feature/auth-module
 │    ├── feature/project-crud
 │    ├── feature/ml-srs-generator
 │    ├── feature/frontend-dashboard
 │    ├── feature/export-module
 │    ├── fix/login-redirect-bug
 │    └── ...
 │
 └── release/v1.0 (pre-release)
```

**Rules:**
1. **Never push directly to `main` or `develop`.**
2. Create a **feature branch** for each task: `feature/<module>-<task>`
3. Submit a **Pull Request (PR)** to `develop` when done.
4. At least **1 team member** must review and approve the PR.
5. Merge `develop` → `main` only for releases.

### 5.2 Code Review Checklist

Before approving a PR, the reviewer checks:

- [ ] Code compiles and runs without errors
- [ ] Follows project coding standards
- [ ] Has meaningful variable and function names
- [ ] No hardcoded values (use environment variables)
- [ ] Includes unit tests for new functionality
- [ ] API endpoints match the documented specification
- [ ] No security vulnerabilities (SQL injection, XSS, etc.)
- [ ] PR description explains what was done and why

### 5.3 Development Workflow (Per Task)

```
1. Pick a task from the sprint board (GitHub Projects)
2. Move task to "In Progress"
3. Create feature branch: git checkout -b feature/<name>
4. Write code + tests
5. Run tests locally: mvn test / npm test / pytest
6. Commit with clear message: git commit -m "feat: add user registration endpoint"
7. Push branch: git push origin feature/<name>
8. Create Pull Request → assign reviewer
9. Address review comments
10. Merge after approval
11. Move task to "Done"
```

### 5.4 Commit Message Convention

Use **Conventional Commits** format:

```
<type>(<scope>): <short description>

Types:
  feat:     New feature
  fix:      Bug fix
  docs:     Documentation only
  style:    Formatting, no logic change
  refactor: Code restructuring
  test:     Adding or fixing tests
  chore:    Build scripts, CI/CD, configs

Examples:
  feat(auth): implement JWT login endpoint
  fix(project): fix null pointer on empty tech stack
  docs(readme): add setup instructions
  test(ml): add unit tests for SRS generator
  chore(docker): add docker-compose for local dev
```

---

## 6. Flow Diagrams

### 6.1 User Registration Flow

```
┌──────────┐
│  START   │
└────┬─────┘
     │
     ▼
┌──────────────┐     ┌──────────────┐
│ Visit        │────▶│ Click        │
│ Landing Page │     │ "Sign Up"    │
└──────────────┘     └──────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ Fill Form:    │
                    │ Name, Email,  │
                    │ Password      │
                    └──────┬────────┘
                           │
                           ▼
                    ┌───────────────┐     No     ┌──────────────┐
                    │ Valid Input?  │────────────▶│ Show Errors  │──┐
                    └──────┬────────┘             └──────────────┘  │
                           │ Yes                                     │
                           ▼                                         │
                    ┌───────────────┐                                │
                    │ Send POST     │◀───────────────────────────────┘
                    │ /auth/register│
                    └──────┬────────┘
                           │
                           ▼
                    ┌───────────────┐     No     ┌──────────────┐
                    │ Email Unique? │────────────▶│ Show "Email  │
                    └──────┬────────┘             │  exists"     │
                           │ Yes                  └──────────────┘
                           ▼
                    ┌───────────────┐
                    │ Create User   │
                    │ Send Email    │
                    └──────┬────────┘
                           │
                           ▼
                    ┌───────────────┐
                    │ Show Success: │
                    │ "Check email" │
                    └──────┬────────┘
                           │
                           ▼
                    ┌──────────┐
                    │   END    │
                    └──────────┘
```

### 6.2 Document Generation Flow

```
┌──────────┐
│  START   │
└────┬─────┘
     │
     ▼
┌──────────────────┐
│ User on Dashboard│
│ Clicks "New      │
│ Project"         │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ STEP 1:          │
│ Enter Project    │
│ Idea (text)      │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ STEP 2:          │
│ Select Project   │
│ Type (dropdown)  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ STEP 3:          │
│ Select Tech      │
│ Stack (optional) │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ STEP 4:          │
│ Enter Metadata   │
│ (name, team)     │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Click "Create &  │
│ Generate"        │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐      ┌──────────────────┐
│ Backend:         │      │ Show Loading:    │
│ Save project     │─────▶│ "Generating      │
│ Status=GENERATING│      │  your docs..."   │
└──────┬───────────┘      └──────────────────┘
       │
       ▼
┌──────────────────┐
│ Backend →        │
│ ML Service:      │
│ POST /generate   │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ ML Service:      │
│ LLM generates    │
│ 6 documents      │
│ (15-30 seconds)  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐     Failed    ┌──────────────────┐
│ Generation       │──────────────▶│ Show Error:      │
│ Successful?      │               │ "Generation      │
└──────┬───────────┘               │  failed. Retry?" │
       │ Yes                       └──────────────────┘
       ▼
┌──────────────────┐
│ Backend:         │
│ Save documents   │
│ + sections to DB │
│ Status=COMPLETE  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Frontend:        │
│ Display project  │
│ with 6 document  │
│ tabs             │
└──────┬───────────┘
       │
       ▼
┌──────────┐
│   END    │
└──────────┘
```

### 6.3 Document Export Flow

```
┌──────────┐
│  START   │
└────┬─────┘
     │
     ▼
┌──────────────────┐
│ User viewing a   │
│ generated doc    │
│ Clicks "Export"  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Modal appears:   │
│ Select format:   │
│ ○ PDF            │
│ ○ DOCX           │
│ ○ Markdown       │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Click "Download" │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Frontend:        │
│ GET /export/{fmt}│
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Backend:         │
│ Fetch document   │
│ sections from DB │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ ExportService:   │
│ Convert to       │
│ selected format  │
└──────┬───────────┘
       │
       ├── PDF  → OpenPDF library
       ├── DOCX → Apache POI library
       └── MD   → Return as-is (Markdown)
       │
       ▼
┌──────────────────┐
│ Return file as   │
│ byte stream      │
│ Content-Disp:    │
│ attachment       │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Browser triggers │
│ file download    │
└──────┬───────────┘
       │
       ▼
┌──────────┐
│   END    │
└──────────┘
```

### 6.4 Authentication Token Flow

```
┌──────────┐
│  START   │
└────┬─────┘
     │
     ▼
┌──────────────────┐
│ User Logs In     │
│ POST /auth/login │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐     No     ┌──────────────────┐
│ Valid Credentials│────────────▶│ Return 401       │
│ ?                │             │ "Invalid creds"  │
└──────┬───────────┘             └──────────────────┘
       │ Yes
       ▼
┌──────────────────┐
│ Generate:        │
│ - Access Token   │
│   (15 min expiry)│
│ - Refresh Token  │
│   (7 day expiry) │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ Store tokens:    │
│ Access →         │
│   localStorage   │
│ Refresh →        │
│   httpOnly cookie│
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ API Request:     │
│ Authorization:   │
│ Bearer <token>   │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐     No     ┌──────────────────┐
│ Token Valid?     │────────────▶│ Token Expired?   │
└──────┬───────────┘             └──────┬───────────┘
       │ Yes                            │ Yes
       ▼                                ▼
┌──────────────────┐            ┌──────────────────┐
│ Process Request  │            │ POST /auth/refresh│
│ Return Response  │            │ Send refresh token│
└──────────────────┘            └──────┬───────────┘
                                       │
                                       ▼
                                ┌──────────────────┐   Invalid
                                │ Refresh Valid?   │──────────▶ Redirect to Login
                                └──────┬───────────┘
                                       │ Valid
                                       ▼
                                ┌──────────────────┐
                                │ Issue new        │
                                │ Access Token     │
                                │ Retry request    │
                                └──────────────────┘
```

### 6.5 Overall System Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DATA FLOW OVERVIEW                           │
│                                                                     │
│  ┌─────────┐                                                        │
│  │   User  │                                                        │
│  └────┬────┘                                                        │
│       │ 1. Enters project description                               │
│       ▼                                                             │
│  ┌─────────────┐  2. POST /projects     ┌──────────────┐           │
│  │   Next.js   │──────────────────────▶│  Spring Boot  │           │
│  │  Frontend   │                        │   Backend     │           │
│  └─────────────┘                        └──────┬───────┘           │
│       ▲                                        │                    │
│       │ 8. Display                  3. Save    │ 4. POST /generate  │
│       │    documents                project    │                    │
│       │                                ▼       ▼                    │
│  ┌─────────────┐                ┌──────────────┐ ┌──────────────┐  │
│  │   Next.js   │                │  PostgreSQL  │ │  Python ML   │  │
│  │  Frontend   │                │  Database    │ │  Service     │  │
│  └─────────────┘                └──────────────┘ └──────┬───────┘  │
│       ▲                                ▲                │          │
│       │                                │                │          │
│       │ 7. GET /documents              │ 6. Save docs   │          │
│       │                                │    + sections  │          │
│       │                         ┌──────┴───────┐        │          │
│       │                         │  Spring Boot │◀───────┘          │
│       └─────────────────────────│   Backend    │ 5. Return         │
│                                 └──────────────┘    generated      │
│                                                     content        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 7. Risk Management

### 7.1 Risk Register

| #  | Risk                                    | Probability | Impact  | Mitigation Strategy                                    |
|----|-----------------------------------------|-------------|---------|--------------------------------------------------------|
| R1 | LLM generates low-quality output        | High        | High    | Invest in prompt engineering, test early, allow editing |
| R2 | Free cloud tier limits reached           | Medium      | Medium  | Monitor usage, optimize queries, cache results          |
| R3 | Team member drops or becomes inactive    | Medium      | High    | Cross-train team, document everything, pair programming |
| R4 | Integration issues between services      | Medium      | Medium  | Define API contracts early, integration testing weekly  |
| R5 | ML service too slow (>60s response)      | Medium      | Medium  | Use faster LLM (Groq), implement async generation      |
| R6 | Scope creep (adding too many features)   | High        | Medium  | Strict MoSCoW prioritization, stick to sprint goals    |
| R7 | Database schema changes late in project  | Low         | High    | Use Flyway migrations, design schema thoroughly in Phase 1 |
| R8 | Security vulnerabilities discovered      | Low         | High    | Follow OWASP guidelines, use Spring Security            |

### 7.2 MoSCoW Prioritization

| Priority       | Features                                                           |
|----------------|--------------------------------------------------------------------|
| **Must Have**  | Auth, Project CRUD, SRS generation, Use Case generation, ERD generation, Document viewer, PDF export |
| **Should Have**| API generation, Architecture generation, Presentation generation, DOCX export, Markdown export |
| **Could Have** | Admin panel, Analytics, Document editor, Regenerate section        |
| **Won't Have** | Real-time collaboration, Mobile app, Multi-language support, AI chatbot |

---

## 8. Milestones & Deliverables

### 8.1 Milestone Timeline

| Milestone | Week | Deliverable                                         | Checkpoint                    |
|-----------|------|-----------------------------------------------------|-------------------------------|
| M1        | 2    | SRS document complete                               | Supervisor review             |
| M2        | 4    | Architecture + ERD + API design complete             | Team review                   |
| M3        | 6    | Backend auth + CRUD working                          | Demo to supervisor            |
| M4        | 8    | ML service generating all 6 document types           | Quality review                |
| M5        | 10   | Frontend core pages complete                         | Internal demo                 |
| M6        | 12   | Full E2E integration working                         | End-to-end demo               |
| M7        | 14   | All features complete, deployed to cloud             | Pre-final review              |
| M8        | 16   | Final presentation and project submission            | **Final Evaluation**          |

### 8.2 Deliverable Checklist

- [ ] Source code (GitHub repository — clean, documented)
- [ ] SRS document (final version)
- [ ] Architecture diagram
- [ ] ERD diagram
- [ ] API documentation (Swagger)
- [ ] User manual / setup guide
- [ ] Docker Compose file for local setup
- [ ] Deployed application (live URL)
- [ ] Demo video (3–5 minutes)
- [ ] Presentation slides (15–20 slides)
- [ ] Progress reports (weekly or bi-weekly)

---

## 9. Tools & Resources

### 9.1 Development Tools (All Free)

| Category        | Tool                    | Purpose                               | URL                             |
|-----------------|-------------------------|---------------------------------------|---------------------------------|
| IDE             | IntelliJ IDEA Community | Java/Spring Boot development          | jetbrains.com                   |
| IDE             | VS Code                 | Frontend + Python development         | code.visualstudio.com           |
| Version Control | Git + GitHub            | Code hosting, PRs, project board      | github.com                      |
| Database GUI    | DBeaver (Community)     | PostgreSQL GUI                        | dbeaver.io                      |
| API Testing     | Postman / Thunder Client| Test REST APIs                        | postman.com                     |
| Design          | Figma (free tier)       | Wireframes and UI mockups             | figma.com                       |
| Diagrams        | draw.io / diagrams.net  | Architecture, ERD, flow diagrams      | diagrams.net                    |
| Containerization| Docker Desktop          | Local containerized development       | docker.com                      |
| AI/ML           | Ollama                  | Run LLMs locally                      | ollama.com                      |
| Documentation   | Notion / Google Docs    | Project documentation and notes       | notion.so                       |

### 9.2 Communication Tools

| Tool            | Purpose                                           |
|-----------------|---------------------------------------------------|
| Discord / Slack | Team communication (free)                         |
| Google Meet     | Virtual meetings (free)                            |
| GitHub Issues   | Bug tracking and task management                  |
| GitHub Projects | Kanban board for sprint management                |

---

## 10. Weekly Progress Tracking Template

### Template: Weekly Status Report

```
╔══════════════════════════════════════════════════════════╗
║            WEEKLY PROGRESS REPORT                       ║
║            Velocira — Week [#] of 16                     ║
║            Date: [DD/MM/YYYY]                            ║
╠══════════════════════════════════════════════════════════╣
║                                                          ║
║  SPRINT: [#] of 8                                        ║
║  PHASE:  [Current Phase Name]                            ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  COMPLETED THIS WEEK:                                    ║
║  ✅ [Task 1]                                             ║
║  ✅ [Task 2]                                             ║
║  ✅ [Task 3]                                             ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  IN PROGRESS:                                            ║
║  🔄 [Task 4] — 70% complete                             ║
║  🔄 [Task 5] — 30% complete                             ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  PLANNED FOR NEXT WEEK:                                  ║
║  📋 [Task 6]                                             ║
║  📋 [Task 7]                                             ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  BLOCKERS / CHALLENGES:                                  ║
║  ⚠️ [Blocker description and proposed solution]          ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  TEAM MEMBER CONTRIBUTIONS:                              ║
║  [Name 1]: [What they worked on]                         ║
║  [Name 2]: [What they worked on]                         ║
║  [Name 3]: [What they worked on]                         ║
║                                                          ║
╠══════════════════════════════════════════════════════════╣
║  OVERALL PROGRESS: [##]% complete                        ║
║  ON TRACK: ✅ Yes / ⚠️ At Risk / ❌ Behind              ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
```

### Progress Percentage by Week (Target)

| Week | Expected Progress | Checkpoint                |
|------|-------------------|---------------------------|
| 2    | 12%               | Planning complete          |
| 4    | 25%               | Design complete            |
| 6    | 37%               | Backend foundation done    |
| 8    | 50%               | ML service done            |
| 10   | 62%               | Frontend core done         |
| 12   | 75%               | Integration complete       |
| 14   | 87%               | Polish + docs complete     |
| 16   | 100%              | Presentation + submission  |

---

_End of Plan & Flow Document — Velocira v1.0_
