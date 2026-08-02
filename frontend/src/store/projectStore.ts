import { create } from "zustand";
import {
  projectApi,
  type ProjectResponse,
  type CreateProjectRequest,
  type UpdateProjectRequest,
  type PaginatedData,
} from "@/lib/api";

/* ------------------------------------------------------------------ */
/*  State & Actions                                                    */
/* ------------------------------------------------------------------ */

interface ProjectState {
  /* data */
  projects: ProjectResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  selectedProject: ProjectResponse | null;

  /* ui */
  isLoading: boolean;
  isSubmitting: boolean;
  error: string | null;

  /* actions */
  fetchProjects: (params?: {
    page?: number;
    size?: number;
    search?: string;
    status?: string;
  }) => Promise<void>;
  fetchProject: (id: string) => Promise<ProjectResponse | null>;
  createProject: (data: CreateProjectRequest) => Promise<ProjectResponse | null>;
  updateProject: (id: string, data: UpdateProjectRequest) => Promise<ProjectResponse | null>;
  deleteProject: (id: string) => Promise<boolean>;
  duplicateProject: (id: string) => Promise<ProjectResponse | null>;
  archiveProject: (id: string) => Promise<ProjectResponse | null>;
  restoreProject: (id: string) => Promise<ProjectResponse | null>;
  setSelectedProject: (project: ProjectResponse | null) => void;
  clearError: () => void;
}

export const useProjectStore = create<ProjectState>((set, get) => ({
  /* ---- initial state ---- */
  projects: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 12,
  selectedProject: null,
  isLoading: false,
  isSubmitting: false,
  error: null,

  /* ---- fetch paginated list ---- */
  fetchProjects: async (params) => {
    set({ isLoading: true, error: null });
    const res = await projectApi.list({
      page: params?.page ?? get().currentPage,
      size: params?.size ?? get().pageSize,
      search: params?.search,
      status: params?.status,
    });
    if (res.success && res.data) {
      const page = res.data as PaginatedData<ProjectResponse>;
      set({
        projects: page.content,
        totalElements: page.totalElements,
        totalPages: page.totalPages,
        currentPage: page.number,
        isLoading: false,
      });
    } else {
      set({ isLoading: false, error: res.message });
    }
  },

  /* ---- fetch single ---- */
  fetchProject: async (id) => {
    set({ isLoading: true, error: null });
    const res = await projectApi.get(id);
    if (res.success && res.data) {
      set({ selectedProject: res.data, isLoading: false });
      return res.data;
    }
    set({ isLoading: false, error: res.message });
    return null;
  },

  /* ---- create ---- */
  createProject: async (data) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.create(data);
    if (res.success && res.data) {
      set((s) => ({
        projects: [res.data!, ...s.projects],
        totalElements: s.totalElements + 1,
        isSubmitting: false,
      }));
      return res.data;
    }
    set({ isSubmitting: false, error: res.message });
    return null;
  },

  /* ---- update ---- */
  updateProject: async (id, data) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.update(id, data);
    if (res.success && res.data) {
      set((s) => ({
        projects: s.projects.map((p) => (p.id === id ? res.data! : p)),
        selectedProject:
          s.selectedProject?.id === id ? res.data! : s.selectedProject,
        isSubmitting: false,
      }));
      return res.data;
    }
    set({ isSubmitting: false, error: res.message });
    return null;
  },

  /* ---- delete ---- */
  deleteProject: async (id) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.delete(id);
    if (res.success) {
      set((s) => ({
        projects: s.projects.filter((p) => p.id !== id),
        totalElements: s.totalElements - 1,
        selectedProject:
          s.selectedProject?.id === id ? null : s.selectedProject,
        isSubmitting: false,
      }));
      return true;
    }
    set({ isSubmitting: false, error: res.message });
    return false;
  },

  /* ---- duplicate ---- */
  duplicateProject: async (id) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.duplicate(id);
    if (res.success && res.data) {
      set((s) => ({
        projects: [res.data!, ...s.projects],
        totalElements: s.totalElements + 1,
        isSubmitting: false,
      }));
      return res.data;
    }
    set({ isSubmitting: false, error: res.message });
    return null;
  },

  archiveProject: async (id) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.archive(id);
    if (res.success && res.data) {
      set((s) => ({
        projects: s.projects.filter((project) => project.id !== id),
        totalElements: Math.max(0, s.totalElements - 1),
        selectedProject: s.selectedProject?.id === id ? res.data! : s.selectedProject,
        isSubmitting: false,
      }));
      return res.data;
    }
    set({ isSubmitting: false, error: res.message });
    return null;
  },

  restoreProject: async (id) => {
    set({ isSubmitting: true, error: null });
    const res = await projectApi.restore(id);
    if (res.success && res.data) {
      set((s) => ({
        projects: [res.data!, ...s.projects.filter((project) => project.id !== id)],
        totalElements: s.totalElements + 1,
        selectedProject: s.selectedProject?.id === id ? res.data! : s.selectedProject,
        isSubmitting: false,
      }));
      return res.data;
    }
    set({ isSubmitting: false, error: res.message });
    return null;
  },

  /* ---- misc ---- */
  setSelectedProject: (project) => set({ selectedProject: project }),
  clearError: () => set({ error: null }),
}));
