"use client";

import { useState, useEffect, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  FileText,
  Users,
  Database,
  Globe,
  Layers,
  Presentation,
  ArrowLeft,
  Download,
  PenLine,
  RefreshCcw,
  Check,
  Copy,
  Trash2,
  FileDown,
  FileType2,
  FileCode2,
  ChevronRight,
  CalendarDays,
  Tag,
  UsersRound,
  Server,
  Clock,
  Sparkles,
  Loader2,
  X,
  Hash,
  Shield,
  Cpu,
  Box,
  List,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";
import Link from "next/link";
import { useRouter, useParams } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type DocTab =
  | "srs"
  | "usecases"
  | "erd"
  | "api"
  | "architecture"
  | "presentation";

interface Section {
  id: string;
  title: string;
  content: string;
  table?: { headers: string[]; rows: string[][] };
  list?: string[];
}

/* ------------------------------------------------------------------ */
/*  Mock Project Data                                                  */
/* ------------------------------------------------------------------ */

const PROJECT: {
  id: string;
  name: string;
  status: "Draft" | "Generating" | "Complete";
  type: string;
  createdAt: string;
  updatedAt: string;
  techStack: string[];
  teamSize: number;
  owner: string;
  description: string;
} = {
  id: "proj_8f2a1c",
  name: "MediSync — Healthcare Management Platform",
  status: "Complete",
  type: "Web App",
  createdAt: "2026-02-10T14:22:00Z",
  updatedAt: "2026-02-24T09:45:00Z",
  techStack: ["Next.js", "Spring Boot", "PostgreSQL", "Redis", "AWS"],
  teamSize: 6,
  owner: "Sarah Chen",
  description:
    "A comprehensive healthcare management platform enabling patients, doctors, and administrators to manage appointments, medical records, prescriptions, and billing through a secure, HIPAA-compliant interface.",
};

/* ------------------------------------------------------------------ */
/*  Document Tabs Config                                               */
/* ------------------------------------------------------------------ */

const DOC_TABS: { key: DocTab; label: string; icon: typeof FileText }[] = [
  { key: "srs", label: "SRS Document", icon: FileText },
  { key: "usecases", label: "Use Cases", icon: Users },
  { key: "erd", label: "ERD", icon: Database },
  { key: "api", label: "API Structure", icon: Globe },
  { key: "architecture", label: "Architecture", icon: Layers },
  { key: "presentation", label: "Presentation", icon: Presentation },
];

/* ------------------------------------------------------------------ */
/*  Rich Mock Document Content                                         */
/* ------------------------------------------------------------------ */

const MOCK_DOCUMENTS: Record<DocTab, Section[]> = {
  srs: [
    {
      id: "srs-1",
      title: "1. Introduction",
      content:
        "MediSync is a next-generation healthcare management platform designed to streamline the interaction between patients, healthcare providers, and administrative staff. The system aims to digitize appointment scheduling, medical record management, prescription tracking, and billing workflows while maintaining strict compliance with HIPAA regulations and industry security standards.",
    },
    {
      id: "srs-2",
      title: "2. Project Scope",
      content:
        "The platform encompasses a patient-facing portal, a physician dashboard, an administrative control panel, and a secure API gateway. The initial release (v1.0) targets outpatient clinic workflows with support for up to 10,000 concurrent users. Future iterations will extend to inpatient management, telemedicine, and insurance claim automation.",
      list: [
        "Patient registration and profile management",
        "Appointment booking with real-time availability",
        "Electronic Health Records (EHR) with version history",
        "E-prescriptions with pharmacy integration",
        "Invoice generation and payment processing",
        "Role-based access control (RBAC) with audit logging",
        "HIPAA-compliant data encryption at rest and in transit",
        "Real-time notifications via email, SMS, and push",
      ],
    },
    {
      id: "srs-3",
      title: "3. Functional Requirements",
      content: "The following functional requirements have been identified for the MediSync platform:",
      table: {
        headers: ["ID", "Requirement", "Priority", "Module"],
        rows: [
          ["FR-001", "User registration with email verification and MFA", "High", "Auth"],
          ["FR-002", "Role-based login for Patient, Doctor, Admin", "High", "Auth"],
          ["FR-003", "Appointment booking with calendar integration", "High", "Scheduling"],
          ["FR-004", "Real-time doctor availability engine", "High", "Scheduling"],
          ["FR-005", "EHR creation, viewing, and version history", "High", "Records"],
          ["FR-006", "Prescription creation with drug interaction checks", "High", "Rx"],
          ["FR-007", "Invoice generation with itemized billing", "Medium", "Billing"],
          ["FR-008", "Stripe/PayPal payment gateway integration", "Medium", "Billing"],
          ["FR-009", "Admin dashboard with analytics and user management", "Medium", "Admin"],
          ["FR-010", "Notification engine (email, SMS, push)", "Medium", "Notifications"],
          ["FR-011", "PDF export of medical records and invoices", "Low", "Export"],
          ["FR-012", "Audit trail for all data modifications", "High", "Security"],
        ],
      },
    },
    {
      id: "srs-4",
      title: "4. Non-Functional Requirements",
      content: "System-wide quality attributes and constraints:",
      table: {
        headers: ["ID", "Category", "Requirement", "Target"],
        rows: [
          ["NFR-001", "Performance", "API response time under load", "< 200ms (p95)"],
          ["NFR-002", "Scalability", "Horizontal scaling support", "10K concurrent users"],
          ["NFR-003", "Security", "Data encryption standard", "AES-256 at rest, TLS 1.3 in transit"],
          ["NFR-004", "Availability", "System uptime SLA", "99.95%"],
          ["NFR-005", "Compliance", "Regulatory adherence", "HIPAA, SOC 2 Type II"],
          ["NFR-006", "Accessibility", "WCAG compliance level", "AA 2.1"],
        ],
      },
    },
    {
      id: "srs-5",
      title: "5. System Constraints",
      content:
        "The platform must operate within the following technical and regulatory constraints. All PHI (Protected Health Information) must be stored in HIPAA-compliant data centers located within the United States. The system must support deployment on AWS GovCloud for clients requiring FedRAMP compliance. All third-party integrations must undergo security review and sign BAA (Business Associate Agreements).",
      list: [
        "Primary cloud provider: AWS (us-east-1, us-west-2)",
        "Database: PostgreSQL 16 with pgcrypto extension",
        "Cache layer: Redis 7 with TLS-enabled connections",
        "CI/CD: GitHub Actions with SAST/DAST scanning",
        "Container orchestration: ECS Fargate (serverless)",
        "Monitoring: CloudWatch + Datadog APM",
      ],
    },
  ],

  usecases: [
    {
      id: "uc-1",
      title: "Use Case Overview",
      content:
        "The following use cases describe the primary interactions between system actors and the MediSync platform. Each use case outlines the actor, preconditions, main flow, and expected outcomes.",
      table: {
        headers: ["UC ID", "Use Case", "Primary Actor", "Priority"],
        rows: [
          ["UC-001", "Register New Patient Account", "Patient", "High"],
          ["UC-002", "Book Appointment", "Patient", "High"],
          ["UC-003", "View / Update Medical Records", "Doctor", "High"],
          ["UC-004", "Create E-Prescription", "Doctor", "High"],
          ["UC-005", "Process Payment", "Patient", "Medium"],
          ["UC-006", "Generate Billing Invoice", "System", "Medium"],
          ["UC-007", "Manage User Roles & Permissions", "Admin", "Medium"],
          ["UC-008", "View Platform Analytics", "Admin", "Low"],
          ["UC-009", "Send Appointment Reminder", "System", "Medium"],
          ["UC-010", "Export Medical Record as PDF", "Doctor / Patient", "Low"],
        ],
      },
    },
    {
      id: "uc-2",
      title: "UC-001: Register New Patient Account",
      content: "",
      list: [
        "Actor: Patient (unregistered)",
        "Precondition: User has a valid email address",
        "Main Flow: 1) Patient navigates to registration page → 2) Fills in name, email, DOB, phone → 3) System sends email verification → 4) Patient verifies email → 5) System creates account with Patient role → 6) Patient is redirected to profile setup",
        "Alternative Flow: If email already exists, system shows error and suggests login or password reset",
        "Postcondition: Patient account is created and verified; patient can log in",
        "Business Rule: Patients must be 18+ or have guardian consent",
      ],
    },
    {
      id: "uc-3",
      title: "UC-002: Book Appointment",
      content: "",
      list: [
        "Actor: Patient (authenticated)",
        "Precondition: Patient is logged in and has completed profile setup",
        "Main Flow: 1) Patient selects specialty or doctor → 2) System displays available time slots → 3) Patient selects date/time → 4) Patient confirms booking → 5) System reserves slot, sends confirmation email → 6) Doctor receives new appointment notification",
        "Alternative Flow: If no slots are available, system suggests next available dates or waitlist option",
        "Postcondition: Appointment is booked; both patient and doctor receive confirmation",
        "Business Rule: Appointments can be cancelled up to 4 hours before the scheduled time",
      ],
    },
    {
      id: "uc-4",
      title: "UC-004: Create E-Prescription",
      content: "",
      list: [
        "Actor: Doctor (authenticated)",
        "Precondition: Doctor has an active appointment or open patient record",
        "Main Flow: 1) Doctor opens patient record → 2) Clicks 'New Prescription' → 3) Searches drug database → 4) System performs drug interaction check → 5) Doctor sets dosage, frequency, duration → 6) System generates prescription with QR code → 7) Prescription is sent to patient and linked pharmacy",
        "Alternative Flow: If drug interaction is detected, system shows warning with severity level and alternative suggestions",
        "Postcondition: Prescription is created, digitally signed, and accessible to patient and pharmacy",
        "Business Rule: Controlled substances require additional MFA verification from the doctor",
      ],
    },
  ],

  erd: [
    {
      id: "erd-1",
      title: "Entity Overview",
      content:
        "The MediSync database schema is designed around the following core entities. Each entity is described with its primary attributes, data types, and relationships to other entities.",
      table: {
        headers: ["Entity", "Description", "Key Relationships"],
        rows: [
          ["User", "Base entity for all users (patients, doctors, admins)", "1:1 → Profile, 1:N → Appointments"],
          ["Patient", "Extended user profile with medical details", "1:N → Records, 1:N → Prescriptions"],
          ["Doctor", "Extended user profile with specialization", "1:N → Appointments, 1:N → Prescriptions"],
          ["Appointment", "Scheduled visit between patient and doctor", "N:1 → Patient, N:1 → Doctor"],
          ["MedicalRecord", "Patient health records and diagnoses", "N:1 → Patient, N:1 → Doctor"],
          ["Prescription", "E-prescription with drug details", "N:1 → Patient, N:1 → Doctor"],
          ["Invoice", "Billing invoice for services rendered", "N:1 → Patient, N:1 → Appointment"],
          ["AuditLog", "Immutable log of all data mutations", "N:1 → User"],
        ],
      },
    },
    {
      id: "erd-2",
      title: "User Entity — Attributes",
      content: "",
      table: {
        headers: ["Attribute", "Type", "Constraints", "Description"],
        rows: [
          ["id", "UUID", "PK, NOT NULL", "Unique identifier"],
          ["email", "VARCHAR(255)", "UNIQUE, NOT NULL", "Login email address"],
          ["password_hash", "VARCHAR(255)", "NOT NULL", "bcrypt hashed password"],
          ["full_name", "VARCHAR(150)", "NOT NULL", "User display name"],
          ["role", "ENUM", "NOT NULL", "PATIENT | DOCTOR | ADMIN"],
          ["email_verified", "BOOLEAN", "DEFAULT false", "Email verification status"],
          ["mfa_enabled", "BOOLEAN", "DEFAULT false", "Multi-factor auth toggle"],
          ["created_at", "TIMESTAMPTZ", "DEFAULT NOW()", "Account creation timestamp"],
          ["updated_at", "TIMESTAMPTZ", "ON UPDATE", "Last modification timestamp"],
          ["deleted_at", "TIMESTAMPTZ", "NULLABLE", "Soft delete timestamp"],
        ],
      },
    },
    {
      id: "erd-3",
      title: "Appointment Entity — Attributes",
      content: "",
      table: {
        headers: ["Attribute", "Type", "Constraints", "Description"],
        rows: [
          ["id", "UUID", "PK, NOT NULL", "Unique appointment identifier"],
          ["patient_id", "UUID", "FK → users.id", "Booking patient reference"],
          ["doctor_id", "UUID", "FK → users.id", "Assigned doctor reference"],
          ["scheduled_at", "TIMESTAMPTZ", "NOT NULL", "Appointment date and time"],
          ["duration_min", "INTEGER", "DEFAULT 30", "Duration in minutes"],
          ["status", "ENUM", "NOT NULL", "SCHEDULED | COMPLETED | CANCELLED | NO_SHOW"],
          ["notes", "TEXT", "NULLABLE", "Pre-appointment notes from patient"],
          ["cancellation_reason", "TEXT", "NULLABLE", "Reason if cancelled"],
          ["created_at", "TIMESTAMPTZ", "DEFAULT NOW()", "Record creation timestamp"],
        ],
      },
    },
    {
      id: "erd-4",
      title: "Indexes & Constraints",
      content:
        "Critical indexes are defined to optimize query performance for the most common access patterns in the healthcare workflow.",
      list: [
        "idx_appointments_patient_date — Composite index on (patient_id, scheduled_at) for patient schedule lookups",
        "idx_appointments_doctor_date — Composite index on (doctor_id, scheduled_at) for doctor availability queries",
        "idx_records_patient — B-tree index on patient_id for medical history retrieval",
        "idx_prescriptions_patient_status — Composite index on (patient_id, status) for active prescription queries",
        "idx_audit_user_timestamp — Composite index on (user_id, created_at) for audit trail queries",
        "UNIQUE constraint on (doctor_id, scheduled_at) to prevent double-booking",
        "CHECK constraint on duration_min: value must be between 15 and 120",
      ],
    },
  ],

  api: [
    {
      id: "api-1",
      title: "API Overview",
      content:
        "The MediSync REST API follows OpenAPI 3.1 standards with versioned endpoints under /api/v1. All endpoints require Bearer JWT authentication unless marked as public. Rate limiting is enforced at 100 requests per minute per user. Responses follow a consistent envelope format with status, data, message, and timestamp fields.",
    },
    {
      id: "api-2",
      title: "Authentication Endpoints",
      content: "",
      table: {
        headers: ["Method", "Endpoint", "Auth", "Description"],
        rows: [
          ["POST", "/api/v1/auth/register", "Public", "Register new user account"],
          ["POST", "/api/v1/auth/login", "Public", "Authenticate and receive JWT pair"],
          ["POST", "/api/v1/auth/refresh", "Refresh Token", "Refresh expired access token"],
          ["POST", "/api/v1/auth/logout", "Bearer JWT", "Invalidate current session tokens"],
          ["POST", "/api/v1/auth/verify-email", "Public", "Verify email with OTP code"],
          ["POST", "/api/v1/auth/forgot-password", "Public", "Request password reset email"],
          ["PUT", "/api/v1/auth/reset-password", "Reset Token", "Set new password with reset token"],
        ],
      },
    },
    {
      id: "api-3",
      title: "Appointment Endpoints",
      content: "",
      table: {
        headers: ["Method", "Endpoint", "Auth", "Description"],
        rows: [
          ["GET", "/api/v1/appointments", "Bearer JWT", "List user appointments with pagination"],
          ["POST", "/api/v1/appointments", "Bearer JWT (Patient)", "Book a new appointment"],
          ["GET", "/api/v1/appointments/:id", "Bearer JWT", "Get appointment details"],
          ["PUT", "/api/v1/appointments/:id", "Bearer JWT", "Update appointment (reschedule)"],
          ["DELETE", "/api/v1/appointments/:id", "Bearer JWT", "Cancel appointment"],
          ["GET", "/api/v1/doctors/:id/availability", "Bearer JWT", "Get doctor available time slots"],
          ["POST", "/api/v1/appointments/:id/complete", "Bearer JWT (Doctor)", "Mark appointment as completed"],
        ],
      },
    },
    {
      id: "api-4",
      title: "Medical Records Endpoints",
      content: "",
      table: {
        headers: ["Method", "Endpoint", "Auth", "Description"],
        rows: [
          ["GET", "/api/v1/patients/:id/records", "Bearer JWT", "List patient medical records"],
          ["POST", "/api/v1/patients/:id/records", "Bearer JWT (Doctor)", "Create new medical record"],
          ["GET", "/api/v1/records/:id", "Bearer JWT", "Get full record with history"],
          ["PUT", "/api/v1/records/:id", "Bearer JWT (Doctor)", "Update medical record"],
          ["GET", "/api/v1/records/:id/versions", "Bearer JWT", "Get record version history"],
          ["POST", "/api/v1/records/:id/export", "Bearer JWT", "Export record as PDF"],
        ],
      },
    },
    {
      id: "api-5",
      title: "Sample Request / Response",
      content:
        'POST /api/v1/appointments\nContent-Type: application/json\nAuthorization: Bearer eyJhbGciOiJIUzI1NiIs...\n\n{\n  "doctorId": "doc_4f29a1",\n  "scheduledAt": "2026-03-15T10:30:00Z",\n  "durationMin": 30,\n  "notes": "Follow-up for blood pressure monitoring"\n}\n\n→ 201 Created\n{\n  "status": "success",\n  "data": {\n    "id": "apt_7c3e2f",\n    "patientId": "pat_8f2a1c",\n    "doctorId": "doc_4f29a1",\n    "scheduledAt": "2026-03-15T10:30:00Z",\n    "status": "SCHEDULED"\n  },\n  "message": "Appointment booked successfully",\n  "timestamp": "2026-02-26T14:22:00Z"\n}',
    },
  ],

  architecture: [
    {
      id: "arch-1",
      title: "Architecture Overview",
      content:
        "MediSync follows a modular monolith architecture with clear domain boundaries, designed for eventual decomposition into microservices as the platform scales. The system uses a layered architecture pattern with API Gateway, Application Services, Domain Services, and Infrastructure layers. Event-driven communication between modules ensures loose coupling while maintaining transactional consistency within bounded contexts.",
    },
    {
      id: "arch-2",
      title: "Technology Stack Recommendation",
      content: "",
      table: {
        headers: ["Layer", "Technology", "Justification"],
        rows: [
          ["Frontend", "Next.js 15 (App Router)", "SSR/SSG, React Server Components, built-in API routes"],
          ["Backend", "Spring Boot 3.3 (Java 21)", "Enterprise-grade, robust security, excellent ORM support"],
          ["Database", "PostgreSQL 16 + pgcrypto", "ACID compliance, JSON support, encryption extensions"],
          ["Cache", "Redis 7 (Cluster mode)", "Session storage, rate limiting, real-time pub/sub"],
          ["Search", "Elasticsearch 8", "Full-text search across medical records and prescriptions"],
          ["Queue", "Amazon SQS + SNS", "Async processing for notifications, report generation"],
          ["Storage", "Amazon S3 (encrypted)", "Medical document and image storage with versioning"],
          ["CDN", "CloudFront", "Static asset delivery and edge caching"],
          ["Monitoring", "Datadog + CloudWatch", "APM, log aggregation, custom health dashboards"],
          ["CI/CD", "GitHub Actions", "Automated build, test, SAST/DAST, and deployment pipeline"],
        ],
      },
    },
    {
      id: "arch-3",
      title: "Module Decomposition",
      content:
        "The system is organized into the following bounded contexts, each encapsulating its own domain logic, data access, and API surface:",
      list: [
        "Auth Module — Registration, login, JWT management, MFA, RBAC, session management",
        "Patient Module — Patient profiles, preferences, consent management, medical history",
        "Doctor Module — Doctor profiles, specializations, schedule management, certifications",
        "Scheduling Module — Appointment CRUD, availability engine, conflict resolution, reminders",
        "Records Module — EHR management, version history, access control, document generation",
        "Prescription Module — Drug database, interaction checks, e-prescription workflow, pharmacy API",
        "Billing Module — Invoice generation, payment processing, refunds, financial reporting",
        "Notification Module — Email (SES), SMS (Twilio), push (FCM), template management",
        "Admin Module — User management, platform analytics, configuration, audit log viewer",
        "Gateway Module — API routing, rate limiting, request validation, CORS, load balancing",
      ],
    },
    {
      id: "arch-4",
      title: "Deployment Architecture",
      content:
        "The production environment runs on AWS with a multi-AZ deployment for high availability. The application is containerized using Docker and orchestrated via ECS Fargate (serverless). Infrastructure is managed as code using Terraform with separate stacks for networking, compute, database, and monitoring. Blue-green deployments are used for zero-downtime releases.",
      list: [
        "VPC with public, private, and isolated subnets across 3 AZs",
        "Application Load Balancer with WAF rules and TLS termination",
        "ECS Fargate services with auto-scaling (CPU/memory targets)",
        "RDS PostgreSQL Multi-AZ with automated backups and read replicas",
        "ElastiCache Redis cluster with automatic failover",
        "S3 buckets with server-side encryption (SSE-KMS) and lifecycle policies",
        "CloudFront distribution with custom domain and SSL certificates",
        "Route 53 for DNS management with health checks and failover routing",
      ],
    },
  ],

  presentation: [
    {
      id: "pres-1",
      title: "Slide 1 — Title Slide",
      content:
        'MediSync: AI-Powered Healthcare Management Platform\n\nSubtitle: "Transforming healthcare delivery through intelligent scheduling, secure records management, and seamless patient-provider collaboration."\n\nPresented by: Sarah Chen, Lead Architect\nDate: February 2026',
    },
    {
      id: "pres-2",
      title: "Slide 2 — The Problem",
      content: "",
      list: [
        "Healthcare providers spend 34% of their time on administrative tasks (AMA, 2025)",
        "Paper-based records lead to 12% error rate in prescriptions",
        "Average patient wait time for appointment booking: 3.2 days",
        "67% of clinics lack integrated billing and records systems",
        "Data fragmentation prevents effective care coordination",
      ],
    },
    {
      id: "pres-3",
      title: "Slide 3 — Our Solution",
      content:
        "MediSync is a unified platform that digitizes the entire healthcare workflow — from appointment booking to billing — with a focus on security, compliance, and user experience.\n\nKey Differentiators:\n• AI-assisted scheduling that predicts optimal appointment slots\n• Real-time drug interaction checking during prescription creation\n• HIPAA-compliant by design with end-to-end encryption\n• Unified patient portal with mobile-first responsive design",
    },
    {
      id: "pres-4",
      title: "Slide 4 — Core Features",
      content: "",
      list: [
        "Smart Scheduling — AI-powered availability engine with conflict resolution",
        "Electronic Health Records — Versioned, searchable, exportable medical records",
        "E-Prescriptions — Drug database with interaction alerts and pharmacy integration",
        "Billing & Payments — Automated invoicing with Stripe/PayPal integration",
        "Admin Dashboard — Real-time analytics, user management, and audit trails",
        "Notification Engine — Multi-channel alerts (email, SMS, push) with smart batching",
      ],
    },
    {
      id: "pres-5",
      title: "Slide 5 — Technical Architecture",
      content:
        "Frontend: Next.js 15 with React Server Components\nBackend: Spring Boot 3.3 on Java 21\nDatabase: PostgreSQL 16 with encrypted storage\nInfrastructure: AWS (ECS Fargate, RDS, ElastiCache, S3)\nSecurity: JWT + MFA, AES-256, TLS 1.3, RBAC\n\nScalable to 10,000+ concurrent users with 99.95% uptime SLA.",
    },
    {
      id: "pres-6",
      title: "Slide 6 — Implementation Roadmap",
      content: "",
      table: {
        headers: ["Phase", "Timeline", "Deliverables"],
        rows: [
          ["Phase 1: Foundation", "Weeks 1–4", "Auth, Patient/Doctor profiles, Database schema, CI/CD"],
          ["Phase 2: Core Features", "Weeks 5–10", "Scheduling engine, EHR module, Prescription workflow"],
          ["Phase 3: Billing & Admin", "Weeks 11–14", "Payment integration, Admin dashboard, Analytics"],
          ["Phase 4: Polish & Launch", "Weeks 15–18", "Performance tuning, Security audit, Beta testing, Launch"],
        ],
      },
    },
    {
      id: "pres-7",
      title: "Slide 7 — Thank You & Q&A",
      content:
        "Thank you for your attention!\n\nNext Steps:\n• Technical review and stakeholder sign-off\n• Phase 1 kickoff targeted for March 2026\n• Weekly sprint demos with stakeholder walkthroughs\n\nContact: sarah.chen@medisync.dev\nRepository: github.com/medisync/platform",
    },
  ],
};

/* ------------------------------------------------------------------ */
/*  Status badge helper                                                */
/* ------------------------------------------------------------------ */

const STATUS_STYLES: Record<string, string> = {
  Draft: "bg-foreground-secondary/10 text-foreground-secondary",
  Generating: "bg-warning/10 text-warning",
  Complete: "bg-success/10 text-success",
};

/* ------------------------------------------------------------------ */
/*  Toast Component                                                    */
/* ------------------------------------------------------------------ */

function Toast({
  message,
  type = "success",
  onClose,
}: {
  message: string;
  type?: "success" | "info";
  onClose: () => void;
}) {
  useEffect(() => {
    const timer = setTimeout(onClose, 3000);
    return () => clearTimeout(timer);
  }, [onClose]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 40, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: 20, scale: 0.95 }}
      className={`fixed bottom-24 right-6 z-50 flex items-center gap-3 px-5 py-3 rounded-xl border shadow-lg ${
        type === "success"
          ? "bg-success/10 border-success/30 text-success"
          : "bg-primary/10 border-primary/30 text-primary"
      }`}
    >
      {type === "success" ? (
        <Check className="h-4 w-4 shrink-0" />
      ) : (
        <Sparkles className="h-4 w-4 shrink-0" />
      )}
      <span className="text-sm font-medium">{message}</span>
      <button onClick={onClose} className="ms-2 opacity-60 hover:opacity-100 transition-opacity">
        <X className="h-3.5 w-3.5" />
      </button>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Section Card Component                                             */
/* ------------------------------------------------------------------ */

function SectionCard({
  section,
  onRegenerate,
}: {
  section: Section;
  onRegenerate: (id: string) => void;
}) {
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(section.content);
  const [regenerating, setRegenerating] = useState(false);

  const handleRegenerate = useCallback(() => {
    setRegenerating(true);
    setTimeout(() => {
      setRegenerating(false);
      onRegenerate(section.id);
    }, 2000);
  }, [onRegenerate, section.id]);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
    >
      <Card className="relative group" hover>
        {/* Section Header */}
        <div className="flex items-start justify-between gap-4 mb-4">
          <h3 className="text-lg font-semibold font-display text-foreground">
            {section.title}
          </h3>
          <div className="flex items-center gap-1.5 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
            <Button
              variant="ghost"
              size="sm"
              icon={<PenLine className="h-3.5 w-3.5" />}
              onClick={() => setIsEditing(!isEditing)}
              className="!px-2 !py-1"
            >
              {isEditing ? "Done" : "Edit"}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              icon={
                regenerating ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <RefreshCcw className="h-3.5 w-3.5" />
                )
              }
              onClick={handleRegenerate}
              disabled={regenerating}
              className="!px-2 !py-1"
            >
              {regenerating ? "Regenerating…" : "Regenerate"}
            </Button>
          </div>
        </div>

        {/* Regeneration Overlay */}
        <AnimatePresence>
          {regenerating && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 z-10 rounded-2xl bg-card/80 backdrop-blur-sm flex flex-col items-center justify-center gap-3"
            >
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
              >
                <Sparkles className="h-8 w-8 text-primary" />
              </motion.div>
              <p className="text-sm font-medium text-foreground-secondary">
                AI is regenerating this section…
              </p>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Content */}
        {isEditing ? (
          <textarea
            className="w-full min-h-[120px] bg-background-secondary border border-border rounded-xl p-4 text-sm text-foreground leading-relaxed focus:outline-none focus:border-primary resize-y font-mono"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
          />
        ) : (
          <>
            {section.content && (
              <div className="text-sm text-foreground-secondary leading-relaxed whitespace-pre-wrap mb-4">
                {section.content}
              </div>
            )}

            {/* Table */}
            {section.table && (
              <div className="overflow-x-auto rounded-xl border border-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-background-secondary">
                      {section.table.headers.map((h) => (
                        <th
                          key={h}
                          className="px-4 py-3 text-start font-semibold text-foreground text-xs uppercase tracking-wider"
                        >
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {section.table.rows.map((row, ri) => (
                      <motion.tr
                        key={ri}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: ri * 0.03 }}
                        className="border-t border-border hover:bg-background-secondary/50 transition-colors"
                      >
                        {row.map((cell, ci) => (
                          <td
                            key={ci}
                            className={`px-4 py-3 text-foreground-secondary ${
                              ci === 0 ? "font-mono text-primary text-xs" : ""
                            }`}
                          >
                            {cell}
                          </td>
                        ))}
                      </motion.tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* List */}
            {section.list && (
              <ul className="space-y-2.5">
                {section.list.map((item, i) => (
                  <motion.li
                    key={i}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.04 }}
                    className="flex items-start gap-3 text-sm text-foreground-secondary"
                  >
                    <span className="mt-1.5 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                    <span className="leading-relaxed">{item}</span>
                  </motion.li>
                ))}
              </ul>
            )}
          </>
        )}
      </Card>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page Component                                                     */
/* ------------------------------------------------------------------ */

export default function ProjectDetailPage() {
  const { t } = useLocale();
  void t;
  const router = useRouter();
  const params = useParams();
  const { isAuthenticated, isLoading } = useAuthStore();

  const [activeTab, setActiveTab] = useState<DocTab>("srs");
  const [toasts, setToasts] = useState<
    { id: number; message: string; type: "success" | "info" }[]
  >([]);
  const [deleteConfirm, setDeleteConfirm] = useState(false);

  /* ---- Auth guard ---- */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  /* ---- Toast helper ---- */
  const addToast = useCallback(
    (message: string, type: "success" | "info" = "success") => {
      const id = Date.now();
      setToasts((prev) => [...prev, { id, message, type }]);
    },
    []
  );

  const removeToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  /* ---- Export handler ---- */
  const handleExport = useCallback(
    (format: string) => {
      addToast(`Downloading ${format.toUpperCase()}…`, "info");
      setTimeout(() => {
        addToast(`${format.toUpperCase()} exported successfully!`, "success");
      }, 1800);
    },
    [addToast]
  );

  /* ---- Regenerate handler ---- */
  const handleRegenerate = useCallback(
    (sectionId: string) => {
      addToast("Section regenerated with AI!", "success");
    },
    [addToast]
  );

  /* ---- Delete handler ---- */
  const handleDelete = useCallback(() => {
    addToast("Project deleted. Redirecting…", "info");
    setTimeout(() => router.push("/dashboard"), 1500);
  }, [addToast, router]);

  /* ---- Quick nav sections ---- */
  const currentSections = MOCK_DOCUMENTS[activeTab];

  /* ---- Loading state ---- */
  if (isLoading || !isAuthenticated) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full"
        />
      </div>
    );
  }

  return (
    <PageTransition>
      <div className="relative min-h-screen">
        {/* Background decorations */}
        
        

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 pb-32">
          {/* ───────── Back Link ───────── */}
          <FadeIn>
            <Link
              href="/dashboard"
              className="inline-flex items-center gap-2 text-sm text-foreground-secondary hover:text-primary transition-colors mb-6 group"
            >
              <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
              Back to Dashboard
            </Link>
          </FadeIn>

          {/* ───────── Project Header ───────── */}
          <FadeIn delay={0.05}>
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-8">
              <div className="space-y-3">
                <div className="flex flex-wrap items-center gap-3">
                  <h1 className="text-2xl sm:text-3xl font-bold font-display gradient-text">
                    {PROJECT.name}
                  </h1>
                  <span
                    className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${
                      STATUS_STYLES[PROJECT.status]
                    }`}
                  >
                    {PROJECT.status === "Complete" && (
                      <Check className="h-3 w-3" />
                    )}
                    {PROJECT.status === "Generating" && (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    )}
                    {PROJECT.status}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-4 text-sm text-foreground-secondary">
                  <span className="inline-flex items-center gap-1.5">
                    <Tag className="h-3.5 w-3.5" />
                    {PROJECT.type}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <CalendarDays className="h-3.5 w-3.5" />
                    Created{" "}
                    {new Intl.DateTimeFormat("en-US", {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                    }).format(new Date(PROJECT.createdAt))}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5" />
                    Updated{" "}
                    {new Intl.DateTimeFormat("en-US", {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                    }).format(new Date(PROJECT.updatedAt))}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<Download className="h-4 w-4" />}
                  onClick={() => handleExport("pdf")}
                >
                  Quick Export
                </Button>
              </div>
            </div>
          </FadeIn>

          {/* ───────── Document Tabs ───────── */}
          <FadeIn delay={0.1}>
            <div className="mb-8 overflow-x-auto scrollbar-hide">
              <div className="flex gap-1.5 p-1.5 bg-background-secondary/60 rounded-2xl border border-border min-w-max">
                {DOC_TABS.map((tab) => {
                  const isActive = activeTab === tab.key;
                  return (
                    <button
                      key={tab.key}
                      onClick={() => setActiveTab(tab.key)}
                      className={`relative flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-all cursor-pointer ${
                        isActive
                          ? "text-primary"
                          : "text-foreground-secondary hover:text-foreground"
                      }`}
                    >
                      {isActive && (
                        <motion.div
                          layoutId="activeDocTab"
                          className="absolute inset-0 bg-card border border-border rounded-xl shadow-sm"
                          transition={{
                            type: "spring",
                            stiffness: 400,
                            damping: 30,
                          }}
                        />
                      )}
                      <span className="relative z-10 flex items-center gap-2">
                        <tab.icon className="h-4 w-4" />
                        <span className="hidden sm:inline">{tab.label}</span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          </FadeIn>

          {/* ───────── Main Content Grid ───────── */}
          <div className="grid lg:grid-cols-[1fr_320px] gap-8">
            {/* ── Document Viewer ── */}
            <div className="min-w-0">
              <AnimatePresence mode="wait">
                <motion.div
                  key={activeTab}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -12 }}
                  transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
                  className="space-y-5"
                >
                  {/* Doc header */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center">
                        {(() => {
                          const TabIcon =
                            DOC_TABS.find((t) => t.key === activeTab)?.icon ??
                            FileText;
                          return <TabIcon className="h-5 w-5 text-primary" />;
                        })()}
                      </div>
                      <div>
                        <h2 className="text-lg font-semibold font-display text-foreground">
                          {DOC_TABS.find((t) => t.key === activeTab)?.label}
                        </h2>
                        <p className="text-xs text-foreground-secondary">
                          {currentSections.length} section
                          {currentSections.length !== 1 ? "s" : ""} ·
                          Last generated Feb 24, 2026
                        </p>
                      </div>
                    </div>

                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<RefreshCcw className="h-3.5 w-3.5" />}
                      onClick={() =>
                        addToast("All sections regenerated!", "success")
                      }
                    >
                      Regenerate All
                    </Button>
                  </div>

                  {/* Section Cards */}
                  <StaggerContainer className="space-y-5">
                    {currentSections.map((section) => (
                      <StaggerItem key={section.id}>
                        <SectionCard
                          section={section}
                          onRegenerate={handleRegenerate}
                        />
                      </StaggerItem>
                    ))}
                  </StaggerContainer>
                </motion.div>
              </AnimatePresence>
            </div>

            {/* ── Sidebar ── */}
            <aside className="hidden lg:block space-y-5">
              {/* Export Panel */}
              <FadeIn delay={0.15}>
                <Card className="space-y-4">
                  <h3 className="text-sm font-semibold text-foreground font-display flex items-center gap-2">
                    <Download className="h-4 w-4 text-primary" />
                    Export Document
                  </h3>
                  <div className="grid gap-2.5">
                    {[
                      {
                        format: "PDF",
                        icon: FileDown,
                        desc: "Print-ready document",
                      },
                      {
                        format: "DOCX",
                        icon: FileType2,
                        desc: "Microsoft Word format",
                      },
                      {
                        format: "Markdown",
                        icon: FileCode2,
                        desc: "Developer-friendly format",
                      },
                    ].map((exp) => (
                      <motion.button
                        key={exp.format}
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.99 }}
                        onClick={() => handleExport(exp.format)}
                        className="flex items-center gap-3 w-full px-4 py-3 rounded-xl border border-border bg-background-secondary/50 hover:border-primary/30 hover:bg-primary/5 transition-all text-start cursor-pointer"
                      >
                        <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                          <exp.icon className="h-4 w-4 text-primary" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-foreground">
                            {exp.format}
                          </p>
                          <p className="text-xs text-foreground-secondary">
                            {exp.desc}
                          </p>
                        </div>
                        <ChevronRight className="h-4 w-4 text-foreground-secondary ms-auto" />
                      </motion.button>
                    ))}
                  </div>
                </Card>
              </FadeIn>

              {/* Project Info */}
              <FadeIn delay={0.2}>
                <Card className="space-y-4">
                  <h3 className="text-sm font-semibold text-foreground font-display flex items-center gap-2">
                    <Box className="h-4 w-4 text-primary" />
                    Project Info
                  </h3>
                  <div className="space-y-3">
                    {[
                      { label: "Type", value: PROJECT.type, icon: Tag },
                      {
                        label: "Tech Stack",
                        value: PROJECT.techStack.join(", "),
                        icon: Cpu,
                      },
                      {
                        label: "Team Size",
                        value: `${PROJECT.teamSize} members`,
                        icon: UsersRound,
                      },
                      { label: "Owner", value: PROJECT.owner, icon: Shield },
                      {
                        label: "Created",
                        value: new Intl.DateTimeFormat("en-US", {
                          month: "short",
                          day: "numeric",
                          year: "numeric",
                        }).format(new Date(PROJECT.createdAt)),
                        icon: CalendarDays,
                      },
                      {
                        label: "Last Updated",
                        value: new Intl.DateTimeFormat("en-US", {
                          month: "short",
                          day: "numeric",
                          year: "numeric",
                        }).format(new Date(PROJECT.updatedAt)),
                        icon: Clock,
                      },
                    ].map((item) => (
                      <div
                        key={item.label}
                        className="flex items-start gap-3 text-sm"
                      >
                        <item.icon className="h-4 w-4 text-foreground-secondary mt-0.5 shrink-0" />
                        <div>
                          <p className="text-xs text-foreground-secondary">
                            {item.label}
                          </p>
                          <p className="text-foreground font-medium">
                            {item.value}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Tech Stack Badges */}
                  <div className="pt-2 border-t border-border">
                    <p className="text-xs text-foreground-secondary mb-2">
                      Tech Stack
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {PROJECT.techStack.map((tech) => (
                        <span
                          key={tech}
                          className="px-2.5 py-1 rounded-lg text-xs font-medium bg-primary/10 text-primary"
                        >
                          {tech}
                        </span>
                      ))}
                    </div>
                  </div>
                </Card>
              </FadeIn>

              {/* Quick Navigation */}
              <FadeIn delay={0.25}>
                <Card className="space-y-3">
                  <h3 className="text-sm font-semibold text-foreground font-display flex items-center gap-2">
                    <List className="h-4 w-4 text-primary" />
                    Quick Navigation
                  </h3>
                  <nav className="space-y-0.5">
                    {currentSections.map((section, i) => (
                      <motion.button
                        key={section.id}
                        initial={{ opacity: 0, x: 10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: i * 0.04 }}
                        onClick={() => {
                          document
                            .getElementById(section.id)
                            ?.scrollIntoView({ behavior: "smooth", block: "center" });
                        }}
                        className="flex items-center gap-2 w-full px-3 py-2 rounded-lg text-sm text-foreground-secondary hover:text-primary hover:bg-primary/5 transition-all text-start cursor-pointer"
                      >
                        <Hash className="h-3 w-3 shrink-0 opacity-50" />
                        <span className="truncate">{section.title}</span>
                      </motion.button>
                    ))}
                  </nav>
                </Card>
              </FadeIn>
            </aside>
          </div>
        </div>

        {/* ───────── Floating Action Bar ───────── */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4, duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
          className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40"
        >
          <div className="flex items-center gap-2 px-4 py-2.5 rounded-2xl border border-border bg-card/90 backdrop-blur-xl shadow-xl">
            <Button
              variant="primary"
              size="sm"
              icon={<Download className="h-4 w-4" />}
              onClick={() => {
                addToast("Exporting all documents…", "info");
                setTimeout(
                  () => addToast("All documents exported!", "success"),
                  2000
                );
              }}
            >
              Export All
            </Button>
            <Button
              variant="outline"
              size="sm"
              icon={<Copy className="h-4 w-4" />}
              onClick={() => addToast("Project duplicated!", "success")}
            >
              Duplicate
            </Button>

            {/* Delete with confirmation */}
            <AnimatePresence mode="wait">
              {deleteConfirm ? (
                <motion.div
                  key="confirm"
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  className="flex items-center gap-1.5"
                >
                  <span className="text-xs text-error font-medium px-2">
                    Confirm?
                  </span>
                  <Button
                    variant="danger"
                    size="sm"
                    icon={<Check className="h-4 w-4" />}
                    onClick={handleDelete}
                  >
                    Yes
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={<X className="h-4 w-4" />}
                    onClick={() => setDeleteConfirm(false)}
                  >
                    No
                  </Button>
                </motion.div>
              ) : (
                <motion.div
                  key="delete"
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                >
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={<Trash2 className="h-4 w-4" />}
                    onClick={() => setDeleteConfirm(true)}
                    className="text-error hover:text-error"
                  >
                    Delete
                  </Button>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </motion.div>

        {/* ───────── Toasts ───────── */}
        <AnimatePresence>
          {toasts.map((toast) => (
            <Toast
              key={toast.id}
              message={toast.message}
              type={toast.type}
              onClose={() => removeToast(toast.id)}
            />
          ))}
        </AnimatePresence>
      </div>
    </PageTransition>
  );
}
