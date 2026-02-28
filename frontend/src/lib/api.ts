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
let refreshSubscribers: ((token: string) => void)[] = [];

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
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
      Cookies.set("accessToken", data.data.accessToken, { expires: 1 });
      Cookies.set("refreshToken", data.data.refreshToken, { expires: 30 });
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
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

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
          onRefreshed(newToken);
          headers.Authorization = `Bearer ${newToken}`;
          res = await fetch(url, { ...options, headers, credentials: "include" });
        } else {
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
        const newToken = await new Promise<string>((resolve) => {
          addRefreshSubscriber(resolve);
        });
        headers.Authorization = `Bearer ${newToken}`;
        res = await fetch(url, { ...options, headers, credentials: "include" });
      }
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

export type ProjectStatus = "DRAFT" | "GENERATING" | "COMPLETE" | "FAILED";

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
};

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
