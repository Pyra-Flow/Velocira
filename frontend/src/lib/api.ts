import Cookies from "js-cookie";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

export interface ApiResponse<T = unknown> {
  success: boolean;
  status: number;
  message: string;
  data: T | null;
  timestamp: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export interface UserDto {
  id: string;
  fullName: string;
  email: string;
  role: "USER" | "ADMIN";
  authProvider: "LOCAL" | "GOOGLE";
  emailVerified: boolean;
  universityName?: string;
  createdAt: string;
  lastLoginAt: string;
}

let isRefreshing = false;
let refreshSubscribers: ((token: string | null) => void)[] = [];

function notifyRefreshSubscribers(token: string | null) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(cb: (token: string | null) => void) {
  refreshSubscribers.push(cb);
}

function sessionCookieOptions(expires: number) {
  return {
    expires,
    sameSite: "strict" as const,
    secure: typeof window !== "undefined" && window.location.protocol === "https:",
  };
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = Cookies.get("refreshToken");
  if (!refreshToken) return null;

  try {
    const res = await fetch(`${API_BASE}/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    const data: ApiResponse<AuthResponse> = await res.json();
    if (data.success && data.data) {
      Cookies.set("accessToken", data.data.accessToken, sessionCookieOptions(1));
      Cookies.set("refreshToken", data.data.refreshToken, sessionCookieOptions(30));
      return data.data.accessToken;
    }
  } catch {
    // refresh failed
  }
  Cookies.remove("accessToken");
  Cookies.remove("refreshToken");
  return null;
}

export async function apiClient<T = unknown>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const url = `${API_BASE}${endpoint}`;
  const accessToken = Cookies.get("accessToken");

  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };
  // Browsers set the multipart boundary. Sending application/json here would
  // make a real evidence upload unreadable by Spring.
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  try {
    let res = await fetch(url, { ...options, headers, credentials: "include" });

    // If 401 and we have a refresh token, try to refresh
    if (res.status === 401 && Cookies.get("refreshToken")) {
      if (!isRefreshing) {
        isRefreshing = true;
        const newToken = await refreshAccessToken();
        isRefreshing = false;
        if (newToken) {
          notifyRefreshSubscribers(newToken);
          headers.Authorization = `Bearer ${newToken}`;
          res = await fetch(url, { ...options, headers, credentials: "include" });
        } else {
          notifyRefreshSubscribers(null);
          return {
            success: false,
            status: 401,
            message: "Session expired. Please login again.",
            data: null,
            timestamp: new Date().toISOString(),
          };
        }
      } else {
        // Wait for the refresh to complete
        const newToken = await new Promise<string | null>((resolve) => {
          addRefreshSubscriber(resolve);
        });
        if (!newToken) {
          return {
            success: false,
            status: 401,
            message: "Session expired. Please login again.",
            data: null,
            timestamp: new Date().toISOString(),
          };
        }
        headers.Authorization = `Bearer ${newToken}`;
        res = await fetch(url, { ...options, headers, credentials: "include" });
      }
    }

    if (res.status === 204) {
      return {
        success: res.ok,
        status: 204,
        message: "Operation completed successfully",
        data: null,
        timestamp: new Date().toISOString(),
      };
    }
    const data: ApiResponse<T> = await res.json();
    return data;
  } catch {
    return {
      success: false,
      status: 0,
      message: "Network error. Please check your connection.",
      data: null,
      timestamp: new Date().toISOString(),
    };
  }
}

// Auth API functions
export const authApi = {
  register: (body: {
    fullName: string;
    email: string;
    password: string;
    universityName?: string;
  }) =>
    apiClient<UserDto>("/v1/auth/register", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  login: (body: { email: string; password: string }) =>
    apiClient<AuthResponse>("/v1/auth/login", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  googleAuth: (idToken: string) =>
    apiClient<AuthResponse>("/v1/auth/google", {
      method: "POST",
      body: JSON.stringify({ idToken }),
    }),

  verifyEmail: (body: { email: string; otp: string }) =>
    apiClient("/v1/auth/verify-email", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  resendOtp: (email: string) =>
    apiClient("/v1/auth/resend-otp", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  forgotPassword: (email: string) =>
    apiClient("/v1/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  resetPassword: (body: { email: string; otp: string; newPassword: string }) =>
    apiClient("/v1/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  refresh: (refreshToken: string) =>
    apiClient<AuthResponse>("/v1/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }),

  logout: (refreshToken: string) =>
    apiClient("/v1/auth/logout", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }),

  logoutAll: () =>
    apiClient("/v1/auth/logout-all", {
      method: "POST",
    }),

  me: () => apiClient<UserDto>("/v1/auth/me", { method: "GET" }),
};

/* ================================================================== */
/*  Types — User Module                                                */
/* ================================================================== */

export interface UserProfileResponse {
  id: string;
  fullName: string;
  email: string;
  role: "USER" | "ADMIN";
  authProvider: "LOCAL" | "GOOGLE";
  emailVerified: boolean;
  universityName?: string;
  bio?: string;
  jobTitle?: string;
  company?: string;
  location?: string;
  websiteUrl?: string;
  projectCount: number;
  createdAt: string;
  lastLoginAt: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  universityName?: string;
  bio?: string;
  jobTitle?: string;
  company?: string;
  location?: string;
  websiteUrl?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/* ================================================================== */
/*  Types — Project Module                                             */
/* ================================================================== */

export type ProjectType =
  | "WEB_APP"
  | "MOBILE_APP"
  | "AI_SYSTEM"
  | "IOT"
  | "DESKTOP_APP"
  | "API_BACKEND";

export type ProjectStatus =
  | "DRAFT"
  | "DISCOVERY"
  | "READY_FOR_GENERATION"
  | "GENERATING"
  | "NEEDS_REVIEW"
  | "APPROVED"
  | "FAILED"
  | "ARCHIVED";

export interface ProjectResponse {
  id: string;
  name: string;
  description: string;
  type: ProjectType;
  status: ProjectStatus;
  techStack?: string;
  industry?: string;
  targetAudience?: string;
  teamSize?: number;
  progress: number;
  documentCount: number;
  ownerId: string;
  ownerName: string;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string | null;
}

export interface CreateProjectRequest {
  name: string;
  description: string;
  type: ProjectType;
  techStack?: string;
  industry?: string;
  targetAudience?: string;
  teamSize?: number;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  type?: ProjectType;
  techStack?: string;
  industry?: string;
  targetAudience?: string;
  teamSize?: number;
}

export interface UpdateProjectStatusRequest {
  status: ProjectStatus;
}

/* ================================================================== */
/*  Types — Discovery Interview                                        */
/* ================================================================== */

export type InterviewCategory =
  | "STAKEHOLDERS"
  | "PROBLEM"
  | "USERS"
  | "SCOPE"
  | "EXCLUSIONS"
  | "WORKFLOWS"
  | "BUSINESS_RULES"
  | "ENTITIES"
  | "INTEGRATIONS"
  | "QUALITY_GOALS"
  | "CONSTRAINTS"
  | "RISKS"
  | "METRICS";

export type InterviewAnswerDisposition = "ANSWERED" | "UNKNOWN" | "SKIPPED";
export type InterviewSessionStatus = "IN_PROGRESS" | "READY_FOR_CONFIRMATION" | "CONFIRMED";
export type OpenQuestionStatus = "OPEN" | "ACKNOWLEDGED_UNKNOWN" | "RESOLVED";
export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";

export interface InterviewQuestionResponse {
  questionKey: string;
  category: InterviewCategory;
  questionText: string;
  whyWeAsk: string;
  riskLevel: RiskLevel;
}

export interface InterviewAnswerResponse {
  id: string;
  questionKey: string;
  category: InterviewCategory;
  questionText: string;
  whyWeAsk: string;
  disposition: InterviewAnswerDisposition;
  answerText?: string | null;
  revisionNumber: number;
  current: boolean;
  createdAt: string;
}

export interface InterviewAssumptionResponse {
  id: string;
  category: InterviewCategory;
  statement: string;
  rationale: string;
  impact: RiskLevel;
  status: "OPEN" | "RESOLVED";
  material: boolean;
}

export interface InterviewOpenQuestionResponse {
  id: string;
  questionKey: string;
  category: InterviewCategory;
  questionText: string;
  reason: string;
  riskLevel: RiskLevel;
  status: OpenQuestionStatus;
  material: boolean;
}

export interface InterviewDecisionResponse {
  id: string;
  category: InterviewCategory;
  statement: string;
  rationale: string;
  status: "ACTIVE" | "SUPERSEDED";
}

export interface InterviewReadinessResponse {
  minimumComplete: boolean;
  generationReady: boolean;
  answeredRequiredCategories: number;
  requiredCategoryCount: number;
  blockers: string[];
  snapshot: Record<string, unknown>;
}

export interface InterviewBriefResponse {
  version: number;
  content: Record<string, unknown>;
  confirmedAt?: string | null;
}

export interface InterviewSessionResponse {
  id: string;
  projectId: string;
  status: InterviewSessionStatus;
  nextQuestion?: InterviewQuestionResponse | null;
  answers: InterviewAnswerResponse[];
  assumptions: InterviewAssumptionResponse[];
  openQuestions: InterviewOpenQuestionResponse[];
  decisions: InterviewDecisionResponse[];
  brief: InterviewBriefResponse;
  readiness: InterviewReadinessResponse;
  reopenedAt?: string | null;
  updatedAt: string;
}

export interface InterviewAnswerRequest {
  questionKey: string;
  disposition: InterviewAnswerDisposition;
  answerText?: string;
}

/* ================================================================== */
/*  Types — Governed evidence and SRS                                  */
/* ================================================================== */

export type KnowledgeSourceStatus =
  | "PENDING_REVIEW"
  | "APPROVED"
  | "QUARANTINED"
  | "REJECTED"
  | "EXPIRED"
  | "DELETED";

export interface KnowledgeSourceResponse {
  id: string;
  title: string;
  originalFilename: string;
  mediaType: string;
  classification: string;
  status: KnowledgeSourceStatus;
  scanMetadata: Record<string, unknown>;
  chunkCount: number;
  approvedAt?: string | null;
  createdAt: string;
}

export interface StandardsProfileResponse {
  key: "STARTER" | "STARTUP" | string;
  name: string;
  description: string;
  controls: string[];
  sourceLicense: string;
  ownerName: string;
  effectiveDate: string;
}

export interface SrsTraceLinkResponse {
  sourceId?: string | null;
  chunkId?: string | null;
  linkType: "EVIDENCE" | "ASSUMPTION" | "CONTROL" | string;
}

export interface SrsRequirementResponse {
  id: string;
  requirementId: string;
  type: "FUNCTIONAL" | "NON_FUNCTIONAL" | string;
  priority: "MUST" | "SHOULD" | "COULD" | string;
  statement: string;
  rationale: string;
  acceptanceCriteria: string;
  sourceKind: "CITATION" | "ASSUMPTION" | string;
  sourceDetail: string;
  verificationMethod: string;
  qualityOutcome: Record<string, unknown>;
  traceLinks: SrsTraceLinkResponse[];
}

export interface SrsVersionResponse {
  id: string;
  versionNumber: number;
  status: "DRAFT" | "NEEDS_REVIEW" | "APPROVED" | "CHANGES_REQUESTED";
  profileKey: string;
  content: Record<string, unknown>;
  validation: { valid?: boolean; issues?: string[]; citation_coverage?: number; citationCoverage?: number };
  citationCoverage: number;
  provider: string;
  model: string;
  promptVersion: string;
  generatedAt: string;
  approvedAt?: string | null;
  changeRequest?: string | null;
  requirements: SrsRequirementResponse[];
}

/* ================================================================== */
/*  Types — Linked documentation package                               */
/* ================================================================== */

export type DocumentationArtifactType = "SRS" | "USE_CASES" | "ERD" | "OPENAPI" | "TRACEABILITY";
export type DocumentationExportFormat = "ZIP" | "MARKDOWN" | "PDF" | "DOCX" | "OPENAPI_JSON" | "OPENAPI_YAML" | "UML_SOURCE" | "ERD_SOURCE";

export interface DocumentationArtifactResponse {
  type: DocumentationArtifactType;
  title: string;
  content: string;
  sourceFormat: string;
  sourceContent: string;
  checksum: string;
  validation: { valid?: boolean; validator?: string; issues?: string[] };
}

export interface DocumentationTraceResponse {
  requirementId: string;
  useCaseId?: string | null;
  entityId?: string | null;
  apiOperationId?: string | null;
  acceptanceCriterionId: string;
  sourceKind: string;
}

export interface DocumentationPackageResponse {
  id: string;
  versionNumber: number;
  status: "NEEDS_REVIEW" | "APPROVED" | "CHANGES_REQUESTED" | string;
  srsVersionId: string;
  generatedAt: string;
  approvedAt?: string | null;
  canonicalModel: Record<string, unknown>;
  validation: { valid?: boolean; issues?: string[]; requirementsChecked?: number; traceLinksChecked?: number };
  artifacts: DocumentationArtifactResponse[];
  traceLinks: DocumentationTraceResponse[];
}

export interface DocumentationExportResponse {
  id: string;
  format: DocumentationExportFormat;
  status: "READY" | "FAILED" | string;
  filename: string;
  contentType: string;
  byteSize: number;
  sha256: string;
  completedAt?: string | null;
  createdAt: string;
}

/* ================================================================== */
/*  Types — Document Module                                            */
/* ================================================================== */

export type DocumentType =
  | "SRS"
  | "BRD"
  | "TECHNICAL_ARCHITECTURE"
  | "API_SPECIFICATION"
  | "DATABASE_SCHEMA"
  | "UI_UX_DESIGN"
  | "TEST_PLAN"
  | "DEPLOYMENT_GUIDE"
  | "PROJECT_TIMELINE"
  | "RISK_ASSESSMENT"
  | "USER_MANUAL";

export type DocumentStatus =
  | "PENDING"
  | "GENERATING"
  | "COMPLETED"
  | "FAILED"
  | "EDITED";

export interface DocumentResponse {
  id: string;
  projectId: string;
  projectName: string;
  type: DocumentType;
  status: DocumentStatus;
  title: string;
  content?: string;
  version: number;
  wordCount: number;
  aiModel?: string;
  generationTimeMs?: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDocumentRequest {
  type: DocumentType;
  title: string;
}

export interface UpdateDocumentRequest {
  title?: string;
  content?: string;
}

/* ================================================================== */
/*  Types — Generation Jobs                                            */
/* ================================================================== */

/**
 * A generation job is the durable server-side record for one request.
 * These statuses deliberately describe work stages instead of inventing a
 * percentage, which would be misleading while providers execute remotely.
 */
export type GenerationJobStatus =
  | "QUEUED"
  | "RETRIEVING"
  | "DRAFTING"
  | "VALIDATING"
  | "NEEDS_INPUT"
  | "READY"
  | "FAILED"
  | "CANCELLED";

export interface GenerationJobResponse {
  /** Backends may expose either id or jobId; clients normalise it with getGenerationJobId. */
  id?: string;
  jobId?: string;
  projectId: string;
  status: GenerationJobStatus;
  statusMessage?: string | null;
  message?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  userMessage?: string | null;
  retryable?: boolean;
  attempt?: number;
  attemptCount?: number;
  maxAttempts?: number;
  cancelRequested?: boolean;
  correlationId?: string;
  idempotencyKey?: string;
  documentId?: string | null;
  artifactVersionId?: string | null;
  requestedDocumentType?: DocumentType;
  queuedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  cancelledAt?: string | null;
  nextAttemptAt?: string | null;
}

export interface CreateGenerationJobRequest {
  /** The backend currently uses SRS for its small structured test artifact. */
  documentType: DocumentType;
  additionalInstructions?: string;
}

export type GenerationJobListData =
  | GenerationJobResponse[]
  | PaginatedData<GenerationJobResponse>;

export function getGenerationJobId(job: GenerationJobResponse): string | null {
  return job.id ?? job.jobId ?? null;
}

/* ================================================================== */
/*  Types — Admin Module                                               */
/* ================================================================== */

export interface AdminUserResponse {
  id: string;
  fullName: string;
  email: string;
  role: "USER" | "ADMIN";
  authProvider: "LOCAL" | "GOOGLE";
  emailVerified: boolean;
  active: boolean;
  universityName?: string;
  projectCount: number;
  createdAt: string;
  lastLoginAt: string;
}

export interface AdminAnalyticsResponse {
  totalUsers: number;
  newUsersLast30Days: number;
  totalProjects: number;
  newProjectsLast30Days: number;
  totalDocuments: number;
  completedDocuments: number;
  projectsByType: Record<string, number>;
  activeUsersLast7Days: number;
  totalAuditEvents: number;
}

export interface AuditLogResponse {
  id: string;
  userId: string;
  email: string;
  action: string;
  details?: string;
  ipAddress?: string;
  deviceInfo?: string;
  success: boolean;
  createdAt: string;
}

/* ================================================================== */
/*  Paginated Response Wrapper                                         */
/* ================================================================== */

export interface PaginatedData<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/* ================================================================== */
/*  User API                                                           */
/* ================================================================== */

export const userApi = {
  getProfile: () =>
    apiClient<UserProfileResponse>("/v1/users/me", { method: "GET" }),

  updateProfile: (body: UpdateProfileRequest) =>
    apiClient<UserProfileResponse>("/v1/users/me", {
      method: "PUT",
      body: JSON.stringify(body),
    }),

  changePassword: (body: ChangePasswordRequest) =>
    apiClient<void>("/v1/users/me/password", {
      method: "PUT",
      body: JSON.stringify(body),
    }),

  deleteAccount: () =>
    apiClient<void>("/v1/users/me", { method: "DELETE" }),
};

/* ================================================================== */
/*  Project API                                                        */
/* ================================================================== */

export const projectApi = {
  list: (params?: { page?: number; size?: number; search?: string; status?: string }) => {
    const query = new URLSearchParams();
    if (params?.page != null) query.set("page", String(params.page));
    if (params?.size != null) query.set("size", String(params.size));
    if (params?.search) query.set("search", params.search);
    if (params?.status) query.set("status", params.status);
    const qs = query.toString();
    return apiClient<PaginatedData<ProjectResponse>>(`/v1/projects${qs ? `?${qs}` : ""}`, {
      method: "GET",
    });
  },

  get: (id: string) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}`, { method: "GET" }),

  create: (body: CreateProjectRequest) =>
    apiClient<ProjectResponse>("/v1/projects", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  update: (id: string, body: UpdateProjectRequest) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),

  delete: (id: string) =>
    apiClient<void>(`/v1/projects/${id}`, { method: "DELETE" }),

  duplicate: (id: string) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}/duplicate`, { method: "POST" }),

  updateStatus: (id: string, body: UpdateProjectStatusRequest) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),

  archive: (id: string) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}/archive`, { method: "POST" }),

