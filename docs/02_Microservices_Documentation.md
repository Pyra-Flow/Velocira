# Microservices & Services Documentation

## Velocira — AI-Powered Documentation SaaS Platform

| Field             | Details                                      |
|-------------------|----------------------------------------------|
| **Project Name**  | Velocira                                     |
| **Version**       | 1.0 (MVP)                                    |
| **Date**          | February 2026                                |
| **Architecture**  | Modular Monolith + ML Microservice           |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Service Breakdown](#2-service-breakdown)
3. [Frontend Service (Next.js)](#3-frontend-service-nextjs)
4. [Backend Service (Spring Boot)](#4-backend-service-spring-boot)
5. [ML Generation Service (Python/FastAPI)](#5-ml-generation-service-pythonfastapi)
6. [Database Service (PostgreSQL)](#6-database-service-postgresql)
7. [Service Communication](#7-service-communication)
8. [API Contracts Between Services](#8-api-contracts-between-services)
9. [Authentication Flow](#9-authentication-flow)
10. [Document Generation Flow](#10-document-generation-flow)
11. [Deployment & DevOps](#11-deployment--devops)
12. [Environment Variables](#12-environment-variables)
13. [Error Handling Strategy](#13-error-handling-strategy)
14. [Monitoring & Logging](#14-monitoring--logging)

---

## 1. Architecture Overview

Velocira uses a **Modular Monolith + Microservice** architecture. The core backend is a single Spring Boot application organized into well-separated modules, while the AI/ML functionality runs as a separate Python microservice. This approach is ideal for Velocira because:

- ✅ Fast iteration and deployment — get features to market quickly
- ✅ Keeps the ML logic isolated (different language, different scaling needs, different infra)
- ✅ Keeps operational complexity manageable as we scale
- ✅ Supports independent scaling of ML service during peak usage
- ✅ Can evolve into event-driven or full microservices architecture as complexity grows

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│   ┌───────────────┐         ┌───────────────┐       ┌───────────────┐   │
│   │               │  HTTPS  │               │  HTTP  │               │   │
│   │   Next.js     │────────▶│  Spring Boot  │──────▶│   Python ML   │   │
│   │   Frontend    │◀────────│  Backend API  │◀──────│   Service     │   │
│   │               │  JSON   │               │  JSON  │   (FastAPI)   │   │
│   │  Port: 3000   │         │  Port: 8080   │        │  Port: 8000   │   │
│   └───────────────┘         └───────┬───────┘       └───────────────┘   │
│                                     │                                    │
│                              ┌──────▼──────┐                            │
│                              │ PostgreSQL  │                            │
│                              │ Port: 5432  │                            │
│                              └─────────────┘                            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Communication Summary

| From           | To             | Protocol | Format | Purpose                    |
|----------------|----------------|----------|--------|----------------------------|
| Frontend       | Backend        | HTTPS    | JSON   | All user-facing API calls  |
| Backend        | ML Service     | HTTP     | JSON   | AI generation requests     |
| Backend        | Database       | TCP      | SQL    | Data persistence           |
| Backend        | SMTP Server    | SMTP     | Email  | Verification/reset emails  |

---

## 2. Service Breakdown

| #  | Service Name       | Technology        | Port  | Responsibility                              |
|----|--------------------|-------------------|-------|---------------------------------------------|
| 1  | Frontend           | Next.js 14+       | 3000  | UI, routing, SSR, client-side state         |
| 2  | Backend API        | Spring Boot 3.x   | 8080  | Business logic, auth, data, orchestration   |
| 3  | ML Service         | FastAPI (Python)   | 8000  | AI document generation using LLMs           |
| 4  | Database           | PostgreSQL 15+     | 5432  | Persistent data storage                     |

---

## 3. Frontend Service (Next.js)

### 3.1 Overview

| Property        | Value                                                   |
|-----------------|---------------------------------------------------------|
| Framework       | Next.js 14+ (App Router)                                |
| Language        | TypeScript                                               |
| Styling         | Tailwind CSS + shadcn/ui                                |
| State Mgmt      | Zustand (global) + React Context (auth)                 |
| HTTP Client     | Axios with interceptors                                  |
| Deployment      | Vercel (free tier)                                      |

### 3.2 Page Structure

```
app/
├── (marketing)/
│   ├── page.tsx                    # Landing page
│   ├── features/page.tsx           # Features overview
│   └── pricing/page.tsx            # Pricing/plans
│
├── (auth)/
│   ├── login/page.tsx              # Login form
│   ├── register/page.tsx           # Registration form
│   ├── forgot-password/page.tsx    # Forgot password
│   └── reset-password/page.tsx     # Reset password
│
├── (dashboard)/
│   ├── dashboard/page.tsx          # Project list dashboard
│   ├── projects/
│   │   ├── new/page.tsx            # Project wizard (multi-step)
│   │   └── [id]/
│   │       ├── page.tsx            # Project overview
│   │       ├── documents/
│   │       │   └── [docId]/page.tsx # Document viewer/editor
│   │       └── export/page.tsx      # Export options
│   └── profile/page.tsx            # User profile settings
│
├── (admin)/
│   ├── admin/page.tsx              # Admin dashboard
│   ├── admin/users/page.tsx        # User management
│   └── admin/analytics/page.tsx    # Platform analytics
│
├── layout.tsx                      # Root layout
├── not-found.tsx                   # 404 page
└── error.tsx                       # Error boundary
```

### 3.3 Key Components

```
components/
├── ui/                    # shadcn/ui components (Button, Input, Card, etc.)
├── layout/
│   ├── Navbar.tsx
│   ├── Sidebar.tsx
│   └── Footer.tsx
├── auth/
│   ├── LoginForm.tsx
│   └── RegisterForm.tsx
├── project/
│   ├── ProjectCard.tsx
│   ├── ProjectWizard.tsx
│   ├── IdeaInput.tsx
│   ├── TypeSelector.tsx
│   └── TechStackPicker.tsx
├── document/
│   ├── DocumentViewer.tsx
│   ├── DocumentEditor.tsx
│   ├── SectionRenderer.tsx
│   └── ExportButton.tsx
├── admin/
│   ├── UserTable.tsx
│   └── AnalyticsChart.tsx
└── common/
    ├── LoadingSpinner.tsx
    ├── ErrorAlert.tsx
    └── ConfirmDialog.tsx
```

### 3.4 Frontend → Backend API Integration

```typescript
// lib/api/client.ts — Axios instance with JWT interceptor

import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor: attach JWT token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: handle 401 and refresh token
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Attempt token refresh logic here
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

```typescript
// lib/api/projects.ts — Project API functions

import apiClient from './client';

export const projectsApi = {
  list: () => apiClient.get('/projects'),
  create: (data: CreateProjectDto) => apiClient.post('/projects', data),
  getById: (id: number) => apiClient.get(`/projects/${id}`),
  update: (id: number, data: UpdateProjectDto) => apiClient.put(`/projects/${id}`, data),
  delete: (id: number) => apiClient.delete(`/projects/${id}`),
  generate: (id: number) => apiClient.post(`/projects/${id}/generate`),
  generateDoc: (id: number, docType: string) =>
    apiClient.post(`/projects/${id}/generate/${docType}`),
};
```

---

## 4. Backend Service (Spring Boot)

### 4.1 Overview

| Property        | Value                                                   |
|-----------------|---------------------------------------------------------|
| Framework       | Spring Boot 3.x                                        |
| Language        | Java 17+                                                |
| Build Tool      | Maven                                                   |
| ORM             | Spring Data JPA + Hibernate                             |
| Security        | Spring Security + JWT                                   |
| Validation      | Jakarta Validation (Bean Validation)                    |
| API Docs        | SpringDoc OpenAPI (Swagger UI)                          |
| Deployment      | Railway / Render (Docker)                               |

### 4.2 Module / Package Structure

```
src/main/java/com/velocira/
├── VelociraApplication.java              # Main application entry
│
├── config/                                # Configuration classes
│   ├── SecurityConfig.java                # Spring Security configuration
│   ├── CorsConfig.java                    # CORS settings
│   ├── JwtConfig.java                     # JWT configuration
│   └── OpenApiConfig.java                 # Swagger/OpenAPI config
│
├── auth/                                  # 🔐 Authentication Module
│   ├── controller/
│   │   └── AuthController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   └── JwtService.java
│   ├── dto/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── AuthResponse.java
│   │   └── PasswordResetRequest.java
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java
│   └── util/
│       └── PasswordEncoder.java
│
├── user/                                  # 👤 User Module
│   ├── controller/
│   │   └── UserController.java
│   ├── service/
│   │   └── UserService.java
│   ├── repository/
│   │   └── UserRepository.java
│   ├── entity/
│   │   └── User.java
│   ├── dto/
│   │   ├── UserDto.java
│   │   └── UpdateProfileRequest.java
│   └── mapper/
│       └── UserMapper.java
│
├── project/                               # 📁 Project Module
│   ├── controller/
│   │   └── ProjectController.java
│   ├── service/
│   │   └── ProjectService.java
│   ├── repository/
│   │   └── ProjectRepository.java
│   ├── entity/
│   │   └── Project.java
│   ├── dto/
│   │   ├── CreateProjectRequest.java
│   │   ├── UpdateProjectRequest.java
│   │   └── ProjectResponse.java
│   ├── mapper/
│   │   └── ProjectMapper.java
│   └── enums/
│       ├── ProjectType.java
│       └── ProjectStatus.java
│
├── document/                              # 📄 Document Module
│   ├── controller/
│   │   ├── DocumentController.java
│   │   └── SectionController.java
│   ├── service/
│   │   ├── DocumentService.java
│   │   ├── DocumentGenerationService.java
│   │   └── SectionService.java
│   ├── repository/
│   │   ├── GeneratedDocumentRepository.java
│   │   └── DocumentSectionRepository.java
│   ├── entity/
│   │   ├── GeneratedDocument.java
│   │   └── DocumentSection.java
│   ├── dto/
│   │   ├── DocumentResponse.java
│   │   ├── SectionResponse.java
│   │   └── UpdateSectionRequest.java
│   ├── mapper/
│   │   └── DocumentMapper.java
│   └── enums/
│       └── DocumentType.java
│
├── generation/                            # 🤖 Generation Orchestration Module
│   ├── service/
│   │   ├── GenerationOrchestrator.java    # Coordinates ML calls
│   │   └── MlServiceClient.java          # HTTP client for ML service
│   └── dto/
│       ├── GenerationRequest.java         # Sent to ML service
│       └── GenerationResponse.java        # Received from ML service
│
├── export/                                # 📥 Export Module
│   ├── controller/
│   │   └── ExportController.java
│   ├── service/
│   │   ├── ExportService.java
│   │   ├── PdfExporter.java
│   │   ├── DocxExporter.java
│   │   └── MarkdownExporter.java
│   └── util/
│       └── FileUtils.java
│
├── admin/                                 # 🛡️ Admin Module
│   ├── controller/
│   │   └── AdminController.java
│   ├── service/
│   │   └── AdminService.java
│   └── dto/
│       ├── AdminUserDto.java
│       └── AnalyticsResponse.java
│
├── audit/                                 # 📊 Audit/Logging Module
│   ├── entity/
│   │   └── AuditLog.java
│   ├── repository/
│   │   └── AuditLogRepository.java
│   └── service/
│       └── AuditService.java
│
└── common/                                # 🔧 Shared/Common
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   ├── ResourceNotFoundException.java
    │   ├── UnauthorizedException.java
    │   └── MlServiceException.java
    ├── dto/
    │   ├── ApiResponse.java
    │   └── PagedResponse.java
    └── util/
        └── DateUtils.java
```

### 4.3 Key Module Descriptions

#### 🔐 Auth Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | Handle user registration, login, token management, password reset  |
| Key Classes   | `AuthController`, `AuthService`, `JwtService`, `JwtAuthenticationFilter` |
| Dependencies  | User Module (for UserRepository), Spring Security, JJWT library    |
| Endpoints     | `/api/v1/auth/*`                                                   |

**Flow: User Login**
```
Client → AuthController.login() → AuthService.authenticate()
  → UserRepository.findByEmail() → PasswordEncoder.matches()
  → JwtService.generateToken() → Return AuthResponse (tokens)
```

#### 📁 Project Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | CRUD operations for projects, project metadata management          |
| Key Classes   | `ProjectController`, `ProjectService`, `ProjectRepository`         |
| Dependencies  | User Module, Document Module                                       |
| Endpoints     | `/api/v1/projects/*`                                               |

**Flow: Create Project**
```
Client → ProjectController.create() → ProjectService.createProject()
  → Validate input → ProjectRepository.save() → Return ProjectResponse
```

#### 📄 Document Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | Manage generated documents and their sections                      |
| Key Classes   | `DocumentController`, `DocumentService`, `DocumentGenerationService` |
| Dependencies  | Generation Module (for ML calls), Project Module                    |
| Endpoints     | `/api/v1/projects/{id}/documents/*`, `/api/v1/documents/{docId}/sections/*` |

#### 🤖 Generation Orchestration Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | Orchestrate communication with the Python ML service               |
| Key Classes   | `GenerationOrchestrator`, `MlServiceClient`                        |
| Dependencies  | Spring RestTemplate/WebClient, ML Service (external)               |
| No Endpoints  | This is an internal module called by DocumentGenerationService     |

**Flow: Generate All Documents**
```
DocumentGenerationService.generateAll(projectId)
  → GenerationOrchestrator.orchestrate(project)
    → For each docType:
      → MlServiceClient.generate(GenerationRequest)  ← HTTP POST to ML service
      → Parse GenerationResponse
      → GeneratedDocumentRepository.save()
      → DocumentSectionRepository.saveAll()
  → Update project.status = COMPLETE
```

#### 📥 Export Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | Convert generated documents to PDF, DOCX, and Markdown             |
| Key Classes   | `ExportController`, `ExportService`, `PdfExporter`, `DocxExporter` |
| Dependencies  | Document Module, Apache POI (DOCX), iText/OpenPDF (PDF)           |
| Endpoints     | `/api/v1/projects/{id}/documents/{docId}/export/{format}`          |

#### 🛡️ Admin Module
| Aspect        | Details                                                            |
|---------------|--------------------------------------------------------------------|
| Purpose       | User management, analytics dashboard for administrators            |
| Key Classes   | `AdminController`, `AdminService`                                  |
| Dependencies  | User Module, Audit Module                                          |
| Endpoints     | `/api/v1/admin/*`                                                  |
| Access        | Requires `ADMIN` role                                              |

### 4.4 Key Dependencies (pom.xml)

```xml
<!-- Core -->
<dependency>spring-boot-starter-web</dependency>
<dependency>spring-boot-starter-data-jpa</dependency>
<dependency>spring-boot-starter-security</dependency>
<dependency>spring-boot-starter-validation</dependency>
<dependency>spring-boot-starter-mail</dependency>

<!-- Database -->
<dependency>postgresql</dependency>
<dependency>flyway-core</dependency>              <!-- DB migrations -->

<!-- JWT -->
<dependency>jjwt-api</dependency>
<dependency>jjwt-impl</dependency>
<dependency>jjwt-jackson</dependency>

<!-- API Documentation -->
<dependency>springdoc-openapi-starter-webmvc-ui</dependency>

<!-- Export -->
<dependency>apache-poi</dependency>               <!-- DOCX export -->
<dependency>openpdf</dependency>                   <!-- PDF export -->

<!-- Utilities -->
<dependency>lombok</dependency>
<dependency>mapstruct</dependency>

<!-- Testing -->
<dependency>spring-boot-starter-test</dependency>
<dependency>spring-security-test</dependency>
<dependency>h2database</dependency>                <!-- In-memory test DB -->
```

---

## 5. ML Generation Service (Python/FastAPI)

### 5.1 Overview

| Property        | Value                                                   |
|-----------------|---------------------------------------------------------|
| Framework       | FastAPI 0.100+                                          |
| Language        | Python 3.10+                                            |
| ML Libraries    | LangChain, Hugging Face Transformers, Ollama (local)    |
| Deployment      | Railway / Render (Docker)                               |

### 5.2 Project Structure

```
ml-service/
├── app/
│   ├── __init__.py
│   ├── main.py                     # FastAPI app entry point
│   ├── config.py                   # Configuration and env variables
│   │
│   ├── api/
│   │   ├── __init__.py
│   │   ├── routes.py               # API endpoints
│   │   └── schemas.py              # Pydantic request/response models
│   │
│   ├── services/
│   │   ├── __init__.py
│   │   ├── generation_service.py   # Main generation orchestrator
│   │   ├── srs_generator.py        # SRS document generator
│   │   ├── usecase_generator.py    # Use case generator
│   │   ├── erd_generator.py        # ERD generator
│   │   ├── api_generator.py        # API structure generator
│   │   ├── arch_generator.py       # Architecture proposal generator
│   │   └── ppt_generator.py        # Presentation outline generator
│   │
│   ├── prompts/
│   │   ├── __init__.py
│   │   ├── srs_prompt.py           # Prompt template for SRS
│   │   ├── usecase_prompt.py       # Prompt template for use cases
│   │   ├── erd_prompt.py           # Prompt template for ERD
│   │   ├── api_prompt.py           # Prompt template for API
│   │   ├── arch_prompt.py          # Prompt template for architecture
│   │   └── ppt_prompt.py           # Prompt template for presentation
│   │
│   ├── llm/
│   │   ├── __init__.py
│   │   ├── llm_factory.py          # LLM provider factory
│   │   └── response_parser.py      # Parse and validate LLM output
│   │
│   └── utils/
│       ├── __init__.py
│       └── text_utils.py           # Text processing utilities
│
├── tests/
│   ├── test_generation.py
│   └── test_api.py
│
├── requirements.txt
├── Dockerfile
└── .env.example
```

### 5.3 API Endpoints

| Method | Endpoint              | Description                              |
|--------|-----------------------|------------------------------------------|
| GET    | `/health`             | Health check                             |
| POST   | `/generate`           | Generate all 6 document types            |
| POST   | `/generate/{docType}` | Generate a single document type          |

### 5.4 Request/Response Schema

#### Generate Request (from Spring Boot Backend)
```json
{
  "projectId": 42,
  "projectName": "Smart Library System",
  "ideaText": "A web application that helps university libraries manage books...",
  "projectType": "WEB_APP",
  "techStack": {
    "backend": "Spring Boot",
    "frontend": "React",
    "database": "PostgreSQL"
  },
  "docTypes": ["SRS", "USE_CASE", "ERD", "API", "ARCHITECTURE", "PRESENTATION"]
}
```

#### Generate Response (to Spring Boot Backend)
```json
{
  "projectId": 42,
  "status": "success",
  "documents": [
    {
      "docType": "SRS",
      "sections": [
        {
          "title": "Introduction",
          "order": 1,
          "content": "## 1. Introduction\n\nThis document describes..."
        },
        {
          "title": "Scope",
          "order": 2,
          "content": "## 2. Scope\n\nThe Smart Library System..."
        }
      ]
    },
    {
      "docType": "ERD",
      "sections": [
        {
          "title": "Entities",
          "order": 1,
          "content": "### Entities\n\n| Entity | Description |..."
        }
      ]
    }
  ],
  "generationTime": 18.5,
  "tokensUsed": 4200
}
```

### 5.5 Prompt Engineering Strategy

Each document type has a dedicated prompt template. Example for SRS:

```python
# prompts/srs_prompt.py

SRS_SYSTEM_PROMPT = """
You are an expert software engineer and technical writer helping university 
students create professional Software Requirements Specification documents.
Output must be in structured Markdown format.
"""

SRS_USER_PROMPT = """
Generate a complete SRS document for the following project:

**Project Name:** {project_name}
**Project Idea:** {idea_text}
**Project Type:** {project_type}
**Tech Stack:** {tech_stack}

The SRS must include these sections:
1. Introduction (purpose, scope, definitions)
2. Overall Description (product perspective, features, user classes)
3. Functional Requirements (at least 10, formatted as FR-XX)
4. Non-Functional Requirements (performance, security, usability)
5. System Interfaces (user, software, hardware)
6. Constraints
7. Assumptions & Dependencies

Format each functional requirement as a table with: ID, Name, Description, 
Priority (High/Medium/Low), Input, Output.
"""
```

### 5.6 LLM Provider Strategy

```python
# llm/llm_factory.py — Supports multiple free LLM backends

class LLMFactory:
    @staticmethod
    def create(provider: str):
        if provider == "ollama":
            # Local Ollama (Llama 3, Mistral)
            return OllamaLLM(model="llama3", base_url="http://localhost:11434")
        elif provider == "huggingface":
            # Hugging Face Inference API (free tier)
            return HuggingFaceHub(repo_id="mistralai/Mistral-7B-Instruct-v0.2")
        elif provider == "groq":
            # Groq (free tier — very fast)
            return ChatGroq(model="llama3-8b-8192")
        else:
            raise ValueError(f"Unknown provider: {provider}")
```

---

## 6. Database Service (PostgreSQL)

### 6.1 Overview

| Property        | Value                                                   |
|-----------------|---------------------------------------------------------|
| Database        | PostgreSQL 15+                                          |
| Hosting         | Supabase (free tier) or Railway PostgreSQL              |
| ORM             | Spring Data JPA + Hibernate                             |
| Migrations      | Flyway                                                  |

### 6.2 Connection Configuration

```yaml
# application.yml (Spring Boot)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # Use Flyway for migrations
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### 6.3 Migration Strategy

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_projects_table.sql
├── V3__create_generated_documents_table.sql
├── V4__create_document_sections_table.sql
├── V5__create_audit_logs_table.sql
└── V6__add_indexes.sql
```

### 6.4 Entity Relationship Summary

```
User (1) ──────── (N) Project
Project (1) ──────── (N) GeneratedDocument
GeneratedDocument (1) ──────── (N) DocumentSection
User (1) ──────── (N) AuditLog
```

---

## 7. Service Communication

### 7.1 Frontend ↔ Backend Communication

| Aspect            | Details                                              |
|-------------------|------------------------------------------------------|
| Protocol          | HTTPS (REST)                                         |
| Data Format       | JSON                                                 |
| Authentication    | JWT Bearer token in Authorization header             |
| Error Format      | Standardized `ApiResponse` wrapper                   |
| CORS              | Frontend domain whitelisted                          |

**Request Flow:**
```
Next.js Page → Axios Client → Spring Boot Controller → Service → Repository → DB
                                    ↓
                              Return JSON Response
                                    ↓
Next.js Page ← Axios Client ← Spring Boot Controller
```

### 7.2 Backend ↔ ML Service Communication

| Aspect            | Details                                              |
|-------------------|------------------------------------------------------|
| Protocol          | HTTP (internal network)                              |
| Data Format       | JSON                                                 |
| Authentication    | API key in header (internal service key)             |
| Timeout           | 60 seconds (AI generation can be slow)               |
| Retry             | 2 retries with exponential backoff                   |
| Circuit Breaker   | Fallback message if ML service is down               |

**Request Flow:**
```
DocumentGenerationService → GenerationOrchestrator → MlServiceClient
    → HTTP POST to ML_SERVICE_URL/generate
    → ML Service processes with LLM
    → Returns GenerationResponse (JSON)
    → Parse and store in database
```

### 7.3 Backend → ML Service Client (Spring Boot)

```java
// generation/service/MlServiceClient.java

@Service
public class MlServiceClient {

    private final RestTemplate restTemplate;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    @Value("${ml.service.api-key}")
    private String apiKey;

    public GenerationResponse generateDocuments(GenerationRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);

        HttpEntity<GenerationRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<GenerationResponse> response = restTemplate.exchange(
                mlServiceUrl + "/generate",
                HttpMethod.POST,
                entity,
                GenerationResponse.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            throw new MlServiceException("ML Service unavailable: " + e.getMessage());
        }
    }
}
```

---

## 8. API Contracts Between Services

### 8.1 Frontend → Backend Contracts

#### Contract: Create Project
```
POST /api/v1/projects
Content-Type: application/json
Authorization: Bearer <token>

Request Body:
{
  "name": string (required, 1-200 chars),
  "ideaText": string (required, 50-2000 chars),
  "projectType": enum (required: WEB_APP|MOBILE_APP|AI_SYSTEM|IOT|DESKTOP_APP|API_SERVICE),
  "techStack": object (optional: {backend?: string, frontend?: string, database?: string}),
  "teamSize": number (optional, 1-10),
  "projectOwner": string (optional, max 100 chars - team lead/PM name)
}

Response 201:
{
  "status": "success",
  "data": {
    "id": number,
    "name": string,
    "status": "DRAFT",
    "createdAt": ISO-8601 string
  }
}
```

#### Contract: Generate Documents
```
POST /api/v1/projects/{id}/generate
Authorization: Bearer <token>

Response 202:
{
  "status": "success",
  "message": "Document generation started.",
  "data": {
    "projectId": number,
    "status": "GENERATING",
    "estimatedTime": string
  }
}
```

#### Contract: Get Document
```
GET /api/v1/projects/{id}/documents/{docId}
Authorization: Bearer <token>

Response 200:
{
  "status": "success",
  "data": {
    "id": number,
    "docType": string,
    "version": number,
    "sections": [
      {
        "id": number,
        "title": string,
        "order": number,
        "content": string (Markdown),
        "isEdited": boolean
      }
    ],
    "createdAt": ISO-8601 string,
    "updatedAt": ISO-8601 string
  }
}
```

### 8.2 Backend → ML Service Contract

#### Contract: Generate
```
POST {ML_SERVICE_URL}/generate
Content-Type: application/json
X-API-Key: <internal-api-key>

Request Body:
{
  "projectId": number (required),
  "projectName": string (required),
  "ideaText": string (required),
  "projectType": string (required),
  "techStack": object (optional),
  "docTypes": string[] (required: array of doc types to generate)
}

Response 200:
{
  "projectId": number,
  "status": "success" | "partial" | "failed",
  "documents": [
    {
      "docType": string,
      "sections": [
        {
          "title": string,
          "order": number,
          "content": string (Markdown)
        }
      ]
    }
  ],
  "generationTime": number (seconds),
  "tokensUsed": number
}
```

---

## 9. Authentication Flow

### 9.1 Registration Flow

```
┌────────┐       ┌───────────┐       ┌──────────┐       ┌──────────┐
│  User  │       │ Frontend  │       │ Backend  │       │   SMTP   │
└───┬────┘       └─────┬─────┘       └─────┬────┘       └─────┬────┘
    │  Fill form       │                    │                   │
    │─────────────────▶│                    │                   │
    │                  │  POST /auth/register│                   │
    │                  │───────────────────▶│                   │
    │                  │                    │  Validate input   │
    │                  │                    │  Hash password    │
    │                  │                    │  Save user        │
    │                  │                    │  Send email       │
    │                  │                    │──────────────────▶│
    │                  │    201 Created     │                   │
    │                  │◀───────────────────│                   │
    │  Show success    │                    │                   │
    │◀─────────────────│                    │                   │
    │                  │                    │                   │
    │  Click email link│                    │                   │
    │─────────────────────────────────────▶│                   │
    │                  │                    │  Verify account   │
    │◀─────────────────────────────────────│                   │
```

### 9.2 Login & Token Flow

```
┌────────┐       ┌───────────┐       ┌──────────┐
│  User  │       │ Frontend  │       │ Backend  │
└───┬────┘       └─────┬─────┘       └─────┬────┘
    │  Enter creds     │                    │
    │─────────────────▶│                    │
    │                  │  POST /auth/login  │
    │                  │───────────────────▶│
    │                  │                    │  Validate creds
    │                  │                    │  Generate JWT (access + refresh)
    │                  │   {accessToken,    │
    │                  │    refreshToken}   │
    │                  │◀───────────────────│
    │                  │  Store tokens      │
    │  Redirect to     │  (localStorage +   │
    │  dashboard       │   httpOnly cookie) │
    │◀─────────────────│                    │
    │                  │                    │
    │  [Later] API call│                    │
    │─────────────────▶│                    │
    │                  │  GET /projects     │
    │                  │  Auth: Bearer xxx  │
    │                  │───────────────────▶│
    │                  │                    │  Verify JWT
    │                  │                    │  Extract user
    │                  │   {projects: [...]}│
    │                  │◀───────────────────│
    │  Show projects   │                    │
    │◀─────────────────│                    │
```

### 9.3 JWT Token Structure

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "ahmad@university.edu",
    "userId": 1,
    "role": "STUDENT",
    "iat": 1708761600,
    "exp": 1708762500
  }
}
```

---

## 10. Document Generation Flow

### 10.1 Full Generation Flow (All Documents)

```
┌────────┐  ┌───────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  User  │  │ Frontend  │  │ Backend  │  │ML Service│  │ Database │
└───┬────┘  └─────┬─────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘
    │             │              │              │              │
    │ Click       │              │              │              │
    │ "Generate"  │              │              │              │
    │────────────▶│              │              │              │
    │             │ POST         │              │              │
    │             │ /generate    │              │              │
    │             │─────────────▶│              │              │
    │             │              │  Update      │              │
    │             │              │  status =    │              │
    │             │              │  GENERATING  │              │
    │             │              │─────────────────────────────▶
    │             │  202         │              │              │
    │             │  Accepted    │              │              │
    │             │◀─────────────│              │              │
    │ Show        │              │              │              │
    │ "Generating │              │              │              │
    │  ..." UI    │              │              │              │
    │◀────────────│              │              │              │
    │             │              │  POST        │              │
    │             │              │  /generate   │              │
    │             │              │─────────────▶│              │
    │             │              │              │  Process     │
    │             │              │              │  with LLM    │
    │             │              │              │  (15-30s)    │
    │             │              │              │              │
    │             │              │  Response    │              │
    │             │              │  (documents) │              │
    │             │              │◀─────────────│              │
    │             │              │              │              │
    │             │              │  Save docs   │              │
    │             │              │  + sections  │              │
    │             │              │─────────────────────────────▶
    │             │              │              │              │
    │             │              │  Update      │              │
    │             │              │  status =    │              │
    │             │              │  COMPLETE    │              │
    │             │              │─────────────────────────────▶
    │             │              │              │              │
    │ [Polling]   │              │              │              │
    │ GET status  │  GET project │              │              │
    │────────────▶│─────────────▶│              │              │
    │             │  200 (COMPLETE)             │              │
    │             │◀─────────────│              │              │
    │ Show        │              │              │              │
    │ documents   │              │              │              │
    │◀────────────│              │              │              │
```

### 10.2 Single Document Regeneration Flow

```
User clicks "Regenerate SRS"
  → Frontend: POST /projects/{id}/generate/SRS
  → Backend: Fetches project data
  → Backend: Calls ML Service POST /generate with docTypes=["SRS"]
  → ML Service: Generates SRS only
  → Backend: Replaces old SRS document, increments version
  → Backend: Returns updated document
  → Frontend: Displays new SRS
```

### 10.3 Export Flow

```
User clicks "Export as PDF"
  → Frontend: GET /projects/{id}/documents/{docId}/export/pdf
  → Backend: Fetches document sections from DB
  → Backend: ExportService selects PdfExporter
  → Backend: PdfExporter converts Markdown → PDF
  → Backend: Returns file as byte stream (Content-Disposition: attachment)
  → Frontend: Browser triggers file download
```

---

## 11. Deployment & DevOps

### 11.1 Docker Configuration

#### Backend Dockerfile
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/velocira-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### ML Service Dockerfile
```dockerfile
FROM python:3.10-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### Docker Compose (Local Development)
```yaml
version: '3.8'
services:
  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      - NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
    depends_on:
      - backend

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=database
      - DB_PORT=5432
      - DB_NAME=velocira
      - DB_USERNAME=postgres
      - DB_PASSWORD=postgres
      - ML_SERVICE_URL=http://ml-service:8000
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - database
      - ml-service

  ml-service:
    build: ./ml-service
    ports:
      - "8000:8000"
    environment:
      - LLM_PROVIDER=ollama
      - OLLAMA_BASE_URL=http://host.docker.internal:11434

  database:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=velocira
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

### 11.2 CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: cd backend && mvn -B verify

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '18'
      - run: cd frontend && npm ci && npm run lint && npm run build

  test-ml:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.10'
      - run: cd ml-service && pip install -r requirements.txt && pytest
```

### 11.3 Hosting Strategy (All Free Tier)

| Service         | Platform       | Free Tier Limits                        |
|-----------------|----------------|-----------------------------------------|
| Frontend        | Vercel         | 100 GB bandwidth, auto-deploy from Git  |
| Backend         | Railway/Render | 500 hours/month, auto-sleep on idle     |
| ML Service      | Railway/Render | 500 hours/month, auto-sleep on idle     |
| Database        | Supabase       | 500 MB storage, 2 GB transfer           |
| File Storage    | Cloudinary     | 25 credits/month (for exports if needed)|

---

## 12. Environment Variables

### 12.1 Backend (.env)

```env
# Server
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=velocira
DB_USERNAME=postgres
DB_PASSWORD=your_password

# JWT
JWT_SECRET=your-256-bit-secret-key-here
JWT_ACCESS_EXPIRATION=900000       # 15 minutes in ms
JWT_REFRESH_EXPIRATION=604800000   # 7 days in ms

# ML Service
ML_SERVICE_URL=http://localhost:8000
ML_SERVICE_API_KEY=your-internal-api-key

# Email (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### 12.2 Frontend (.env.local)

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_APP_NAME=Velocira
```

### 12.3 ML Service (.env)

```env
LLM_PROVIDER=ollama           # ollama | huggingface | groq
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3
HF_API_TOKEN=your-huggingface-token
GROQ_API_KEY=your-groq-api-key
API_KEY=your-internal-api-key
```

---

## 13. Error Handling Strategy

### 13.1 Backend Error Handling

```
GlobalExceptionHandler (@ControllerAdvice)
├── ResourceNotFoundException     → 404 Not Found
├── UnauthorizedException         → 401 Unauthorized
├── AccessDeniedException         → 403 Forbidden
├── MethodArgumentNotValid        → 400 Bad Request (validation)
├── MlServiceException            → 503 Service Unavailable
├── DataIntegrityViolation        → 409 Conflict
└── Exception (catch-all)         → 500 Internal Server Error
```

### 13.2 ML Service Error Handling

```python
# Fallback strategy in ML Service
try:
    response = llm.generate(prompt)
except TimeoutError:
    return {"status": "failed", "error": "LLM timed out after 60s"}
except Exception as e:
    return {"status": "failed", "error": str(e)}
```

### 13.3 Frontend Error Handling

```
Axios Interceptor
├── 401 → Attempt token refresh → If fails, redirect to login
├── 403 → Show "Access Denied" toast
├── 404 → Show "Not Found" page
├── 500 → Show "Something went wrong" toast with retry button
└── Network Error → Show "Check your internet connection" toast
```

---

## 14. Monitoring & Logging

### 14.1 Logging Strategy

| Service     | Library         | Format                              |
|-------------|-----------------|-------------------------------------|
| Backend     | SLF4J + Logback | `[timestamp] [level] [class] - msg` |
| ML Service  | Python logging  | `[timestamp] [level] [module] - msg`|
| Frontend    | Console + Sentry| Browser console in dev, Sentry in prod |

### 14.2 Health Checks

| Service     | Endpoint      | Checks                               |
|-------------|---------------|---------------------------------------|
| Backend     | `/actuator/health` | DB connection, ML service reachability |
| ML Service  | `/health`     | LLM availability, memory usage        |

### 14.3 Free Monitoring Tools

| Tool          | Purpose                                           |
|---------------|---------------------------------------------------|
| Spring Actuator| Backend health and metrics                       |
| Sentry (free) | Error tracking for frontend and backend           |
| UptimeRobot   | Uptime monitoring (free tier: 50 monitors)        |

---

_End of Microservices Documentation — Velocira v1.0_

