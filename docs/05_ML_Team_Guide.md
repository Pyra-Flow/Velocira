# ML Team Implementation Guide

## Velocira — AI-Powered Documentation SaaS Platform

| Field                     | Details                                      |
|---------------------------|----------------------------------------------|
| **Project Name**          | Velocira                                     |
| **Version**               | 1.0 (MVP)                                    |
| **Date**                  | February 2026                                |
| **Target Audience**       | ML/AI Engineering Team                       |
| **Purpose**               | Complete guide for ML team implementation     |

---

## Table of Contents

1. [ML Team Overview](#1-ml-team-overview)
2. [ML Team Responsibilities](#2-ml-team-responsibilities)
3. [Technology Stack for ML Team](#3-technology-stack-for-ml-team)
4. [ML Service Architecture](#4-ml-service-architecture)
5. [Features to Implement](#5-features-to-implement)
6. [Document Generation Requirements](#6-document-generation-requirements)
7. [Implementation Phases](#7-implementation-phases)
8. [Prompt Engineering Strategy](#8-prompt-engineering-strategy)
9. [Quality Assurance and Testing](#9-quality-assurance-and-testing)
10. [Integration with Backend](#10-integration-with-backend)
11. [Performance Optimization](#11-performance-optimization)
12. [Deployment Strategy](#12-deployment-strategy)
13. [Monitoring and Maintenance](#13-monitoring-and-maintenance)
14. [Common Challenges and Solutions](#14-common-challenges-and-solutions)
15. [Success Metrics](#15-success-metrics)
16. [Resources and References](#16-resources-and-references)

---

## 1. ML Team Overview

### 1.1 Team Composition

The ML team for Velocira consists of:

| Role                      | Count | Primary Focus                                  |
|---------------------------|-------|------------------------------------------------|
| **ML/AI Engineer Lead**   | 1     | Architecture, LLM selection, quality control   |
| **ML Engineer**           | 1-2   | Prompt engineering, implementation, testing    |
| **ML DevOps (Optional)**  | 0.5   | Model deployment, monitoring, optimization     |

### 1.2 Project Context

Velocira is an AI-powered SaaS platform that generates comprehensive project documentation from simple text descriptions. The ML team is responsible for building the intelligent core that transforms user ideas into production-ready documentation.

**Key Value Proposition:**
- Convert project ideas (50-2000 characters) into structured documentation
- Generate 6 document types: SRS, Use Cases, ERD, API Structure, Architecture, Implementation Roadmap
- Deliver results in under 30 seconds
- Ensure quality suitable for student projects and MVPs

### 1.3 Timeline

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Research & Setup | Weeks 1-2 | LLM evaluation, environment setup |
| Core Generators (MVP) | Weeks 3-6 | SRS, Use Cases, ERD, API generators |
| Quality & Testing | Weeks 7-8 | Prompt tuning, validation, optimization |
| Integration | Weeks 9-10 | Backend integration, end-to-end testing |
| Production Readiness | Weeks 11-12 | Performance tuning, monitoring setup |

---

## 2. ML Team Responsibilities

### 2.1 Core Responsibilities

#### Research and Evaluation
- Evaluate open-source LLM options (Ollama, Hugging Face models)
- Compare model quality vs. performance tradeoffs
- Assess cost implications for cloud-based LLM APIs
- Benchmark generation quality and latency

#### Development
- Build Python FastAPI service for document generation
- Implement 6 document generators with prompt engineering
- Create response parsing and validation logic
- Develop error handling and retry mechanisms
- Build health monitoring endpoints

#### Quality Assurance
- Design test cases for each document type
- Validate output structure and completeness
- Ensure consistency across different project types
- Test edge cases and error scenarios
- Conduct quality reviews on generated content

#### Integration
- Design API contracts with backend team
- Implement RESTful endpoints matching specifications
- Handle communication with Spring Boot backend
- Manage request/response format compliance

#### Optimization
- Optimize prompt engineering for faster responses
- Reduce token consumption without quality loss
- Implement caching strategies where applicable
- Profile and optimize service performance

### 2.2 Deliverables

| Week | Deliverable | Description |
|------|-------------|-------------|
| 2 | LLM Evaluation Report | Comparison of models with recommendations |
| 3 | ML Service Skeleton | FastAPI project structure with health checks |
| 4 | SRS Generator | Working SRS document generation |
| 5 | Use Case Generator | Actor and use case generation |
| 6 | ERD & API Generators | Entity-relationship and API endpoint generation |
| 7 | Quality Benchmark | Test results showing 80%+ quality score |
| 8 | Architecture & Roadmap Generators | V1.1 features (optional for MVP) |
| 10 | Integration Complete | End-to-end flow working with backend |
| 12 | Production Deployment | Service deployed with monitoring |

---

## 3. Technology Stack for ML Team

### 3.1 Core Technologies

#### Python Runtime
- **Version:** Python 3.10+
- **Why:** Modern async support, type hints, broad ML library ecosystem

#### FastAPI Framework
- **Version:** 0.100+
- **Why:** High performance, automatic API documentation, async support, easy integration

#### LLM Options (Choose One)

| Option | Pros | Cons | Recommended For |
|--------|------|------|-----------------|
| **Ollama (Local)** | Free, privacy, low latency | Requires local compute, limited model selection | Development, testing |
| **OpenAI API** | High quality, GPT-4 access | Paid ($$$), usage limits | Production (premium tier) |
| **Hugging Face Inference** | Many models, free tier available | Variable quality, rate limits | MVP testing |
| **Anthropic Claude** | Excellent instruction following | Paid ($$), newer | Production alternative |
| **Mistral AI** | Good quality, affordable | Smaller context window | Production (budget) |

**MVP Recommendation:** Start with **Ollama + Llama 3** for development, evaluate **Mistral AI** or **OpenAI** for production.

#### Supporting Libraries

```python
# Core ML/AI
langchain==0.1.0              # LLM orchestration
langchain-community==0.1.0    # Community integrations

# API and Web
fastapi==0.110.0              # Web framework
uvicorn[standard]==0.27.0     # ASGI server
pydantic==2.6.0               # Data validation

# HTTP and Integration
httpx==0.27.0                 # Async HTTP client
python-dotenv==1.0.0          # Environment management

# Utilities
pyyaml==6.0                   # YAML parsing for prompts
jinja2==3.1.3                 # Template rendering
python-multipart==0.0.9       # File upload support

# Testing
pytest==8.0.0                 # Testing framework
pytest-asyncio==0.23.0        # Async test support
```

### 3.2 Development Tools

| Tool | Purpose | Installation |
|------|---------|--------------|
| **VS Code** | IDE with Python extensions | https://code.visualstudio.com/ |
| **Postman** | API testing | https://www.postman.com/ |
| **Ollama** | Local LLM runtime | https://ollama.com/ |
| **Docker** | Containerization | https://www.docker.com/ |
| **Git** | Version control | https://git-scm.com/ |

### 3.3 VS Code Extensions

```
- Python (Microsoft)
- Pylance
- Python Debugger
- autoDocstring
- YAML
- REST Client
- Docker
```

---

## 4. ML Service Architecture

### 4.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FastAPI ML Service                        │
│                                                              │
│  ┌────────────────┐      ┌──────────────────────────────┐  │
│  │   API Layer    │      │   Generation Orchestrator     │  │
│  │  (routes.py)   │─────▶│  (generation_service.py)      │  │
│  └────────────────┘      └──────────┬───────────────────┘  │
│                                     │                        │
│           ┌─────────────────────────┼─────────────────┐     │
│           │                         │                 │     │
│           ▼                         ▼                 ▼     │
│  ┌─────────────────┐    ┌──────────────┐  ┌─────────────┐ │
│  │  SRS Generator  │    │Use Case Gen. │  │ ERD Gen.    │ │
│  └────────┬────────┘    └──────┬───────┘  └──────┬──────┘ │
│           │                    │                  │         │
│           │                    │                  │         │
│           └────────────────────┼──────────────────┘         │
│                                │                            │
│                         ┌──────▼────────┐                  │
│                         │  LLM Provider  │                  │
│                         │   (Ollama/API) │                  │
│                         └────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
         ▲                                        │
         │                                        │
         │ HTTP POST /generate                    │
         │ (JSON Request)                         │
         │                                        │
         │                                        ▼
┌────────┴──────────┐                    ┌──────────────────┐
│  Spring Boot      │                    │   Response       │
│  Backend API      │◀───────────────────│   (JSON)         │
└───────────────────┘                    └──────────────────┘
```

### 4.2 Service Components

#### API Layer (routes.py)
- **Purpose:** Handle HTTP requests from backend
- **Endpoints:** `/health`, `/generate`, `/generate/{docType}`
- **Validation:** Input validation using Pydantic models
- **Error Handling:** HTTP status codes, error messages

#### Generation Orchestrator (generation_service.py)
- **Purpose:** Coordinate document generation workflow
- **Responsibilities:**
  - Route requests to appropriate generators
  - Manage parallel vs. sequential generation
  - Aggregate results
  - Handle generator failures

#### Document Generators
Each generator is responsible for one document type:
- **SRS Generator:** Software Requirements Specification
- **Use Case Generator:** Actors, use cases, flows
- **ERD Generator:** Entities, attributes, relationships
- **API Generator:** REST endpoints, payloads
- **Architecture Generator:** System design, tech choices (V1.1)
- **Roadmap Generator:** Implementation phases, timeline (V1.1)

#### LLM Provider Layer
- **Purpose:** Abstract LLM provider interactions
- **Implementations:**
  - OllamaProvider (local)
  - OpenAIProvider (cloud)
  - HuggingFaceProvider (cloud)
  - AnthropicProvider (cloud)
- **Capabilities:** Prompt submission, response retrieval, error handling

#### Response Parser
- **Purpose:** Extract structured data from LLM text output
- **Techniques:**
  - Markdown parsing
  - JSON extraction
  - Section identification
  - Validation against schema

### 4.3 Data Flow

```
1. Backend sends request to ML Service
   POST /api/v1/generate
   {
     "projectId": 42,
     "ideaText": "A mobile app for...",
     "projectType": "MOBILE_APP",
     "techStack": {...}
   }

2. API Layer validates request → passes to Orchestrator

3. Orchestrator determines which generators to run

4. For each document type:
   a. Generator builds prompt from template
   b. LLM Provider sends prompt to model
   c. Model generates text response
   d. Parser extracts structured sections
   e. Validator checks completeness

5. Orchestrator aggregates all results

6. Response returned to backend
   {
     "projectId": 42,
     "status": "success",
     "documents": [
       {
         "docType": "SRS",
         "sections": [...]
       },
       ...
     ]
   }
```

---

## 5. Features to Implement

### 5.1 MVP Features (V1.0) — REQUIRED

#### Feature 1: SRS Document Generation

**Priority:** HIGH  
**Timeline:** Week 4  

**Requirements:**
- Generate complete Software Requirements Specification
- Include all standard SRS sections
- Adapt content based on project type
- Output structured Markdown format
- Generation time: < 10 seconds

**Expected Sections:**
1. Introduction (Purpose, Scope, Definitions)
2. Overall Description (Product Perspective, Features)
3. Functional Requirements (FR-1, FR-2, etc.)
4. Non-Functional Requirements (Performance, Security)
5. System Interfaces
6. Constraints
7. Assumptions & Dependencies

**Input Parameters:**
- Project idea text
- Project type (Web App, Mobile App, etc.)
- Tech stack (optional)
- Team size (optional)

**Output Format:**
```json
{
  "docType": "SRS",
  "sections": [
    {
      "title": "Introduction",
      "order": 1,
      "content": "## 1. Introduction\n\n### 1.1 Purpose\n..."
    },
    ...
  ]
}
```

---

#### Feature 2: Use Case Generation

**Priority:** HIGH  
**Timeline:** Week 5  

**Requirements:**
- Identify system actors (User, Admin, Guest, etc.)
- Generate comprehensive use case list
- Create detailed use case descriptions
- Include main flow, alternative flows
- Generate use case diagram description

**Expected Outputs:**
1. **Actor List:** Primary actors, secondary actors, system actors
2. **Use Case Catalog:** UC-1, UC-2, etc. with descriptions
3. **Detailed Use Cases:**
   - Use Case ID and Name
   - Actor
   - Preconditions
   - Main Flow (steps)
   - Alternative Flows
   - Postconditions
4. **Use Case Diagram Description:** Actor-Use Case relationships

**Input Parameters:**
- Project idea text
- Project type
- Functional requirements (from SRS)

---

#### Feature 3: ERD (Entity-Relationship Diagram) Generation

**Priority:** HIGH  
**Timeline:** Week 6  

**Requirements:**
- Identify all entities relevant to the project
- Define attributes with data types
- Establish relationships (1-1, 1-M, M-M)
- Specify cardinality and constraints
- Generate textual ERD representation

**Expected Outputs:**
1. **Entity List:**
   - Entity name
   - Attributes with types
   - Primary key
   - Constraints
2. **Relationship List:**
   - Source entity
   - Target entity
   - Relationship type
   - Cardinality
3. **ERD Diagram Description:** Mermaid or PlantUML format
4. **SQL Schema Draft:** CREATE TABLE statements (optional)

**Input Parameters:**
- Project idea text
- Project type
- Functional requirements

---

#### Feature 4: API Structure Generation

**Priority:** HIGH  
**Timeline:** Week 6  

**Requirements:**
- Generate RESTful API endpoint list
- Group endpoints by resource
- Provide request/response examples
- Suggest authentication strategy
- Follow REST best practices

**Expected Outputs:**
1. **Endpoint Catalog:**
   - HTTP Method (GET, POST, PUT, DELETE)
   - URL Pattern
   - Description
   - Authentication required?
2. **Request/Response Examples:**
   - Sample JSON request body
   - Sample JSON response
   - Status codes
3. **Authentication Recommendation:**
   - JWT, OAuth2, API Key, etc.
   - Implementation guidance
4. **API Documentation Structure:**
   - Resource grouping
   - Error responses
   - Rate limiting notes

**Input Parameters:**
- Project idea text
- Project type
- Entities (from ERD)
- Use cases

---

### 5.2 Future Features (V1.1+) — OPTIONAL

#### Feature 5: Architecture Proposal Generation

**Priority:** MEDIUM  
**Timeline:** Week 8 (Post-MVP)  

**Requirements:**
- Suggest architecture pattern (Monolith, Microservices, Serverless)
- Recommend technology stack (if not provided)
- Propose deployment structure
- Consider scalability and cost

**Expected Outputs:**
1. Recommended architecture pattern with justification
2. Technology stack breakdown (Frontend, Backend, Database, Infra)
3. Deployment diagram description
4. Scalability considerations
5. Security architecture notes

---

#### Feature 6: Implementation Roadmap Generation

**Priority:** MEDIUM  
**Timeline:** Week 8 (Post-MVP)  

**Requirements:**
- Generate phased implementation plan
- Break down into sprints/milestones
- Assign estimated timelines
- Identify risks and dependencies

**Expected Outputs:**
1. Phase breakdown (Phase 1, 2, 3, etc.)
2. Sprint plan with goals
3. Task assignments by role
4. Timeline (Gantt chart description)
5. Risk assessment
6. Team composition recommendations

---

### 5.3 Feature Priority Matrix

| Feature | Priority | MVP Status | Complexity | Impact |
|---------|----------|------------|------------|--------|
| SRS Generation | HIGH | ✅ Required | High | Critical |
| Use Case Generation | HIGH | ✅ Required | Medium | Critical |
| ERD Generation | HIGH | ✅ Required | Medium | Critical |
| API Structure | HIGH | ✅ Required | Medium | Critical |
| Architecture Proposal | MEDIUM | 🔜 V1.1 | Medium | High |
| Roadmap Generation | MEDIUM | 🔜 V1.1 | High | High |
| Multi-language Support | LOW | 🔜 V2.0 | High | Medium |

---

## 6. Document Generation Requirements

### 6.1 Quality Standards

All generated documents must meet these quality criteria:

#### Completeness
- ✅ All required sections present
- ✅ No placeholder text (e.g., "[Insert details here]")
- ✅ Sufficient detail for the target audience (students, MVPs)

#### Accuracy
- ✅ Technically sound recommendations
- ✅ Consistent with project type and tech stack
- ✅ Realistic constraints and assumptions

#### Structure
- ✅ Proper Markdown formatting
- ✅ Hierarchical section organization
- ✅ Tables and lists where appropriate
- ✅ Clear headings and numbering

#### Coherence
- ✅ Consistent terminology across sections
- ✅ Logical flow of ideas
- ✅ Cross-references between related items

#### Relevance
- ✅ Tailored to specific project type
- ✅ Appropriate scope (not too generic, not too specific)
- ✅ Aligned with modern software development practices

### 6.2 Input Validation Requirements

Before processing, validate all inputs:

| Field | Validation Rule |
|-------|-----------------|
| `ideaText` | 50-2000 characters, non-empty |
| `projectType` | Enum: WEB_APP, MOBILE_APP, AI_SYSTEM, IOT, DESKTOP_APP, API_SERVICE |
| `techStack` | Optional object with backend, frontend, database fields |
| `projectName` | 1-200 characters |
| `teamSize` | 1-50 (if provided) |

**Error Handling:**
- Return 400 Bad Request with clear error message
- Include field name and constraint violated
- Suggest valid values

### 6.3 Output Format Specification

#### Response Structure

```json
{
  "projectId": 42,
  "status": "success",
  "generationTimeMs": 25340,
  "documents": [
    {
      "docType": "SRS",
      "status": "completed",
      "sections": [
        {
          "title": "Introduction",
          "order": 1,
          "content": "Markdown content here..."
        }
      ]
    }
  ],
  "warnings": [],
  "metadata": {
    "modelUsed": "llama3",
    "tokensConsumed": 3500,
    "promptVersion": "v1.2"
  }
}
```

#### Section Structure

Each section must have:
- **title:** Human-readable section name
- **order:** Numeric position in document
- **content:** Markdown-formatted text

#### Markdown Guidelines

```markdown
# Use H1 for document title (if standalone)
## Use H2 for main sections
### Use H3 for subsections

- Use bullet lists for features
- Use numbered lists for steps

| Column 1 | Column 2 |
|----------|----------|
| Use tables for structured data |

**Bold** for emphasis
*Italic* for technical terms
`Code` for inline code/commands

Three backticks for code blocks
```

### 6.4 Performance Requirements

| Metric | Target | Acceptable | Unacceptable |
|--------|--------|------------|--------------|
| Single document generation | < 10s | 10-15s | > 15s |
| Full generation (4 docs) | < 30s | 30-45s | > 45s |
| API response time | < 500ms | 500ms-1s | > 1s |
| Concurrent requests | 10+ | 5-10 | < 5 |
| Uptime | 99%+ | 95-99% | < 95% |

---

## 7. Implementation Phases

### 7.1 Phase 1: Research & Environment Setup (Weeks 1-2)

#### Week 1: LLM Evaluation

**Objectives:**
- Evaluate 3-5 LLM options
- Test generation quality
- Measure latency and cost
- Make final LLM selection

**Tasks:**
1. Set up Ollama with Llama 3, Mistral, and Gemma models
2. Create test prompts for SRS generation
3. Compare outputs across models
4. Benchmark generation time
5. Calculate cost estimates for cloud APIs
6. Document findings in evaluation report

**Deliverable:** LLM Evaluation Report with recommendation

**Success Criteria:**
- ✅ At least 3 models tested
- ✅ Quality scored on 1-10 scale
- ✅ Latency measured for each
- ✅ Clear recommendation with justification

---

#### Week 2: Project Setup

**Objectives:**
- Set up Python FastAPI project structure
- Configure development environment
- Establish coding standards
- Integrate with version control

**Tasks:**
1. Create `ml-service/` directory structure
2. Initialize Python virtual environment
3. Install dependencies (FastAPI, LangChain, etc.)
4. Create `requirements.txt`
5. Set up `.env` configuration
6. Create Docker containerization (optional)
7. Implement health check endpoint
8. Write README with setup instructions

**Deliverable:** Working FastAPI skeleton with health endpoint

**Success Criteria:**
- ✅ Server starts on `http://localhost:8000`
- ✅ `/health` endpoint returns 200 OK
- ✅ All dependencies installed correctly
- ✅ Git repository configured

---

### 7.2 Phase 2: Core Generators (Weeks 3-6)

#### Week 3-4: SRS Generator

**Objectives:**
- Implement complete SRS document generation
- Build prompt template with variables
- Parse LLM output into structured sections
- Validate output quality

**Tasks:**
1. Design SRS prompt template structure
2. Implement `srs_generator.py`
3. Create prompt variables injection
4. Test with 10+ different project ideas
5. Refine prompts based on output quality
6. Implement section parser
7. Add validation logic
8. Write unit tests

**Deliverable:** Working SRS generator with 80%+ quality score

---

#### Week 5: Use Case Generator

**Objectives:**
- Generate actors and use cases
- Create detailed use case descriptions
- Format output consistently

**Tasks:**
1. Design use case prompt template
2. Implement `usecase_generator.py`
3. Parse actor list from LLM output
4. Extract use case catalog
5. Generate detailed use case flows
6. Test with various project types
7. Refine prompts for consistency
8. Add validation and testing

**Deliverable:** Use Case generator with complete outputs

---

#### Week 6: ERD & API Generators

**Objectives:**
- Generate entity-relationship models
- Create API endpoint specifications
- Ensure consistency across document types

**Tasks:**

**ERD Generator:**
1. Design ERD prompt template
2. Implement `erd_generator.py`
3. Parse entities and attributes
4. Extract relationships
5. Generate ERD diagram description (Mermaid/PlantUML)
6. Test with backend/database-heavy projects

**API Generator:**
1. Design API prompt template
2. Implement `api_generator.py`
3. Generate endpoint list with HTTP methods
4. Create request/response examples
5. Add authentication recommendations
6. Test with various application types

**Deliverable:** Both generators working end-to-end

---

### 7.3 Phase 3: Integration & Testing (Weeks 7-10)

#### Week 7-8: Quality Assurance

**Objectives:**
- Achieve 80%+ quality benchmark across all generators
- Implement comprehensive testing
- Refine prompts based on edge cases

**Tasks:**
1. Create quality scoring rubric
2. Test each generator with 20+ diverse inputs
3. Score outputs using rubric
4. Identify common failure patterns
5. Refine prompts to address failures
6. Implement retry logic for errors
7. Add input validation
8. Write integration tests

**Deliverable:** Quality benchmark report showing 80%+ scores

---

#### Week 9-10: Backend Integration

**Objectives:**
- Integrate ML service with Spring Boot backend
- Implement API contract
- Test end-to-end flow
- Handle errors gracefully

**Tasks:**
1. Coordinate with backend team on API contract
2. Implement `/generate` endpoint
3. Implement `/generate/{docType}` endpoint
4. Add request validation
5. Format responses according to contract
6. Test integration with backend locally
7. Deploy to staging environment
8. Conduct end-to-end testing
9. Fix integration issues

**Deliverable:** ML service fully integrated with backend

---

### 7.4 Phase 4: Production Readiness (Weeks 11-12)

#### Week 11: Performance Optimization

**Objectives:**
- Reduce generation latency to < 30s for full generation
- Optimize resource usage
- Implement caching where appropriate

**Tasks:**
1. Profile service performance
2. Identify bottlenecks
3. Optimize prompt length
4. Implement prompt caching
5. Add connection pooling
6. Optimize JSON parsing
7. Load test with concurrent requests
8. Document performance metrics

**Deliverable:** Optimized service meeting performance targets

---

#### Week 12: Monitoring & Launch Prep

**Objectives:**
- Set up monitoring and logging
- Prepare for production deployment
- Document operations procedures

**Tasks:**
1. Implement structured logging
2. Add error tracking (Sentry)
3. Create monitoring dashboard
4. Set up alerting rules
5. Write deployment documentation
6. Create runbook for common issues
7. Conduct security review
8. Deploy to production
9. Monitor initial production traffic

**Deliverable:** Production-ready ML service with monitoring

---

## 8. Prompt Engineering Strategy

### 8.1 Prompt Design Principles

#### Principle 1: Be Specific and Detailed

**Bad Prompt:**
```
Generate an SRS document for this project.
```

**Good Prompt:**
```
You are an expert software architect. Generate a comprehensive Software Requirements Specification (SRS) document following IEEE 830 standards for the following project:

Project Idea: {idea_text}
Project Type: {project_type}
Tech Stack: {tech_stack}

The SRS must include these sections:
1. Introduction (Purpose, Scope, Definitions)
2. Overall Description
3. Functional Requirements (with FR-IDs)
4. Non-Functional Requirements
5. System Interfaces
6. Constraints
7. Assumptions & Dependencies

Format each section using Markdown with proper headers (##, ###).
Be specific, avoid placeholders, and provide realistic requirements.
```

---

#### Principle 2: Provide Context and Examples

Include examples in prompts to guide the model:

```
Example Functional Requirement:

| Field          | Details                                                    |
|----------------|------------------------------------------------------------|
| **ID**         | FR-1.1                                                     |
| **Priority**   | High                                                       |
| **Description**| The system shall allow users to register using email...    |
| **Input**      | Full name, email, password                                 |
| **Output**     | User account created, verification email sent              |

Now generate similar requirements for this project...
```

---

#### Principle 3: Use Structured Output Instructions

Request specific formatting:

```
Output the entity list in this exact format:

### Entities

1. **User**
   - Attributes: id (UUID), email (String), name (String), created_at (DateTime)
   - Primary Key: id
   - Constraints: email unique, not null

2. **Project**
   - Attributes: ...
```

---

#### Principle 4: Chain of Thought

Break complex tasks into steps:

```
First, identify the main actors in the system.
Then, for each actor, list their primary goals.
Next, convert each goal into a use case.
Finally, write detailed flows for each use case.
```

---

#### Principle 5: Handle Edge Cases

Address potential issues explicitly:

```
If the project idea is vague, make reasonable assumptions and state them.
If tech stack is not specified, recommend modern, popular choices.
If the project type is unclear, default to Web Application.
```

---

### 8.2 Prompt Templates

#### Template Structure

```python
SRS_PROMPT_TEMPLATE = """
{system_role}

{task_description}

{input_data}

{output_format}

{constraints}

{examples}
"""
```

#### Example: SRS Prompt Template

```python
SRS_PROMPT = """
You are an expert software requirements engineer with 10+ years of experience writing SRS documents for startups and MVPs.

Generate a comprehensive Software Requirements Specification (SRS) document following IEEE 830 standards.

PROJECT INFORMATION:
- Project Name: {project_name}
- Project Idea: {idea_text}
- Project Type: {project_type}
- Tech Stack: {tech_stack}
- Team Size: {team_size}

REQUIRED SECTIONS:
1. Introduction
   - Purpose
   - Scope
   - Definitions and Acronyms
   - Overview
2. Overall Description
   - Product Perspective
   - Product Features
   - User Classes
   - Operating Environment
   - Design Constraints
3. System Features & Functional Requirements
   - List all functional requirements with IDs (FR-1.1, FR-1.2, etc.)
   - Use tables for structured data
4. Non-Functional Requirements
   - Performance
   - Security
   - Usability
   - Reliability
5. System Interfaces
6. Constraints
7. Assumptions & Dependencies

OUTPUT FORMAT:
- Use Markdown formatting
- Use ## for main sections, ### for subsections
- Use tables for requirements
- Be specific and avoid generic statements
- Provide 15-20 detailed functional requirements
- Ensure requirements are testable and realistic

CONSTRAINTS:
- Length: 2000-4000 words
- Target audience: Technical team and stakeholders
- Scope: MVP (Minimum Viable Product)
- Avoid placeholder text like "[Insert details]"

Generate the complete SRS document now.
"""
```

---

### 8.3 Prompt Optimization Techniques

#### Technique 1: Few-Shot Learning

Provide 1-3 examples before asking for the target output:

```
Example 1:
Input: "A todo list app for students"
Output: [show example SRS]

Example 2:
Input: "An e-commerce platform for local farmers"
Output: [show example SRS]

Now generate for:
Input: "{user_input}"
Output:
```

---

#### Technique 2: Role Playing

Assign expertise to the model:

```
You are a senior software architect at Google with expertise in distributed systems.
You are a UX researcher specializing in mobile applications.
You are a database designer with 15 years of experience in e-commerce.
```

---

#### Technique 3: Constrained Generation

Limit creativity where needed:

```
Use ONLY the following project types: [WEB_APP, MOBILE_APP, API_SERVICE]
Do NOT suggest technologies outside of: [Java, Python, JavaScript, PostgreSQL, MongoDB]
Limit the SRS to exactly 7 sections, no more, no less.
```

---

#### Technique 4: Self-Critique

Ask the model to review its own output:

```
First, generate the SRS document.
Then, review it for completeness and accuracy.
Finally, revise any sections that are too generic or contain placeholders.
```

---

### 8.4 Managing Prompt Versions

Maintain prompt templates in version control:

```
prompts/
├── srs_prompt_v1.0.txt
├── srs_prompt_v1.1.txt    # Improved based on feedback
├── srs_prompt_v1.2.txt    # Added edge case handling
├── usecase_prompt_v1.0.txt
├── erd_prompt_v1.0.txt
└── api_prompt_v1.0.txt
```

Track which version is used in production:

```python
PROMPT_VERSION = "v1.2"

metadata = {
    "promptVersion": PROMPT_VERSION,
    "modelUsed": "llama3",
    "generatedAt": datetime.now()
}
```

---

## 9. Quality Assurance and Testing

### 9.1 Testing Strategy

#### Unit Tests

Test individual components:

```python
# test_srs_generator.py

def test_srs_generator_with_web_app():
    """Test SRS generation for web application"""
    input_data = {
        "ideaText": "A web app for managing university courses",
        "projectType": "WEB_APP",
        "techStack": {"backend": "Spring Boot", "frontend": "React"}
    }
    
    result = srs_generator.generate(input_data)
    
    assert result["docType"] == "SRS"
    assert len(result["sections"]) >= 7
    assert any("Introduction" in s["title"] for s in result["sections"])
    assert any("Functional Requirements" in s["title"] for s in result["sections"])
```

---

#### Integration Tests

Test end-to-end API flow:

```python
# test_api.py

@pytest.mark.asyncio
async def test_generate_endpoint():
    """Test full document generation endpoint"""
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.post("/generate", json={
            "projectId": 1,
            "ideaText": "A mobile app for fitness tracking",
            "projectType": "MOBILE_APP",
            "docTypes": ["SRS", "USE_CASE", "ERD", "API"]
        })
    
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert len(data["documents"]) == 4
```

---

#### Quality Tests

Validate output quality:

```python
def test_srs_quality():
    """Validate SRS meets quality standards"""
    result = generate_srs(test_input)
    
    # Check completeness
    assert has_all_required_sections(result)
    
    # Check for placeholders
    assert not contains_placeholders(result["content"])
    
    # Check length
    word_count = count_words(result["content"])
    assert 2000 <= word_count <= 5000
    
    # Check structure
    assert is_valid_markdown(result["content"])
```

---

### 9.2 Quality Scoring Rubric

Score each generated document on a scale of 1-10:

| Criterion | Weight | Description | Score 1-10 |
|-----------|--------|-------------|------------|
| **Completeness** | 25% | All required sections present, no missing content | ___ / 10 |
| **Accuracy** | 20% | Technical correctness, realistic requirements | ___ / 10 |
| **Specificity** | 20% | Detailed, project-specific content (not generic) | ___ / 10 |
| **Structure** | 15% | Proper formatting, hierarchy, readability | ___ / 10 |
| **Coherence** | 10% | Logical flow, consistent terminology | ___ / 10 |
| **Relevance** | 10% | Appropriate scope, aligned with project type | ___ / 10 |

**Total Score:** (Weighted Average) ___ / 10

**Quality Tiers:**
- 9-10: Excellent (Production-ready)
- 7-8: Good (Minor refinements needed)
- 5-6: Fair (Significant improvements needed)
- 1-4: Poor (Regenerate required)

**MVP Target:** Average score ≥ 8.0 across all document types

---

### 9.3 Test Data Sets

Create diverse test cases covering:

#### Project Types
- Web Application (SaaS, E-commerce, Social Network)
- Mobile Application (iOS, Android, Cross-platform)
- AI System (Chatbot, Recommendation Engine, Image Recognition)
- IoT (Smart Home, Industrial Monitoring)
- Desktop Application (Productivity, Creative Tools)
- API/Backend Service (RESTful API, GraphQL)

#### Project Scales
- Small (1-2 person team, simple MVP)
- Medium (3-5 person team, moderate complexity)
- Large (6+ person team, enterprise features)

#### Tech Stack Varieties
- Modern JS (React, Node.js, MongoDB)
- Java Enterprise (Spring Boot, PostgreSQL)
- Python Data Science (FastAPI, TensorFlow, PostgreSQL)
- Mobile Native (Swift, Kotlin)
- Microservices (Docker, Kubernetes, RabbitMQ)

#### Edge Cases
- Very short idea (50 characters)
- Very long idea (2000 characters)
- Vague idea ("make an app for students")
- Highly technical idea (specific algorithms, protocols)
- No tech stack specified
- Unusual project type combinations

---

### 9.4 Continuous Testing Process

```
┌─────────────────────────────────────────────────────────┐
│                  Development Cycle                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. Write/Modify Prompt                                 │
│  2. Run Unit Tests → Pass?                              │
│  3. Generate Sample Outputs (10+ test cases)            │
│  4. Score Quality (using rubric)                        │
│  5. Average Score ≥ 8.0? ────┐                          │
│        │                      │                          │
│        NO                    YES                         │
│        │                      │                          │
│  6. Refine Prompt ◄──────────┘                          │
│  7. Repeat steps 2-5                                    │
│  8. Commit to version control                           │
│  9. Deploy to staging                                   │
│ 10. Integration tests → Pass?                           │
│ 11. Deploy to production                                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 10. Integration with Backend

### 10.1 API Contract

The ML service exposes RESTful endpoints consumed by the Spring Boot backend.

#### Endpoint 1: Health Check

**Request:**
```
GET /health
```

**Response:**
```json
{
  "status": "healthy",
  "service": "velocira-ml",
  "version": "1.0.0",
  "uptime": 3600
}
```

---

#### Endpoint 2: Generate All Documents

**Request:**
```
POST /generate
Content-Type: application/json

{
  "projectId": 42,
  "projectName": "Smart Library System",
  "ideaText": "A web application that helps libraries manage books, track inventory, and recommend books to students based on their reading history.",
  "projectType": "WEB_APP",
  "techStack": {
    "backend": "Spring Boot",
    "frontend": "React",
    "database": "PostgreSQL"
  },
  "teamSize": 4,
  "docTypes": ["SRS", "USE_CASE", "ERD", "API"]
}
```

**Response:**
```json
{
  "projectId": 42,
  "status": "success",
  "generationTimeMs": 28450,
  "documents": [
    {
      "docType": "SRS",
      "status": "completed",
      "sections": [
        {
          "title": "Introduction",
          "order": 1,
          "content": "## 1. Introduction\n\n### 1.1 Purpose..."
        },
        {
          "title": "Scope",
          "order": 2,
          "content": "## 2. Scope\n\nThe Smart Library System..."
        }
      ]
    },
    {
      "docType": "USE_CASE",
      "status": "completed",
      "sections": [...]
    }
  ],
  "warnings": [],
  "metadata": {
    "modelUsed": "llama3",
    "tokensConsumed": 4200,
    "promptVersion": "v1.2"
  }
}
```

---

#### Endpoint 3: Generate Single Document Type

**Request:**
```
POST /generate/srs
Content-Type: application/json

{
  "projectId": 42,
  "projectName": "Smart Library System",
  "ideaText": "...",
  "projectType": "WEB_APP",
  "techStack": {...}
}
```

**Response:**
```json
{
  "projectId": 42,
  "status": "success",
  "generationTimeMs": 9200,
  "documents": [
    {
      "docType": "SRS",
      "status": "completed",
      "sections": [...]
    }
  ]
}
```

---

### 10.2 Error Handling

#### Client Errors (4xx)

```json
// 400 Bad Request
{
  "status": "error",
  "message": "Validation failed",
  "errors": [
    {
      "field": "ideaText",
      "message": "Idea text must be between 50 and 2000 characters"
    }
  ]
}

// 404 Not Found
{
  "status": "error",
  "message": "Document type 'INVALID_TYPE' not supported"
}
```

#### Server Errors (5xx)

```json
// 500 Internal Server Error
{
  "status": "error",
  "message": "Document generation failed",
  "details": "LLM service timeout after 30 seconds",
  "requestId": "req_abc123"
}

// 503 Service Unavailable
{
  "status": "error",
  "message": "LLM provider temporarily unavailable",
  "retryAfter": 60
}
```

---

### 10.3 Communication Flow

```
┌──────────────────┐                    ┌──────────────────┐
│                  │                    │                  │
│  Spring Boot     │                    │   ML Service     │
│  Backend         │                    │   (FastAPI)      │
│                  │                    │                  │
└────────┬─────────┘                    └────────┬─────────┘
         │                                       │
         │  1. User creates project              │
         │                                       │
         │  2. POST /projects/{id}/generate      │
         │  ────────────────────────────────────▶│
         │                                       │
         │                                3. Validate input
         │                                4. Build prompts
         │                                5. Call LLM
         │                                6. Parse responses
         │                                7. Validate outputs
         │                                       │
         │  8. Return generated documents        │
         │  ◀────────────────────────────────────│
         │                                       │
         │  9. Save documents to database        │
         │ 10. Update project status             │
         │                                       │
         │ 11. Return success to frontend        │
         │                                       │
```

---

### 10.4 Timeout and Retry Strategy

#### Backend Configuration

The Spring Boot backend should:
- Set HTTP timeout to **45 seconds** for ML service calls
- Implement **retry logic** with exponential backoff (max 2 retries)
- Fall back to error state if all retries fail

#### ML Service Configuration

The ML service should:
- Set LLM timeout to **30 seconds** per document
- Return partial results if some documents succeed
- Log failures for debugging

---

### 10.5 Testing Integration Locally

#### Setup

1. Start PostgreSQL database
2. Start Spring Boot backend on port 8080
3. Start ML service on port 8000
4. Configure backend to point to `http://localhost:8000`

#### Test Flow

```bash
# 1. Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","fullName":"Test User"}'

# 2. Login and get token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

# 3. Create project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Project","ideaText":"A simple todo app","projectType":"WEB_APP"}'

# 4. Trigger generation
curl -X POST http://localhost:8080/api/v1/projects/1/generate \
  -H "Authorization: Bearer <token>"

# 5. Check ML service directly
curl -X POST http://localhost:8000/generate \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"ideaText":"A todo app","projectType":"WEB_APP","docTypes":["SRS"]}'
```

---

## 11. Performance Optimization

### 11.1 Performance Targets

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Single doc generation | < 10s | Time from request to response |
| Full generation (4 docs) | < 30s | End-to-end latency |
| Concurrent requests | 10+ | Load test with Apache Bench |
| Memory usage | < 2GB | Monitor with `htop` or Docker stats |
| CPU usage | < 80% | Monitor during generation |

---

### 11.2 Optimization Techniques

#### Technique 1: Prompt Optimization

**Problem:** Long prompts increase token usage and latency  
**Solution:** Remove unnecessary examples, reduce verbosity

**Before:**
```
You are an expert software requirements engineer with 10+ years of experience writing SRS documents for startups, enterprises, and MVPs. You have deep knowledge of IEEE 830 standards, agile methodologies, and modern software development practices...
(500 words of instructions)
```

**After:**
```
You are an expert software requirements engineer. Generate an SRS document following IEEE 830 standards for this project:
(200 words of instructions)
```

**Impact:** 30-40% reduction in token usage, 20-30% faster generation

---

#### Technique 2: Parallel Generation

**Problem:** Sequential generation of 4 documents takes 4x longer  
**Solution:** Generate documents in parallel using async/await

```python
import asyncio

async def generate_all_documents(request):
    tasks = [
        generate_srs(request),
        generate_use_cases(request),
        generate_erd(request),
        generate_api(request)
    ]
    
    results = await asyncio.gather(*tasks)
    return results
```

**Impact:** 50-70% reduction in total generation time

---

#### Technique 3: Caching Common Patterns

**Problem:** Similar projects generate similar outputs  
**Solution:** Cache prompt templates and common sections

```python
from functools import lru_cache

@lru_cache(maxsize=100)
def get_prompt_template(project_type: str, doc_type: str):
    """Cache prompt templates to avoid rebuilding"""
    return load_template(project_type, doc_type)
```

**Impact:** 5-10% reduction in overhead

---

#### Technique 4: Model Selection

**Problem:** Larger models are slower but higher quality  
**Solution:** Use smaller models for simpler document types

| Document Type | Model | Reasoning |
|---------------|-------|-----------|
| SRS | Llama 3 (8B) | Complex, needs quality |
| Use Cases | Mistral (7B) | Structured, simpler |
| ERD | Mistral (7B) | Pattern-based |
| API | Mistral (7B) | Formulaic |

**Impact:** 20-30% faster generation for simpler docs

---

#### Technique 5: Streaming Responses

**Problem:** User waits for complete generation before seeing anything  
**Solution:** Stream sections as they're generated (V1.1 feature)

```python
from fastapi.responses import StreamingResponse

@app.post("/generate/stream")
async def generate_stream(request: GenerateRequest):
    async def event_generator():
        for section in generate_sections(request):
            yield f"data: {json.dumps(section)}\n\n"
    
    return StreamingResponse(event_generator(), media_type="text/event-stream")
```

**Impact:** Improved perceived performance

---

### 11.3 Load Testing

#### Tools

- **Apache Bench (ab):** Simple HTTP load testing
- **Locust:** Python-based load testing framework
- **k6:** Modern load testing tool with scripting

#### Test Scenarios

```bash
# Test 1: Single user, single generation
ab -n 10 -c 1 -T 'application/json' -p request.json \
   http://localhost:8000/generate

# Test 2: 10 concurrent users
ab -n 100 -c 10 -T 'application/json' -p request.json \
   http://localhost:8000/generate

# Test 3: Sustained load
ab -n 1000 -c 50 -T 'application/json' -p request.json \
   http://localhost:8000/generate
```

#### Metrics to Track

- **Requests per second (RPS)**
- **Average latency**
- **95th percentile latency**
- **Error rate**
- **Memory usage**
- **CPU usage**

---

### 11.4 Scalability Considerations

#### Horizontal Scaling

Deploy multiple ML service instances behind a load balancer:

```yaml
# docker-compose.yml
services:
  ml-service-1:
    image: velocira-ml:latest
    ports:
      - "8001:8000"
  
  ml-service-2:
    image: velocira-ml:latest
    ports:
      - "8002:8000"
  
  ml-service-3:
    image: velocira-ml:latest
    ports:
      - "8003:8000"
  
  load-balancer:
    image: nginx:latest
    ports:
      - "8000:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
```

#### Resource Allocation

**Minimum per instance:**
- CPU: 2 cores
- RAM: 4GB
- Disk: 10GB (for model storage)

**Recommended for production:**
- CPU: 4 cores
- RAM: 8GB
- Disk: 20GB

---

## 12. Deployment Strategy

### 12.1 Deployment Options

#### Option 1: Railway (Recommended for MVP)

**Pros:**
- Free tier available ($5 credit)
- Easy deployment from GitHub
- Automatic HTTPS
- Environment variable management
- Health checks and auto-restart

**Cons:**
- Limited resources on free tier
- No GPU support

**Steps:**
1. Connect GitHub repository to Railway
2. Create new project
3. Select `ml-service` directory
4. Configure environment variables
5. Deploy

---

#### Option 2: Render

**Pros:**
- Free tier with 750 hours/month
- Docker support
- Automatic deploys from GitHub
- Good for Python apps

**Cons:**
- Slower cold starts on free tier
- Limited memory (512MB free tier)

---

#### Option 3: Fly.io

**Pros:**
- Free allowance ($5/month equivalent)
- Global edge deployment
- GPU support (paid)

**Cons:**
- More complex setup
- Command-line focused

---

### 12.2 Docker Configuration

#### Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

# Run application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### docker-compose.yml

```yaml
version: '3.8'

services:
  ml-service:
    build:
      context: ./ml-service
      dockerfile: Dockerfile
    ports:
      - "8000:8000"
    environment:
      - LLM_PROVIDER=ollama
      - OLLAMA_URL=http://ollama:11434
      - LOG_LEVEL=info
    depends_on:
      - ollama
    restart: unless-stopped
  
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    restart: unless-stopped

volumes:
  ollama_data:
```

---

### 12.3 Environment Variables

Create `.env` file for configuration:

```bash
# LLM Configuration
LLM_PROVIDER=ollama               # ollama, openai, huggingface
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3
OPENAI_API_KEY=sk-...             # If using OpenAI
HF_API_TOKEN=hf_...               # If using Hugging Face

# Service Configuration
SERVICE_PORT=8000
LOG_LEVEL=info                    # debug, info, warning, error
MAX_CONCURRENT_REQUESTS=10

# Generation Settings
MAX_GENERATION_TIME=30            # seconds
DEFAULT_TEMPERATURE=0.7
DEFAULT_MAX_TOKENS=4000

# Backend Integration
BACKEND_API_URL=http://localhost:8080
API_SECRET=your_shared_secret     # For authentication
```

---

### 12.4 Deployment Checklist

#### Pre-Deployment

- [ ] All tests passing (unit, integration, quality)
- [ ] Load testing completed, performance targets met
- [ ] Docker image builds successfully
- [ ] Environment variables documented
- [ ] Health check endpoint working
- [ ] Logging configured
- [ ] Error handling tested

#### Deployment

- [ ] Deploy to staging environment
- [ ] Verify staging environment health
- [ ] Run smoke tests on staging
- [ ] Backend integration test on staging
- [ ] Get approval from team
- [ ] Deploy to production
- [ ] Verify production health
- [ ] Monitor initial traffic

#### Post-Deployment

- [ ] Set up monitoring dashboard
- [ ] Configure alerting rules
- [ ] Document rollback procedure
- [ ] Create runbook for common issues
- [ ] Schedule team review meeting

---

## 13. Monitoring and Maintenance

### 13.1 Monitoring Strategy

#### Key Metrics to Track

| Metric | Tool | Alert Threshold |
|--------|------|-----------------|
| Service uptime | Uptime Robot | < 99% |
| Response time | Prometheus | > 35s (p95) |
| Error rate | Sentry | > 5% |
| Memory usage | Docker stats | > 90% |
| CPU usage | Docker stats | > 85% sustained |
| Request rate | Prometheus | Baseline ±50% |

---

#### Logging Strategy

Implement structured logging:

```python
import logging
import json
from datetime import datetime

class JSONLogger:
    def __init__(self):
        self.logger = logging.getLogger("velocira-ml")
        self.logger.setLevel(logging.INFO)
    
    def log_generation(self, request, response, duration_ms):
        log_entry = {
            "timestamp": datetime.utcnow().isoformat(),
            "event": "generation_completed",
            "projectId": request.projectId,
            "projectType": request.projectType,
            "docTypes": request.docTypes,
            "duration_ms": duration_ms,
            "status": response.status,
            "modelUsed": response.metadata.modelUsed,
            "tokensConsumed": response.metadata.tokensConsumed
        }
        self.logger.info(json.dumps(log_entry))
```

**Log Levels:**
- **DEBUG:** Detailed prompt/response data (development only)
- **INFO:** Request/response, generation metrics
- **WARNING:** Slow responses, retries, fallbacks
- **ERROR:** Failed generations, LLM errors, timeouts
- **CRITICAL:** Service crashes, dependency failures

---

#### Error Tracking

Integrate Sentry for error tracking:

```python
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration

sentry_sdk.init(
    dsn="https://your-sentry-dsn",
    integrations=[FastApiIntegration()],
    traces_sample_rate=0.1,  # 10% of requests
    environment="production"
)
```

---

### 13.2 Maintenance Tasks

#### Daily
- Check error logs for new issues
- Verify service health (uptime, response time)
- Monitor resource usage trends

#### Weekly
- Review quality metrics (scoring trends)
- Analyze failed generations
- Update prompt versions if needed
- Review user feedback (via backend team)

#### Monthly
- Performance optimization review
- Dependency updates (pip upgrade)
- Security patches
- Capacity planning (scale up if needed)

#### Quarterly
- Model evaluation (test newer LLM releases)
- Major feature releases
- Architecture review

---

### 13.3 Incident Response

#### Severity Levels

| Level | Description | Response Time | Example |
|-------|-------------|---------------|---------|
| P0 | Service down | Immediate | ML service unreachable |
| P1 | Degraded performance | 15 minutes | >50% error rate |
| P2 | Partial functionality | 1 hour | One doc type failing |
| P3 | Minor issues | 24 hours | Slow response times |

#### Incident Response Runbook

```
INCIDENT: ML Service Unreachable

1. Check service health endpoint
   curl http://ml-service-url/health

2. Check Docker container status
   docker ps -a

3. Check logs for errors
   docker logs ml-service --tail 100

4. Common causes:
   - LLM provider down → Switch to backup provider
   - Out of memory → Restart service, scale up
   - Timeout → Reduce concurrent requests

5. Restart service if needed
   docker-compose restart ml-service

6. Verify recovery
   Run health check again

7. Post-mortem
   - Document root cause
   - Implement prevention measures
   - Update runbook
```

---

### 13.4 Backup and Disaster Recovery

#### Configuration Backup
- Store all configuration in version control (Git)
- Back up environment variables securely
- Maintain deployment documentation

#### Model Backup
- Store model versions in artifact registry
- Document which model version is in production
- Keep previous 2 versions for rollback

#### Rollback Procedure

```bash
# 1. Identify last known good version
git log --oneline

# 2. Checkout that version
git checkout <commit-hash>

# 3. Rebuild and redeploy
docker-compose build ml-service
docker-compose up -d ml-service

# 4. Verify rollback
curl http://localhost:8000/health

# 5. Monitor logs
docker logs -f ml-service
```

---

## 14. Common Challenges and Solutions

### 14.1 Challenge: Inconsistent Output Quality

**Symptoms:**
- Some generations are excellent, others are poor
- Quality varies by project type
- Generic or placeholder text in outputs

**Solutions:**
1. **Improve prompts:** Add more specific instructions, examples
2. **Increase temperature:** For more creative outputs (0.7-0.9)
3. **Decrease temperature:** For more consistent outputs (0.3-0.5)
4. **Add validation:** Reject outputs with placeholders
5. **Implement retry:** Regenerate if quality score < threshold

---

### 14.2 Challenge: Slow Generation Times

**Symptoms:**
- Generation takes > 45 seconds
- Timeouts in backend
- Poor user experience

**Solutions:**
1. **Optimize prompts:** Reduce token count
2. **Parallel generation:** Use async for multiple docs
3. **Smaller models:** Use Mistral (7B) instead of Llama 3 (8B)
4. **Caching:** Cache common prompts/sections
5. **Hardware upgrade:** More CPU/RAM for ML service
6. **Streaming:** Send sections as they're generated (V1.1)

---

### 14.3 Challenge: LLM Provider Downtime

**Symptoms:**
- Connection errors to LLM API
- Timeout errors
- Service unavailable errors

**Solutions:**
1. **Multiple providers:** Implement fallback to backup LLM
2. **Retry logic:** Exponential backoff with max 3 retries
3. **Circuit breaker:** Stop calling if provider consistently fails
4. **Graceful degradation:** Return partial results if some docs succeed
5. **User notification:** Inform user of temporary issue

---

### 14.4 Challenge: Handling Vague Project Ideas

**Symptoms:**
- User input: "make an app"
- Insufficient detail to generate meaningful docs
- Generic outputs

**Solutions:**
1. **Frontend validation:** Require minimum detail in input
2. **Clarifying questions:** Prompt user for more info (V1.1)
3. **Assumption-making:** Make reasonable assumptions and state them
4. **Template selection:** Use generic templates for vague inputs
5. **User guidance:** Provide examples of good project descriptions

---

### 14.5 Challenge: Managing Token Costs

**Symptoms:**
- High API costs (if using OpenAI/Claude)
- Budget exceeded
- Need to reduce usage

**Solutions:**
1. **Shorter prompts:** Remove redundant instructions
2. **Lower max tokens:** Reduce output length limit
3. **Caching:** Cache common generations
4. **Tiered features:** Limit premium features to paid users
5. **Local models:** Switch to Ollama for development/testing

---

## 15. Success Metrics

### 15.1 Technical Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| **Generation Success Rate** | > 95% | (Successful generations / Total attempts) × 100 |
| **Average Generation Time** | < 30s | Mean time from request to response |
| **P95 Generation Time** | < 45s | 95th percentile latency |
| **Quality Score (Average)** | > 8.0/10 | Average rubric score across all docs |
| **Service Uptime** | > 99% | Uptime monitoring tool |
| **Error Rate** | < 5% | (Failed requests / Total requests) × 100 |

---

### 15.2 Quality Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| **Completeness Score** | > 9.0/10 | All required sections present |
| **Specificity Score** | > 7.5/10 | Content is project-specific, not generic |
| **Accuracy Score** | > 8.5/10 | Technical correctness verified |
| **User Satisfaction** | > 4.0/5.0 | User ratings (via backend feedback) |

---

### 15.3 Business Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| **Documents Generated** | 1000+ in month 1 | Count from database |
| **Repeat Usage Rate** | > 40% | Users generating 2+ projects |
| **Export Rate** | > 60% | Users exporting generated docs |
| **Time Saved (Estimated)** | 10+ hours per user | 12 hours (manual) - 0.5 hours (Velocira) |

---

### 15.4 Tracking Dashboard

Create a dashboard to visualize metrics:

```python
# metrics.py

from datetime import datetime, timedelta
from collections import defaultdict

class MetricsTracker:
    def __init__(self):
        self.metrics = defaultdict(list)
    
    def record_generation(self, success: bool, duration_ms: int, quality_score: float):
        timestamp = datetime.utcnow()
        self.metrics["generations"].append({
            "timestamp": timestamp,
            "success": success,
            "duration_ms": duration_ms,
            "quality_score": quality_score
        })
    
    def get_daily_stats(self):
        today = datetime.utcnow().date()
        today_metrics = [
            m for m in self.metrics["generations"]
            if m["timestamp"].date() == today
        ]
        
        return {
            "total_generations": len(today_metrics),
            "success_rate": sum(m["success"] for m in today_metrics) / len(today_metrics) * 100,
            "avg_duration_ms": sum(m["duration_ms"] for m in today_metrics) / len(today_metrics),
            "avg_quality": sum(m["quality_score"] for m in today_metrics) / len(today_metrics)
        }
```

---

## 16. Resources and References

### 16.1 Learning Resources

#### LLM and Prompt Engineering
- [Prompt Engineering Guide](https://www.promptingguide.ai/)
- [OpenAI Prompt Engineering Best Practices](https://platform.openai.com/docs/guides/prompt-engineering)
- [LangChain Documentation](https://python.langchain.com/docs/get_started/introduction)
- [Anthropic Prompt Engineering](https://docs.anthropic.com/claude/docs/prompt-engineering)

#### FastAPI
- [FastAPI Official Documentation](https://fastapi.tiangolo.com/)
- [FastAPI Best Practices](https://github.com/zhanymkanov/fastapi-best-practices)
- [Async Python Tutorial](https://realpython.com/async-io-python/)

#### Testing and Quality
- [Pytest Documentation](https://docs.pytest.org/)
- [Software Testing Best Practices](https://martinfowler.com/testing/)

---

### 16.2 Tools and Libraries

| Category | Tool | Documentation |
|----------|------|---------------|
| **LLM Orchestration** | LangChain | https://python.langchain.com/ |
| **Local LLM** | Ollama | https://ollama.com/docs |
| **API Framework** | FastAPI | https://fastapi.tiangolo.com/ |
| **HTTP Client** | HTTPX | https://www.python-httpx.org/ |
| **Data Validation** | Pydantic | https://docs.pydantic.dev/ |
| **Testing** | Pytest | https://docs.pytest.org/ |
| **Monitoring** | Sentry | https://docs.sentry.io/ |
| **Logging** | Python Logging | https://docs.python.org/3/library/logging.html |

---

### 16.3 Community and Support

- **Velocira Team Slack:** Internal communication
- **GitHub Repository:** https://github.com/YOUR_ORG/velocira
- **LangChain Discord:** https://discord.gg/langchain
- **FastAPI Discord:** https://discord.com/invite/VQjSZaeJmf
- **Ollama Discord:** https://discord.gg/ollama

---

### 16.4 Relevant Documentation from Project

Refer to these project documents for context:

1. **01_SRS_Document.md:** Understand functional requirements
2. **02_Microservices_Documentation.md:** Backend API contracts
3. **03_Plan_and_Flow.md:** Project timeline and dependencies
4. **04_How_To_Do_It.md:** Step-by-step implementation guide

---

## Conclusion

This guide provides everything the ML team needs to successfully implement the AI document generation core of Velocira. The focus is on:

✅ **Clear Requirements:** Exactly what needs to be built  
✅ **Practical Guidance:** How to build it (without code)  
✅ **Quality Focus:** Ensuring outputs meet standards  
✅ **Integration:** Seamless connection with backend  
✅ **Operations:** Deployment, monitoring, maintenance  

**Key Success Factors:**
1. Start with thorough LLM evaluation
2. Invest time in prompt engineering
3. Test quality continuously
4. Optimize performance iteratively
5. Monitor production closely

**Remember:** The ML service is the "brain" of Velocira. Quality and reliability are more important than speed in the MVP phase. Focus on getting it right, then optimize for performance.

Good luck, ML team! 🚀

---

**Document Version:** 1.0  
**Last Updated:** February 28, 2026  
**Maintainer:** ML Team Lead  
**Status:** Active