  restore: (id: string) =>
    apiClient<ProjectResponse>(`/v1/projects/${id}/restore`, { method: "POST" }),
};

/* ================================================================== */
/*  Discovery Interview API                                            */
/* ================================================================== */

export const interviewApi = {
  start: (projectId: string) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview/start`, {
      method: "POST",
    }),

  summary: (projectId: string) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview`, {
      method: "GET",
    }),

  answer: (projectId: string, body: InterviewAnswerRequest) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview/answers`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  reviseAnswer: (projectId: string, answerId: string, body: InterviewAnswerRequest) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview/answers/${answerId}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),

  confirm: (projectId: string) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview/confirm`, {
      method: "POST",
    }),

  reopen: (projectId: string) =>
    apiClient<InterviewSessionResponse>(`/v1/projects/${projectId}/interview/reopen`, {
      method: "POST",
    }),
};

/* ================================================================== */
/*  Governed evidence / SRS API                                        */
/* ================================================================== */

export const knowledgeSourceApi = {
  list: (projectId: string) =>
    apiClient<KnowledgeSourceResponse[]>(`/v1/projects/${projectId}/knowledge-sources`, { method: "GET" }),

  upload: (projectId: string, file: File, title?: string) => {
    const body = new FormData();
    body.set("file", file);
    if (title?.trim()) body.set("title", title.trim());
    return apiClient<KnowledgeSourceResponse>(`/v1/projects/${projectId}/knowledge-sources`, { method: "POST", body });
  },

  approve: (projectId: string, sourceId: string) =>
    apiClient<KnowledgeSourceResponse>(`/v1/projects/${projectId}/knowledge-sources/${sourceId}/approve`, { method: "POST" }),

  reject: (projectId: string, sourceId: string) =>
    apiClient<KnowledgeSourceResponse>(`/v1/projects/${projectId}/knowledge-sources/${sourceId}/reject`, { method: "POST" }),

  delete: (projectId: string, sourceId: string) =>
    apiClient<void>(`/v1/projects/${projectId}/knowledge-sources/${sourceId}`, { method: "DELETE" }),
};

