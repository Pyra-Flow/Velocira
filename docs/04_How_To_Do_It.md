# How To Do It — Step-by-Step Implementation Guide

## Velocira — AI-Powered Documentation SaaS Platform

| Field             | Details                                      |
|-------------------|----------------------------------------------|
| **Project Name**  | Velocira                                     |
| **Version**       | 1.0 (MVP)                                    |
| **Date**          | February 2026                                |
| **Purpose**       | Step-by-step guide for building the product  |

---

## Table of Contents

1. [Prerequisites & Setup](#1-prerequisites--setup)
2. [Step 1: Project Initialization](#2-step-1-project-initialization)
3. [Step 2: Database Setup](#3-step-2-database-setup)
4. [Step 3: Backend — Spring Boot Foundation](#4-step-3-backend--spring-boot-foundation)
5. [Step 4: Backend — Authentication Module](#5-step-4-backend--authentication-module)
6. [Step 5: Backend — Project & Document Modules](#6-step-5-backend--project--document-modules)
7. [Step 6: ML Service — Python FastAPI Setup](#7-step-6-ml-service--python-fastapi-setup)
8. [Step 7: ML Service — Document Generators](#8-step-7-ml-service--document-generators)
9. [Step 8: Backend — ML Integration](#9-step-8-backend--ml-integration)
10. [Step 9: Frontend — Next.js Foundation](#10-step-9-frontend--nextjs-foundation)
11. [Step 10: Frontend — Authentication Pages](#11-step-10-frontend--authentication-pages)
12. [Step 11: Frontend — Dashboard & Project Wizard](#12-step-11-frontend--dashboard--project-wizard)
13. [Step 12: Frontend — Document Viewer & Editor](#13-step-12-frontend--document-viewer--editor)
14. [Step 13: Export Module](#14-step-13-export-module)
15. [Step 14: Admin Panel](#15-step-14-admin-panel)
16. [Step 15: Testing](#16-step-15-testing)
17. [Step 16: Docker & Deployment](#17-step-16-docker--deployment)
18. [Step 17: Launch Readiness & Operations](#18-step-17-launch-readiness--operations)
19. [Common Mistakes to Avoid](#19-common-mistakes-to-avoid)
20. [Helpful Resources](#20-helpful-resources)

---

## 1. Prerequisites & Setup

### 1.1 Software to Install (All Free)

Install these on every team member's machine:

| Software                  | Version  | Download Link                                    | Purpose                      |
|---------------------------|----------|--------------------------------------------------|------------------------------|
| **Java JDK**              | 17+      | https://adoptium.net/                            | Backend language              |
| **Maven**                 | 3.9+     | https://maven.apache.org/download.cgi            | Java build tool               |
| **Node.js**               | 18+      | https://nodejs.org/                              | Frontend runtime              |
| **Python**                | 3.10+    | https://www.python.org/downloads/               | ML service language           |
| **PostgreSQL**            | 15+      | https://www.postgresql.org/download/            | Database                      |
| **Git**                   | Latest   | https://git-scm.com/downloads                   | Version control               |
| **Docker Desktop**        | Latest   | https://www.docker.com/products/docker-desktop  | Containerization              |
| **IntelliJ IDEA Community** | Latest | https://www.jetbrains.com/idea/download/        | Java IDE                      |
| **VS Code**               | Latest   | https://code.visualstudio.com/                  | Frontend + Python IDE         |
| **Postman**               | Latest   | https://www.postman.com/downloads/              | API testing                   |
| **DBeaver Community**     | Latest   | https://dbeaver.io/download/                    | Database GUI                  |
| **Ollama**                | Latest   | https://ollama.com/download                     | Run LLMs locally              |

### 1.2 VS Code Extensions to Install

```
- ESLint
- Prettier
- Tailwind CSS IntelliSense
- Python
- Thunder Client (API testing alternative)
```

### 1.3 IntelliJ IDEA Plugins

```
- Lombok
- Spring Boot Assistant
- .env files support
```

### 1.4 Verify Installations

Open a terminal and run each command to verify:

```bash
java --version          # Should show Java 17+
mvn --version           # Should show Maven 3.9+
node --version          # Should show v18+
npm --version           # Should show 9+
python --version        # Should show 3.10+
pip --version           # Should show pip 22+
git --version           # Should show git 2.x
docker --version        # Should show Docker 24+
docker-compose --version # Should show Compose v2+
```

### 1.5 Set Up Ollama (Local LLM)

```bash
# After installing Ollama, pull a model:
ollama pull llama3         # ~4.7 GB download
# Or a smaller model:
ollama pull mistral        # ~4.1 GB download

# Test it:
ollama run llama3 "Hello, write a short SRS introduction"
```

---

## 2. Step 1: Project Initialization

### 2.1 Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `velocira`
3. Description: "AI-powered SaaS platform for intelligent documentation generation"
4. Visibility: **Private** (make public later if launching as open-source or after beta)
5. Initialize with: **README.md**, **.gitignore (Java)**, **MIT License**
6. Click "Create repository"

### 2.2 Clone and Set Up Folder Structure

```bash
git clone https://github.com/YOUR_USERNAME/velocira.git
cd velocira

# Create the monorepo folder structure
mkdir backend
mkdir frontend
mkdir ml-service
mkdir docs
```

Your project should look like this:
```
velocira/
├── backend/           # Spring Boot (Java)
├── frontend/          # Next.js (TypeScript)
├── ml-service/        # FastAPI (Python)
├── docs/              # Project documentation
├── docker-compose.yml # Local development orchestration
├── .gitignore
├── README.md
└── LICENSE
```

### 2.3 Set Up Git Branching

```bash
# Create develop branch
git checkout -b develop
git push -u origin develop

# Set develop as the default working branch
# On GitHub: Settings → Branches → Default branch → develop
```

### 2.4 Create `.gitignore` (Root Level)

```gitignore
# Java
backend/target/
*.class
*.jar

# Node
frontend/node_modules/
frontend/.next/
frontend/out/

# Python
ml-service/__pycache__/
ml-service/*.pyc
ml-service/venv/
ml-service/.venv/

# Environment files
.env
.env.local
.env.production

# IDE
.idea/
.vscode/
*.iml

# Docker
*.log

# OS
.DS_Store
Thumbs.db
```

### 2.5 Create README.md

```markdown
# 🚀 Velocira — AI-Powered Documentation SaaS

An intelligent SaaS platform that helps entrepreneurs and product teams generate
production-ready documentation for their projects in minutes using AI.

## Features
- 📄 Intelligent SRS Document Generation
- 📊 Automated Use Cases & Flows
- 🗄️ Smart ERD & Schema Design
- 🔌 RESTful API Structure Generation
- 🏗️ System Architecture Recommendations
- ⏱️ Implementation Roadmap & Timeline (v1.1)
- 🌐 Multi-language support (v1.1)
- 👥 Team collaboration (v1.1)

## Tech Stack
- **Backend:** Java 17 + Spring Boot 3
- **Frontend:** Next.js 14 + Tailwind CSS + shadcn/ui
- **ML/AI Service:** Python 3.10 + FastAPI + LangChain
- **Database:** PostgreSQL 15
- **Cloud Deployment:** Vercel, Railway, Supabase
- **CI/CD:** GitHub Actions

## Quick Start
See [SETUP.md](SETUP.md) for full installation and development instructions.

## Roadmap
- **V1.0:** Core document generation (SRS, Use Cases, ERD, API)
- **V1.1:** Payments, team features, versioning, architecture generation
- **V2.0:** Real-time collaboration, integrations, mobile app

## Team
- [Name] — Product Manager
- [Name] — Lead Backend Engineer
- [Name] — Frontend Engineer
- [Name] — ML/AI Engineer
```

---

## 3. Step 2: Database Setup

### 3.1 Create Local Database

Open DBeaver or your terminal:

```sql
-- Connect to PostgreSQL as superuser
psql -U postgres

-- Create the database
CREATE DATABASE velocira;

-- Create a dedicated user (optional but recommended)
CREATE USER velocira_user WITH PASSWORD 'velocira_pass';
GRANT ALL PRIVILEGES ON DATABASE velocira TO velocira_user;

-- Connect to the new database
\c velocira

-- Verify
\dt    -- Should show "No relations found" (empty database)
```

### 3.2 Database Connection Details (for .env files)

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=velocira
DB_USERNAME=velocira_user    (or postgres)
DB_PASSWORD=velocira_pass    (or your postgres password)
```

> **Note:** Don't create tables manually. Flyway migrations in the backend will handle table creation automatically.

---

## 4. Step 3: Backend — Spring Boot Foundation

### 4.1 Generate Spring Boot Project

**Option A: Use Spring Initializr (Easiest)**

1. Go to https://start.spring.io/
2. Configure:
   - **Project:** Maven
   - **Language:** Java
   - **Spring Boot:** 3.2.x (latest stable)
   - **Group:** `com.velocira`
   - **Artifact:** `backend`
   - **Name:** `backend`
   - **Java:** 17
3. Add Dependencies:
   - Spring Web
   - Spring Data JPA
   - Spring Security
   - Spring Boot DevTools
   - PostgreSQL Driver
   - Lombok
   - Validation
   - Spring Boot Starter Mail
4. Click "Generate" → Extract into `velocira/backend/`

**Option B: Create manually with Maven**

```bash
cd backend
mvn archetype:generate \
  -DgroupId=com.velocira \
  -DartifactId=backend \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

### 4.2 Configure `application.yml`

Create `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: velocira-backend

  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:velocira}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  # JPA / Hibernate
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  # Flyway (Database Migrations)
  flyway:
    enabled: true
    locations: classpath:db/migration

  # Email (for later — password reset)
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

# Custom properties
app:
  jwt:
    secret: ${JWT_SECRET:my-super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha}
    access-expiration: ${JWT_ACCESS_EXPIRATION:900000}     # 15 minutes
    refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000} # 7 days
  ml:
    service-url: ${ML_SERVICE_URL:http://localhost:8000}
    api-key: ${ML_SERVICE_API_KEY:dev-api-key}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

### 4.3 Add Additional Dependencies to `pom.xml`

Add these inside `<dependencies>`:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- OpenAPI / Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>

<!-- MapStruct (DTO mapping) -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>

<!-- PDF Export -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>1.3.30</version>
</dependency>

<!-- DOCX Export -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- Test -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### 4.4 Create the Package Structure

```bash
# Inside backend/src/main/java/com/velocira/
mkdir -p config
mkdir -p auth/controller auth/service auth/dto auth/filter
mkdir -p user/controller user/service user/repository user/entity user/dto user/mapper
mkdir -p project/controller project/service project/repository project/entity project/dto project/mapper project/enums
mkdir -p document/controller document/service document/repository document/entity document/dto document/mapper document/enums
mkdir -p generation/service generation/dto
mkdir -p export/controller export/service
mkdir -p admin/controller admin/service admin/dto
mkdir -p audit/entity audit/repository audit/service
mkdir -p common/exception common/dto common/util
```

### 4.5 Create First Flyway Migration

Create `backend/src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
-- V1__initial_schema.sql

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    university VARCHAR(150),
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    idea_text TEXT NOT NULL,
    project_type VARCHAR(50) NOT NULL,
    tech_stack JSONB,
    team_size INT,
    project_owner VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE generated_documents (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    doc_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_sections (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES generated_documents(id) ON DELETE CASCADE,
    section_title VARCHAR(200) NOT NULL,
    section_order INT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_generated_documents_project_id ON generated_documents(project_id);
CREATE INDEX idx_generated_documents_doc_type ON generated_documents(doc_type);
CREATE INDEX idx_document_sections_document_id ON document_sections(document_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
```

### 4.6 Test Backend Starts

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

You should see:
```
Started VelociraApplication in X.XX seconds
Tomcat started on port 8080
Flyway: Successfully applied 1 migration
```

Visit http://localhost:8080/swagger-ui.html to see the API documentation page (empty for now).

---

## 5. Step 4: Backend — Authentication Module

### 5.1 Create the User Entity

**File:** `user/entity/User.java`

```java
package com.velocira.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 150)
    private String university;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "STUDENT";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### 5.2 Create the User Repository

**File:** `user/repository/UserRepository.java`

```java
package com.velocira.user.repository;

import com.velocira.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### 5.3 Create JWT Service

**File:** `auth/service/JwtService.java`

```java
package com.velocira.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(Map.of(
            "userId", userId,
            "role", role
        ), email, accessExpiration);
    }

    public String generateRefreshToken(String email) {
        return buildToken(Map.of(), email, refreshExpiration);
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
```

### 5.4 Create Auth DTOs

**File:** `auth/dto/RegisterRequest.java`

```java
package com.velocira.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Size(max = 150)
    private String university;
}
```

**File:** `auth/dto/LoginRequest.java`

```java
package com.velocira.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;
}
```

**File:** `auth/dto/AuthResponse.java`

```java
package com.velocira.auth.dto;

import lombok.*;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String fullName;
    private String email;
    private String role;
}
```

### 5.5 Create Auth Service

**File:** `auth/service/AuthService.java`

```java
package com.velocira.auth.service;

import com.velocira.auth.dto.*;
import com.velocira.common.exception.UnauthorizedException;
import com.velocira.user.entity.User;
import com.velocira.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create user
        User user = User.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .university(request.getUniversity())
            .role("STUDENT")
            .build();

        user = userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .userId(user.getId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .userId(user.getId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }
}
```

### 5.6 Create Auth Controller

**File:** `auth/controller/AuthController.java`

```java
package com.velocira.auth.controller;

import com.velocira.auth.dto.*;
import com.velocira.auth.service.AuthService;
import com.velocira.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
```

### 5.7 Create Common ApiResponse Wrapper

**File:** `common/dto/ApiResponse.java`

```java
package com.velocira.common.dto;

import lombok.*;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .status("success")
            .message(message)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .status("error")
            .message(message)
            .build();
    }
}
```

### 5.8 Configure Spring Security

**File:** `config/SecurityConfig.java`

```java
package com.velocira.config;

import com.velocira.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configure(http))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

### 5.9 Test the Auth Endpoints

Start the backend and use Postman:

**Register:**
```
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "fullName": "Test User",
  "email": "test@example.com",
  "password": "password123",
  "university": "Test University"
}
```

**Login:**
```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123"
}
```

**Expected Response:**
```json
{
  "status": "success",
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "fullName": "Test User",
    "email": "test@example.com",
    "role": "STUDENT"
  }
}
```

✅ **Checkpoint:** Auth module is working if you get tokens back.

---

## 6. Step 5: Backend — Project & Document Modules

### 6.1 Create Project Entity

**File:** `project/entity/Project.java`

```java
@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "idea_text", nullable = false, columnDefinition = "TEXT")
    private String ideaText;

    @Column(name = "project_type", nullable = false, length = 50)
    private String projectType;
    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private String techStack;  // Store as JSON string

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(length = 100)
    private String projectOwner;  // Team lead or PM name

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeneratedDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### 6.2 Create Project CRUD Controller

Follow the same pattern as Auth:
1. **`CreateProjectRequest`** DTO with validation
2. **`ProjectResponse`** DTO for output
3. **`ProjectRepository`** extending `JpaRepository<Project, Long>`
4. **`ProjectService`** with `create`, `findAll`, `findById`, `update`, `delete`
5. **`ProjectController`** with REST endpoints under `/api/v1/projects`

> **Important:** Always check that the project belongs to the authenticated user before allowing access.

### 6.3 Create Document Entities

Follow the same pattern:
1. **`GeneratedDocument`** entity → `generated_documents` table
2. **`DocumentSection`** entity → `document_sections` table
3. Repositories, services, controllers

### 6.4 Test Project CRUD

Use Postman with the JWT token from login:

```
POST http://localhost:8080/api/v1/projects
Authorization: Bearer <your-access-token>
Content-Type: application/json

{
  "name": "Smart Library System",
  "ideaText": "A web application that helps libraries manage books and recommend them to students.",
  "projectType": "WEB_APP"
}
```

✅ **Checkpoint:** You can create, list, update, and delete projects.

---

## 7. Step 6: ML Service — Python FastAPI Setup

### 7.1 Initialize Python Project

```bash
cd ml-service

# Create virtual environment
python -m venv venv

# Activate it
# Windows:
venv\Scripts\activate
# Mac/Linux:
source venv/bin/activate

# Install dependencies
pip install fastapi uvicorn langchain langchain-community pydantic python-dotenv requests

# Save dependencies
pip freeze > requirements.txt
```

### 7.2 Create Project Structure

```bash
mkdir -p app/api app/services app/prompts app/llm app/utils
touch app/__init__.py app/api/__init__.py app/services/__init__.py
touch app/prompts/__init__.py app/llm/__init__.py app/utils/__init__.py
```

### 7.3 Create FastAPI Main App

**File:** `ml-service/app/main.py`

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.routes import router

app = FastAPI(
    title="Velocira ML Service",
    description="AI Document Generation Service",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "velocira-ml"}
```

### 7.4 Create Request/Response Schemas

**File:** `ml-service/app/api/schemas.py`

```python
from pydantic import BaseModel
from typing import Optional
from enum import Enum

class DocType(str, Enum):
    SRS = "SRS"
    USE_CASE = "USE_CASE"
    ERD = "ERD"
    API = "API"
    ARCHITECTURE = "ARCHITECTURE"
    PRESENTATION = "PRESENTATION"

class TechStack(BaseModel):
    backend: Optional[str] = None
    frontend: Optional[str] = None
    database: Optional[str] = None

class GenerationRequest(BaseModel):
    projectId: int
    projectName: str
    ideaText: str
    projectType: str
    techStack: Optional[TechStack] = None
    docTypes: list[DocType]

class Section(BaseModel):
    title: str
    order: int
    content: str

class DocumentResult(BaseModel):
    docType: str
    sections: list[Section]

class GenerationResponse(BaseModel):
    projectId: int
    status: str
    documents: list[DocumentResult]
    generationTime: float
```

### 7.5 Create API Routes

**File:** `ml-service/app/api/routes.py`

```python
from fastapi import APIRouter, HTTPException
from app.api.schemas import GenerationRequest, GenerationResponse
from app.services.generation_service import GenerationService
import time

router = APIRouter()
generation_service = GenerationService()

@router.post("/generate", response_model=GenerationResponse)
async def generate_documents(request: GenerationRequest):
    start_time = time.time()

    try:
        documents = await generation_service.generate_all(request)
        elapsed = round(time.time() - start_time, 2)

        return GenerationResponse(
            projectId=request.projectId,
            status="success",
            documents=documents,
            generationTime=elapsed
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
```

### 7.6 Run and Test ML Service

```bash
cd ml-service
uvicorn app.main:app --reload --port 8000
```

Visit http://localhost:8000/docs for the interactive API documentation.

✅ **Checkpoint:** ML service starts and shows the health endpoint.

---

## 8. Step 7: ML Service — Document Generators

### 8.1 Set Up LLM Integration

**File:** `ml-service/app/llm/llm_factory.py`

```python
import os
from langchain_community.llms import Ollama

def get_llm():
    """Get the configured LLM instance."""
    provider = os.getenv("LLM_PROVIDER", "ollama")

    if provider == "ollama":
        return Ollama(
            model=os.getenv("OLLAMA_MODEL", "llama3"),
            base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
            temperature=0.7
        )
    else:
        raise ValueError(f"Unknown LLM provider: {provider}")
```

### 8.2 Create SRS Generator (Example)

**File:** `ml-service/app/services/srs_generator.py`

```python
from app.llm.llm_factory import get_llm
from app.api.schemas import Section

SRS_PROMPT = """
You are a senior product architect with 10+ years of experience writing
professional SRS documents for startups and enterprises.

Project Name: {project_name}
Project Description: {idea_text}
Project Type: {project_type}
Preferred Tech Stack: {tech_stack}

Generate a complete SRS document with these sections in Markdown format:
1. Introduction (purpose, scope, definitions)
2. Overall Description
3. Functional Requirements (minimum 8 requirements, use table format)
4. Non-Functional Requirements
5. Constraints
6. Assumptions & Dependencies

Be specific to this project. Use proper IDs (FR-01, NFR-01).
Format output as clean Markdown.
"""

async def generate_srs(project_name, idea_text, project_type, tech_stack) -> list[Section]:
    llm = get_llm()

    prompt = SRS_PROMPT.format(
        project_name=project_name,
        idea_text=idea_text,
        project_type=project_type,
        tech_stack=tech_stack or "Not specified"
    )

    response = llm.invoke(prompt)

    # Parse the response into sections
    # (Simple approach: split by ## headers)
    sections = parse_markdown_sections(response)
    return sections

def parse_markdown_sections(markdown_text: str) -> list[Section]:
    """Split markdown by ## headers into sections."""
    sections = []
    current_title = "Introduction"
    current_content = []
    order = 1

    for line in markdown_text.split('\n'):
        if line.startswith('## '):
            if current_content:
                sections.append(Section(
                    title=current_title,
                    order=order,
                    content='\n'.join(current_content).strip()
                ))
                order += 1
            current_title = line.replace('## ', '').strip()
            current_content = []
        else:
            current_content.append(line)

    # Don't forget the last section
    if current_content:
        sections.append(Section(
            title=current_title,
            order=order,
            content='\n'.join(current_content).strip()
        ))

    return sections
```

### 8.3 Create Remaining Generators

Follow the same pattern for each document type. Create one file per generator:

| File                    | What It Generates                                     |
|-------------------------|-------------------------------------------------------|
| `srs_generator.py`     | SRS Document (intro, requirements, constraints)        |
| `usecase_generator.py` | Actors, use case list, use case descriptions           |
| `erd_generator.py`     | Entities, attributes, relationships, SQL schema        |
| `api_generator.py`     | REST endpoints, request/response examples              |
| `arch_generator.py`    | Architecture pattern, stack, deployment structure      |
| `ppt_generator.py`     | Presentation outline (slide by slide)                  |

> **Tip for prompt engineering:**
> - Be very specific in your prompts about what you want
> - Ask for structured output (tables, lists, numbered items)
> - Include the project context in every prompt
> - Test and refine prompts iteratively — this is the most important part of the ML work!

### 8.4 Create the Generation Orchestrator

**File:** `ml-service/app/services/generation_service.py`

```python
from app.api.schemas import GenerationRequest, DocumentResult, DocType
from app.services.srs_generator import generate_srs
from app.services.usecase_generator import generate_usecase
from app.services.erd_generator import generate_erd
from app.services.api_generator import generate_api
from app.services.arch_generator import generate_architecture
from app.services.ppt_generator import generate_presentation

class GenerationService:

    GENERATORS = {
        DocType.SRS: generate_srs,
        DocType.USE_CASE: generate_usecase,
        DocType.ERD: generate_erd,
        DocType.API: generate_api,
        DocType.ARCHITECTURE: generate_architecture,
        DocType.PRESENTATION: generate_presentation,
    }

    async def generate_all(self, request: GenerationRequest) -> list[DocumentResult]:
        results = []
        tech_stack_str = ""
        if request.techStack:
            parts = []
            if request.techStack.backend: parts.append(f"Backend: {request.techStack.backend}")
            if request.techStack.frontend: parts.append(f"Frontend: {request.techStack.frontend}")
            if request.techStack.database: parts.append(f"Database: {request.techStack.database}")
            tech_stack_str = ", ".join(parts)

        for doc_type in request.docTypes:
            generator = self.GENERATORS.get(doc_type)
            if generator:
                sections = await generator(
                    project_name=request.projectName,
                    idea_text=request.ideaText,
                    project_type=request.projectType,
                    tech_stack=tech_stack_str
                )
                results.append(DocumentResult(
                    docType=doc_type.value,
                    sections=sections
                ))

        return results
```

✅ **Checkpoint:** Calling `POST /generate` returns AI-generated documents.

---

## 9. Step 8: Backend — ML Integration

### 9.1 Create ML Client in Spring Boot

**File:** `generation/service/MlServiceClient.java`

This class makes HTTP calls from Spring Boot to the Python ML service. Use `RestTemplate` or `WebClient`:

```java
@Service
public class MlServiceClient {

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public MlServiceClient(
            RestTemplateBuilder builder,
            @Value("${app.ml.service-url}") String mlServiceUrl) {
        this.restTemplate = builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))  // AI can be slow
            .build();
        this.mlServiceUrl = mlServiceUrl;
    }

    public GenerationResponse generate(GenerationRequest request) {
        try {
            ResponseEntity<GenerationResponse> response = restTemplate.postForEntity(
                mlServiceUrl + "/generate",
                request,
                GenerationResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new MlServiceException("ML Service error: " + e.getMessage());
        }
    }
}
```

### 9.2 Create the Generation Orchestrator

**File:** `generation/service/GenerationOrchestrator.java`

```java
@Service
@RequiredArgsConstructor
public class GenerationOrchestrator {

    private final MlServiceClient mlServiceClient;
    private final GeneratedDocumentRepository documentRepository;
    private final DocumentSectionRepository sectionRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public void generateDocuments(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        // Update status
        project.setStatus("GENERATING");
        projectRepository.save(project);

        try {
            // Build request for ML service
            GenerationRequest request = buildRequest(project);

            // Call ML service
            GenerationResponse response = mlServiceClient.generate(request);

            // Save generated documents and sections
            for (var doc : response.getDocuments()) {
                GeneratedDocument document = new GeneratedDocument();
                document.setProject(project);
                document.setDocType(doc.getDocType());
                document.setContent("");  // Content is in sections
                document = documentRepository.save(document);

                for (var section : doc.getSections()) {
                    DocumentSection docSection = new DocumentSection();
                    docSection.setDocument(document);
                    docSection.setSectionTitle(section.getTitle());
                    docSection.setSectionOrder(section.getOrder());
                    docSection.setContent(section.getContent());
                    sectionRepository.save(docSection);
                }
            }

            // Update status to COMPLETE
            project.setStatus("COMPLETE");
            projectRepository.save(project);

        } catch (Exception e) {
            project.setStatus("FAILED");
            projectRepository.save(project);
            throw e;
        }
    }
}
```

### 9.3 Add Generation Endpoint to Project Controller

```java
@PostMapping("/{id}/generate")
public ResponseEntity<ApiResponse<String>> generateDocuments(@PathVariable Long id) {
    // Trigger generation (can be async with @Async)
    generationOrchestrator.generateDocuments(id);
    return ResponseEntity.accepted()
        .body(ApiResponse.success("Generation started", null));
}
```

✅ **Checkpoint:** Creating a project and calling generate produces documents in the database.

---

## 10. Step 9: Frontend — Next.js Foundation

### 10.1 Create Next.js Project

```bash
cd frontend
npx create-next-app@latest . --typescript --tailwind --eslint --app --src-dir --import-alias "@/*"
```

When prompted:
- Would you like to use TypeScript? **Yes**
- Would you like to use ESLint? **Yes**
- Would you like to use Tailwind CSS? **Yes**
- Would you like to use `src/` directory? **Yes**
- Would you like to use App Router? **Yes**
- Would you like to customize the default import alias? **Yes** → `@/*`

### 10.2 Install Additional Dependencies

```bash
npm install axios zustand react-hot-toast react-markdown
npm install @radix-ui/react-dialog @radix-ui/react-dropdown-menu
npx shadcn-ui@latest init
```

When prompted for shadcn/ui:
- Style: **Default**
- Base color: **Slate**
- CSS variables: **Yes**

Then add components:
```bash
npx shadcn-ui@latest add button card input label dialog dropdown-menu
npx shadcn-ui@latest add form select textarea tabs badge separator
```

### 10.3 Set Up Axios API Client

**File:** `frontend/src/lib/api/client.ts`

```typescript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

// Add JWT token to every request
apiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Handle 401 errors (token expired)
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('accessToken');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

### 10.4 Create Environment File

**File:** `frontend/.env.local`

```
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_APP_NAME=Velocira
```

### 10.5 Create Basic Layout

**File:** `frontend/src/app/layout.tsx`

```tsx
import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'Velocira — AI-Powered Documentation SaaS',
  description: 'Generate production-ready SaaS documentation with AI in minutes',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={inter.className}>{children}</body>
    </html>
  )
}
```

### 10.6 Run and Test Frontend

```bash
cd frontend
npm run dev
```

Visit http://localhost:3000 — you should see the Next.js default page.

✅ **Checkpoint:** Frontend runs on port 3000.

---

## 11. Step 10: Frontend — Authentication Pages

### 11.1 Create Auth API Functions

**File:** `frontend/src/lib/api/auth.ts`

```typescript
import apiClient from './client';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  university?: string;
}

export const authApi = {
  login: (data: LoginRequest) => apiClient.post('/auth/login', data),
  register: (data: RegisterRequest) => apiClient.post('/auth/register', data),
};
```

### 11.2 Create Login Page

**File:** `frontend/src/app/(auth)/login/page.tsx`

Build a form with:
- Email input
- Password input
- "Login" button
- Link to register page
- On submit: call `authApi.login()`, store tokens, redirect to `/dashboard`

### 11.3 Create Register Page

**File:** `frontend/src/app/(auth)/register/page.tsx`

Build a form with:
- Full name input
- Email input
- Password input
- University input (optional)
- "Register" button
- Link to login page

### 11.4 Create Auth Store (Zustand)

**File:** `frontend/src/store/auth-store.ts`

```typescript
import { create } from 'zustand';

interface AuthState {
  user: { id: number; email: string; fullName: string; role: string } | null;
  isAuthenticated: boolean;
  setUser: (user: any) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  setUser: (user) => set({ user, isAuthenticated: true }),
  logout: () => {
    localStorage.removeItem('accessToken');
    set({ user: null, isAuthenticated: false });
  },
}));
```

✅ **Checkpoint:** You can register and login from the UI.

---

## 12. Step 11: Frontend — Dashboard & Project Wizard

### 12.1 Dashboard Page

**File:** `frontend/src/app/(dashboard)/dashboard/page.tsx`

- Fetch projects from `GET /projects` on page load
- Display project cards in a grid
- Each card shows: name, type, status, date, actions (view/delete)
- "New Project" button at the top

### 12.2 Project Wizard (Multi-Step Form)

**File:** `frontend/src/app/(dashboard)/projects/new/page.tsx`

Create a multi-step wizard:

```
Step 1: "What's your project idea?"
  → Text area (50–2000 characters)
  → Character counter

Step 2: "What type of project?"
  → Card selection: Web App, Mobile App, AI System, IoT, Desktop App
  → Visual icons for each type

Step 3: "Tech stack (optional)"
  → Backend dropdown (Spring Boot, Node.js, Django, etc.)
  → Frontend dropdown (React, Next.js, Vue, etc.)
  → Database dropdown (PostgreSQL, MySQL, MongoDB, etc.)
  → Or "Let AI decide" button

Step 4: "Project details"
  → Project name
  → Team size
  → Project owner/PM name
  → "Create & Generate" button
```

### 12.3 Generation Progress UI

After clicking "Create & Generate":
1. Show a loading screen with animation
2. Poll `GET /projects/{id}` every 3 seconds
3. When status changes to `COMPLETE`, redirect to project view
4. If status changes to `FAILED`, show error with retry option

```typescript
// Simple polling example
const pollStatus = async (projectId: number) => {
  const interval = setInterval(async () => {
    const res = await projectsApi.getById(projectId);
    if (res.data.data.status === 'COMPLETE') {
      clearInterval(interval);
      router.push(`/projects/${projectId}`);
    } else if (res.data.data.status === 'FAILED') {
      clearInterval(interval);
      setError('Generation failed. Please try again.');
    }
  }, 3000);
};
```

✅ **Checkpoint:** You can create a project, see it generating, and view it when complete.

---

## 13. Step 12: Frontend — Document Viewer & Editor

### 13.1 Document Viewer Page

**File:** `frontend/src/app/(dashboard)/projects/[id]/page.tsx`

- Fetch project details + all documents
- Display **6 tabs** (SRS, Use Case, ERD, API, Architecture, Presentation)
- Each tab shows the document content rendered from Markdown
- Use `react-markdown` to render Markdown content

```tsx
import ReactMarkdown from 'react-markdown';

<Tabs defaultValue="SRS">
  <TabsList>
    <TabsTrigger value="SRS">📄 SRS</TabsTrigger>
    <TabsTrigger value="USE_CASE">📊 Use Cases</TabsTrigger>
    <TabsTrigger value="ERD">🗄️ ERD</TabsTrigger>
    <TabsTrigger value="API">🔌 API</TabsTrigger>
    <TabsTrigger value="ARCHITECTURE">🏗️ Architecture</TabsTrigger>
    <TabsTrigger value="PRESENTATION">📽️ Presentation</TabsTrigger>
  </TabsList>

  {documents.map(doc => (
    <TabsContent key={doc.docType} value={doc.docType}>
      {doc.sections.map(section => (
        <div key={section.id}>
          <ReactMarkdown>{section.content}</ReactMarkdown>
        </div>
      ))}
    </TabsContent>
  ))}
</Tabs>
```

### 13.2 Inline Editing

- Add an "Edit" button next to each section
- When clicked, switch to a textarea with the Markdown content
- "Save" button calls `PUT /documents/{docId}/sections/{sectionId}`
- "Cancel" button reverts changes

### 13.3 Regenerate Section

- Add a "Regenerate" button for each section
- Shows a modal: "Add additional instructions (optional)"
- Calls `POST /documents/{docId}/sections/{sectionId}/regenerate`
- Replaces the section content with the new generation

✅ **Checkpoint:** Documents are viewable, editable, and regeneratable from the UI.

---

## 14. Step 13: Export Module

### 14.1 Backend: PDF Export

**File:** `export/service/PdfExporter.java`

```java
// Use OpenPDF to convert document sections to PDF
// Steps:
// 1. Create a Document object
// 2. Add title, headers, paragraphs from sections
// 3. Write to ByteArrayOutputStream
// 4. Return as byte array

public byte[] exportToPdf(List<DocumentSection> sections, String title) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4);
    PdfWriter.getInstance(document, baos);
    document.open();

    // Add title
    Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD);
    document.add(new Paragraph(title, titleFont));
    document.add(new Paragraph("\n"));

    // Add sections
    for (DocumentSection section : sections) {
        Font sectionFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        document.add(new Paragraph(section.getSectionTitle(), sectionFont));
        document.add(new Paragraph(section.getContent()));
        document.add(new Paragraph("\n"));
    }

    document.close();
    return baos.toByteArray();
}
```

### 14.2 Backend: Export Controller

```java
@GetMapping("/{projectId}/documents/{docId}/export/{format}")
public ResponseEntity<byte[]> exportDocument(
        @PathVariable Long projectId,
        @PathVariable Long docId,
        @PathVariable String format) {

    byte[] fileContent = exportService.export(docId, format);
    String filename = "velocira-document." + format;

    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=" + filename)
        .contentType(getMediaType(format))
        .body(fileContent);
}
```

### 14.3 Frontend: Download Button

```tsx
const handleExport = async (format: 'pdf' | 'docx' | 'md') => {
  const response = await apiClient.get(
    `/projects/${projectId}/documents/${docId}/export/${format}`,
    { responseType: 'blob' }
  );

  // Trigger browser download
  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.download = `velocira-document.${format}`;
  link.click();
  window.URL.revokeObjectURL(url);
};
```

✅ **Checkpoint:** Users can download documents as PDF, DOCX, or Markdown.

---

## 15. Step 14: Admin Panel

### 15.1 Admin Pages

Create these pages (only accessible to users with `ADMIN` role):

1. **Admin Dashboard** (`/admin`) — Statistics cards: total users, total projects, etc.
2. **User Management** (`/admin/users`) — Table of all users with suspend/activate buttons
3. **Analytics** (`/admin/analytics`) — Charts showing usage over time (use a free chart library like `recharts`)

### 15.2 Admin Route Protection

```tsx
// middleware.ts or a wrapper component
const AdminGuard = ({ children }) => {
  const { user } = useAuthStore();

  if (user?.role !== 'ADMIN') {
    return <div>Access Denied</div>;
  }

  return <>{children}</>;
};
```

### 15.3 Seed an Admin User

Add a data migration or a backend command to create an admin user:

```sql
-- V7__seed_admin_user.sql
INSERT INTO users (full_name, email, password_hash, role, is_active, email_verified)
VALUES ('Admin', 'admin@velocira.app',
        '$2a$10$...', -- bcrypt hash of 'admin123'
        'ADMIN', true, true);
```

✅ **Checkpoint:** Admin can view users and platform statistics.

---

## 16. Step 15: Testing

### 16.1 Backend Unit Tests

**Location:** `backend/src/test/java/com/velocira/`

```java
// Example: AuthServiceTest.java
@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test User");
        request.setEmail("test@test.com");
        request.setPassword("password123");

        AuthResponse response = authService.register(request);

        assertNotNull(response.getAccessToken());
        assertEquals("test@test.com", response.getEmail());
    }

    @Test
    void shouldNotRegisterDuplicateEmail() {
        // Register first time
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test");
        request.setEmail("duplicate@test.com");
        request.setPassword("password123");
        authService.register(request);

        // Try to register again
        assertThrows(RuntimeException.class, () -> authService.register(request));
    }
}
```

Run tests:
```bash
cd backend
mvn test
```

### 16.2 ML Service Tests

**File:** `ml-service/tests/test_generation.py`

```python
import pytest
from app.services.srs_generator import parse_markdown_sections

def test_parse_markdown_sections():
    markdown = """## Introduction
This is the intro.

## Requirements
- FR-01: Login
- FR-02: Register
"""
    sections = parse_markdown_sections(markdown)
    assert len(sections) == 2
    assert sections[0].title == "Introduction"
    assert sections[1].title == "Requirements"
```

Run tests:
```bash
cd ml-service
pytest
```

### 16.3 Frontend Tests (Optional but Recommended)

```bash
cd frontend
npm test
```

### 16.4 Manual End-to-End Testing Checklist

Test the complete flow:

- [ ] Register a new user
- [ ] Login with the new user
- [ ] Create a new project with idea, type, and tech stack
- [ ] Wait for generation to complete
- [ ] View all 6 generated document types
- [ ] Edit a section of the SRS
- [ ] Regenerate a section
- [ ] Export a document as PDF
- [ ] Export a document as Markdown
- [ ] Delete a project
- [ ] Login as admin
- [ ] View admin dashboard with analytics
- [ ] Suspend and reactivate a user

---

## 17. Step 16: Docker & Deployment

### 17.1 Create Docker Files

**Backend Dockerfile** (`backend/Dockerfile`):
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**ML Service Dockerfile** (`ml-service/Dockerfile`):
```dockerfile
FROM python:3.10-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Frontend Dockerfile** (`frontend/Dockerfile`):
```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:18-alpine
WORKDIR /app
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

### 17.2 Docker Compose for Local Development

**File:** `docker-compose.yml` (at project root)

```yaml
version: '3.8'
services:
  database:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: velocira
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - pgdata:/var/lib/postgresql/data

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      DB_HOST: database
      DB_PORT: 5432
      DB_NAME: velocira
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      ML_SERVICE_URL: http://ml-service:8000
      JWT_SECRET: your-secret-key-at-least-32-characters-long
    depends_on:
      - database
      - ml-service

  ml-service:
    build: ./ml-service
    ports:
      - "8000:8000"
    environment:
      LLM_PROVIDER: ollama
      OLLAMA_BASE_URL: http://host.docker.internal:11434

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080/api/v1
    depends_on:
      - backend

volumes:
  pgdata:
```

### 17.3 Run Everything Locally

```bash
# Make sure Ollama is running on your host machine
ollama serve

# Start all services
docker-compose up --build

# Visit:
# Frontend:  http://localhost:3000
# Backend:   http://localhost:8080/swagger-ui.html
# ML:        http://localhost:8000/docs
```

### 17.4 Deploy to Cloud (All Free)

#### Frontend → Vercel
1. Push code to GitHub
2. Go to https://vercel.com → Import project → Select your repo
3. Set root directory to `frontend`
4. Add environment variable: `NEXT_PUBLIC_API_URL=https://your-backend.railway.app/api/v1`
5. Deploy!

#### Backend → Railway
1. Go to https://railway.app → New Project → Deploy from GitHub
2. Select your repo, set root directory to `backend`
3. Add environment variables (DB_HOST, JWT_SECRET, etc.)
4. Railway auto-detects Dockerfile and deploys

#### ML Service → Railway
1. Same process as backend, root directory `ml-service`
2. Set `LLM_PROVIDER=groq` (since you can't run Ollama on Railway)
3. Set `GROQ_API_KEY=your-free-groq-key`

#### Database → Supabase
1. Go to https://supabase.com → New Project
2. Get the connection string
3. Use it as `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` in Railway

✅ **Checkpoint:** Application is live and accessible from a public URL.

---

## 18. Step 17: Final Polish & Presentation

### 18.1 UI Polish Checklist

- [ ] Loading spinners on all async actions
- [ ] Toast notifications for success/error messages
- [ ] Empty states ("No projects yet. Create your first one!")
- [ ] Responsive design tested on mobile
- [ ] Favicon and page titles set
- [ ] 404 Not Found page
- [ ] Error boundary page

### 18.2 Prepare Demo Data

Seed the database with 2–3 realistic example projects for launch demos and onboarding:

```sql
-- Seed example projects for demo
INSERT INTO projects (user_id, name, idea_text, project_type, status) VALUES
(1, 'Smart Library System', 'A web app for managing university library books...', 'WEB_APP', 'COMPLETE'),
(1, 'Campus Navigation App', 'A mobile app to help students navigate campus...', 'MOBILE_APP', 'COMPLETE');
```

### 18.3 Presentation Preparation

1. **Create slides** using Google Slides (free) or PowerPoint
2. **Follow this structure** (from SRS Section 12):
   - Title slide
   - Problem statement
   - Solution overview
   - Live demo (or recorded video as backup)
   - Architecture
   - Tech stack
   - Challenges and solutions
   - Future improvements
   - Q&A
3. **Practice** the product walkthrough 2–3 times with a real script
4. **Record a short demo video** for onboarding, docs, and support

### 18.4 Final Documentation Checklist

- [ ] README.md with setup instructions
- [ ] SRS document (final version)
- [ ] Architecture diagram (export from draw.io)
- [ ] ERD diagram
- [ ] API documentation (Swagger URL or exported PDF)
- [ ] User manual (how to use the app)
- [ ] Source code is clean and commented
- [ ] Git history is clean (no sensitive data in commits)

---

## 19. Common Mistakes to Avoid

| # | Mistake                                              | Solution                                                |
|---|------------------------------------------------------|---------------------------------------------------------|
| 1 | Starting to code before designing                    | Lock requirements and architecture before implementation |
| 2 | Not using Git branches (pushing to main)             | Always use feature branches + pull requests              |
| 3 | Hardcoding secrets in code                           | Use environment variables (.env files)                   |
| 4 | Not testing until the end                            | Test each module as you build it                         |
| 5 | Trying to build everything at once                   | Follow the sprint plan, one feature at a time            |
| 6 | Ignoring error handling                              | Add proper error handling from day 1                     |
| 7 | Overcomplicating the ML service                      | Start simple: one LLM, one prompt per doc type           |
| 8 | Not communicating with team                          | Daily standups, even 5-minute text updates               |
| 9 | Scope creep (adding features mid-project)            | Stick to MoSCoW priorities                               |
| 10| Leaving deployment too late                          | Deploy staging early and production behind feature flags |
| 11| Not preparing demo and support assets                | Keep a short demo video and FAQ ready for users          |
| 12| Committing node_modules or .env files                | Check .gitignore is correct from day 1                   |

---

## 20. Helpful Resources

### 20.1 Learning Resources

| Topic                    | Resource                                                  | Link                                          |
|--------------------------|-----------------------------------------------------------|-----------------------------------------------|
| Spring Boot              | Spring Boot Tutorial (Baeldung)                           | https://www.baeldung.com/spring-boot          |
| Spring Security + JWT    | JWT Authentication Tutorial                               | https://www.baeldung.com/spring-security-jwt  |
| Next.js                  | Official Next.js Tutorial                                 | https://nextjs.org/learn                      |
| Tailwind CSS             | Official Documentation                                    | https://tailwindcss.com/docs                  |
| shadcn/ui                | Component Library                                         | https://ui.shadcn.com                         |
| FastAPI                  | Official Tutorial                                         | https://fastapi.tiangolo.com/tutorial/        |
| LangChain                | Official Documentation                                    | https://docs.langchain.com                    |
| Ollama                   | Official Documentation                                    | https://ollama.com                            |
| Docker                   | Docker for Beginners                                      | https://docker-curriculum.com/                |
| Git                      | Git Handbook                                              | https://guides.github.com/introduction/git-handbook/ |
| PostgreSQL               | PostgreSQL Tutorial                                       | https://www.postgresqltutorial.com/           |

### 20.2 YouTube Channels

| Channel                  | Best For                                                  |
|--------------------------|-----------------------------------------------------------|
| Amigoscode               | Spring Boot tutorials                                     |
| Traversy Media           | Full-stack web development                                |
| The Net Ninja            | Next.js and React                                         |
| Tech With Tim            | Python and FastAPI                                        |
| Fireship                 | Quick overviews of any technology                         |

### 20.3 Free Tools Recap

| Category       | Tool            | URL                              |
|----------------|-----------------|----------------------------------|
| Hosting        | Vercel          | https://vercel.com               |
| Hosting        | Railway         | https://railway.app              |
| Hosting        | Render          | https://render.com               |
| Database       | Supabase        | https://supabase.com             |
| AI/ML          | Ollama          | https://ollama.com               |
| AI/ML          | Groq (free API) | https://console.groq.com        |
| AI/ML          | Hugging Face    | https://huggingface.co           |
| Design         | Figma           | https://figma.com                |
| Diagrams       | draw.io         | https://app.diagrams.net         |
| Monitoring     | UptimeRobot     | https://uptimerobot.com          |
| Error Tracking | Sentry (free)   | https://sentry.io                |

---

_End of How To Do It Guide — Velocira v1.0_