export const srsApi = {
  profiles: (projectId: string) =>
    apiClient<StandardsProfileResponse[]>(`/v1/projects/${projectId}/srs/profiles`, { method: "GET" }),

  list: (projectId: string) =>
    apiClient<SrsVersionResponse[]>(`/v1/projects/${projectId}/srs`, { method: "GET" }),

  generate: (projectId: string, profileKey: string) =>
    apiClient<SrsVersionResponse>(`/v1/projects/${projectId}/srs/generate`, {
      method: "POST", body: JSON.stringify({ profileKey }),
    }),

  approve: (projectId: string, versionId: string) =>
    apiClient<SrsVersionResponse>(`/v1/projects/${projectId}/srs/${versionId}/approve`, { method: "POST" }),

  requestChanges: (projectId: string, versionId: string, message: string) =>
    apiClient<SrsVersionResponse>(`/v1/projects/${projectId}/srs/${versionId}/request-changes`, {
      method: "POST", body: JSON.stringify({ message }),
    }),
};

export const documentationPackageApi = {
  list: (projectId: string) =>
    apiClient<DocumentationPackageResponse[]>(`/v1/projects/${projectId}/documentation-packages`, { method: "GET" }),
  generate: (projectId: string, srsVersionId: string) =>
    apiClient<DocumentationPackageResponse>(`/v1/projects/${projectId}/documentation-packages`, {
      method: "POST", body: JSON.stringify({ srsVersionId }),
    }),
  approve: (projectId: string, packageId: string) =>
    apiClient<DocumentationPackageResponse>(`/v1/projects/${projectId}/documentation-packages/${packageId}/approve`, { method: "POST" }),
  exports: (projectId: string, packageId: string) =>
    apiClient<DocumentationExportResponse[]>(`/v1/projects/${projectId}/documentation-packages/${packageId}/exports`, { method: "GET" }),
  export: (projectId: string, packageId: string, format: DocumentationExportFormat) =>
    apiClient<DocumentationExportResponse>(`/v1/projects/${projectId}/documentation-packages/${packageId}/exports`, {
      method: "POST", body: JSON.stringify({ format }),
    }),
};

export async function downloadDocumentationExport(projectId: string, packageId: string, exportId: string, filename: string): Promise<string | null> {
  const token = Cookies.get("accessToken");
  try {
    const response = await fetch(`${API_BASE}/v1/projects/${projectId}/documentation-packages/${packageId}/exports/${exportId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}, credentials: "include",
    });
    if (!response.ok) return "The export could not be downloaded.";
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a"); link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
    URL.revokeObjectURL(url);
    return null;
  } catch { return "Network error while downloading the export."; }
}

/* ================================================================== */
/*  Document API                                                       */
/* ================================================================== */

export const documentApi = {
  list: (projectId: string, params?: { page?: number; size?: number; search?: string; status?: string }) => {
    const query = new URLSearchParams();
    if (params?.page != null) query.set("page", String(params.page));
    if (params?.size != null) query.set("size", String(params.size));
    if (params?.search) query.set("search", params.search);
    if (params?.status) query.set("status", params.status);
    const qs = query.toString();
    return apiClient<PaginatedData<DocumentResponse>>(
      `/v1/projects/${projectId}/documents${qs ? `?${qs}` : ""}`,
      { method: "GET" }
    );
  },

  listAll: (projectId: string) =>
    apiClient<DocumentResponse[]>(`/v1/projects/${projectId}/documents/all`, {
      method: "GET",
    }),

  get: (projectId: string, documentId: string) =>
    apiClient<DocumentResponse>(
      `/v1/projects/${projectId}/documents/${documentId}`,
      { method: "GET" }
    ),

  create: (projectId: string, body: CreateDocumentRequest) =>
    apiClient<DocumentResponse>(`/v1/projects/${projectId}/documents`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  update: (projectId: string, documentId: string, body: UpdateDocumentRequest) =>
    apiClient<DocumentResponse>(
      `/v1/projects/${projectId}/documents/${documentId}`,
      { method: "PUT", body: JSON.stringify(body) }
    ),

  delete: (projectId: string, documentId: string) =>
    apiClient<void>(
      `/v1/projects/${projectId}/documents/${documentId}`,
      { method: "DELETE" }
  ),
};

/* ================================================================== */
/*  Generation Job API                                                 */
/* ================================================================== */

export const generationJobApi = {
  list: (projectId: string) =>
    apiClient<GenerationJobListData>(
      `/v1/projects/${projectId}/generation-jobs`,
      { method: "GET" }
    ),

  get: (projectId: string, jobId: string) =>
    apiClient<GenerationJobResponse>(
      `/v1/projects/${projectId}/generation-jobs/${jobId}`,
      { method: "GET" }
    ),

  create: (
    projectId: string,
    body: CreateGenerationJobRequest,
    idempotencyKey: string
  ) =>
    apiClient<GenerationJobResponse>(
      `/v1/projects/${projectId}/generation-jobs`,
      {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(body),
      }
    ),

  cancel: (projectId: string, jobId: string) =>
    apiClient<GenerationJobResponse>(
      `/v1/projects/${projectId}/generation-jobs/${jobId}/cancel`,
      { method: "POST" }
    ),

  retry: (projectId: string, jobId: string, idempotencyKey: string) =>
    apiClient<GenerationJobResponse>(
      `/v1/projects/${projectId}/generation-jobs/${jobId}/retry`,
      {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
      }
    ),
};

/* ================================================================== */
/*  Admin API                                                          */
/* ================================================================== */

export const adminApi = {
  listUsers: (params?: { page?: number; size?: number; search?: string }) => {
    const query = new URLSearchParams();
    if (params?.page != null) query.set("page", String(params.page));
    if (params?.size != null) query.set("size", String(params.size));
    if (params?.search) query.set("search", params.search);
    const qs = query.toString();
    return apiClient<PaginatedData<AdminUserResponse>>(
      `/v1/admin/users${qs ? `?${qs}` : ""}`,
      { method: "GET" }
    );
  },

  getUser: (id: string) =>
    apiClient<AdminUserResponse>(`/v1/admin/users/${id}`, { method: "GET" }),

  suspendUser: (id: string) =>
    apiClient<void>(`/v1/admin/users/${id}/suspend`, { method: "PUT" }),

  activateUser: (id: string) =>
    apiClient<void>(`/v1/admin/users/${id}/activate`, { method: "PUT" }),

  changeRole: (id: string, role: "USER" | "ADMIN") =>
    apiClient<void>(`/v1/admin/users/${id}/role`, {
      method: "PUT",
      body: JSON.stringify({ role }),
    }),

  deleteUser: (id: string) =>
    apiClient<void>(`/v1/admin/users/${id}`, { method: "DELETE" }),

  analytics: () =>
    apiClient<AdminAnalyticsResponse>("/v1/admin/analytics", { method: "GET" }),

  auditLogs: (params?: { page?: number; size?: number; userId?: string; action?: string }) => {
    const query = new URLSearchParams();
    if (params?.page != null) query.set("page", String(params.page));
    if (params?.size != null) query.set("size", String(params.size));
    if (params?.userId) query.set("userId", params.userId);
    if (params?.action) query.set("action", params.action);
    const qs = query.toString();
    return apiClient<PaginatedData<AuditLogResponse>>(
      `/v1/admin/audit-logs${qs ? `?${qs}` : ""}`,
      { method: "GET" }
    );
  },
};
