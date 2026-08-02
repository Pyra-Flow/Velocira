import { create } from "zustand";
import {
  generationJobApi,
  getGenerationJobId,
  type CreateGenerationJobRequest,
  type GenerationJobListData,
  type GenerationJobResponse,
  type GenerationJobStatus,
} from "@/lib/api";

export const ACTIVE_GENERATION_JOB_STATUSES: readonly GenerationJobStatus[] = [
  "QUEUED",
  "RETRIEVING",
  "DRAFTING",
  "VALIDATING",
];

export function isGenerationJobActive(status: GenerationJobStatus): boolean {
  return ACTIVE_GENERATION_JOB_STATUSES.includes(status);
}

function normaliseJobList(data: GenerationJobListData): GenerationJobResponse[] {
  return Array.isArray(data) ? data : data.content;
}

function newestFirst(jobs: GenerationJobResponse[]): GenerationJobResponse[] {
  return [...jobs].sort(
    (left, right) =>
      new Date(right.updatedAt ?? right.createdAt).getTime() -
      new Date(left.updatedAt ?? left.createdAt).getTime()
  );
}

function upsertJob(
  jobs: GenerationJobResponse[],
  job: GenerationJobResponse
): GenerationJobResponse[] {
  const jobId = getGenerationJobId(job);
  const existingIndex = jobs.findIndex(
    (candidate) => getGenerationJobId(candidate) === jobId
  );
  const next = [...jobs];

  if (existingIndex >= 0) {
    next[existingIndex] = job;
  } else {
    next.unshift(job);
  }

  return newestFirst(next);
}

interface GenerationJobState {
  jobsByProject: Record<string, GenerationJobResponse[]>;
  isLoadingByProject: Record<string, boolean>;
  isSubmittingByProject: Record<string, boolean>;
  errorByProject: Record<string, string | null>;

  fetchJobs: (projectId: string) => Promise<GenerationJobResponse[]>;
  createJob: (
    projectId: string,
    request: CreateGenerationJobRequest,
    idempotencyKey: string
  ) => Promise<GenerationJobResponse | null>;
  cancelJob: (
    projectId: string,
    jobId: string
  ) => Promise<GenerationJobResponse | null>;
  retryJob: (
    projectId: string,
    jobId: string,
    idempotencyKey: string
  ) => Promise<GenerationJobResponse | null>;
  clearError: (projectId: string) => void;
}

/**
 * Keeps job state separate from project state. Generation jobs outlive a page
 * visit, so every screen first reads the durable server-side list and only then
 * polls active jobs.
 */
export const useGenerationJobStore = create<GenerationJobState>((set, get) => ({
  jobsByProject: {},
  isLoadingByProject: {},
  isSubmittingByProject: {},
  errorByProject: {},

  fetchJobs: async (projectId) => {
    set((state) => ({
      isLoadingByProject: { ...state.isLoadingByProject, [projectId]: true },
      errorByProject: { ...state.errorByProject, [projectId]: null },
    }));

    const response = await generationJobApi.list(projectId);
    if (response.success && response.data) {
      const jobs = newestFirst(normaliseJobList(response.data));
      set((state) => ({
        jobsByProject: { ...state.jobsByProject, [projectId]: jobs },
        isLoadingByProject: { ...state.isLoadingByProject, [projectId]: false },
      }));
      return jobs;
    }

    set((state) => ({
      isLoadingByProject: { ...state.isLoadingByProject, [projectId]: false },
      errorByProject: { ...state.errorByProject, [projectId]: response.message },
    }));
    return get().jobsByProject[projectId] ?? [];
  },

  createJob: async (projectId, request, idempotencyKey) => {
    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: true,
      },
      errorByProject: { ...state.errorByProject, [projectId]: null },
    }));

    const response = await generationJobApi.create(
      projectId,
      request,
      idempotencyKey
    );
    if (response.success && response.data) {
      set((state) => ({
        jobsByProject: {
          ...state.jobsByProject,
          [projectId]: upsertJob(
            state.jobsByProject[projectId] ?? [],
            response.data!
          ),
        },
        isSubmittingByProject: {
          ...state.isSubmittingByProject,
          [projectId]: false,
        },
      }));
      return response.data;
    }

    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: false,
      },
      errorByProject: { ...state.errorByProject, [projectId]: response.message },
    }));
    return null;
  },

  cancelJob: async (projectId, jobId) => {
    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: true,
      },
      errorByProject: { ...state.errorByProject, [projectId]: null },
    }));

    const response = await generationJobApi.cancel(projectId, jobId);
    if (response.success && response.data) {
      set((state) => ({
        jobsByProject: {
          ...state.jobsByProject,
          [projectId]: upsertJob(
            state.jobsByProject[projectId] ?? [],
            response.data!
          ),
        },
        isSubmittingByProject: {
          ...state.isSubmittingByProject,
          [projectId]: false,
        },
      }));
      return response.data;
    }

    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: false,
      },
      errorByProject: { ...state.errorByProject, [projectId]: response.message },
    }));
    return null;
  },

  retryJob: async (projectId, jobId, idempotencyKey) => {
    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: true,
      },
      errorByProject: { ...state.errorByProject, [projectId]: null },
    }));

    const response = await generationJobApi.retry(
      projectId,
      jobId,
      idempotencyKey
    );
    if (response.success && response.data) {
      set((state) => ({
        jobsByProject: {
          ...state.jobsByProject,
          [projectId]: upsertJob(
            state.jobsByProject[projectId] ?? [],
            response.data!
          ),
        },
        isSubmittingByProject: {
          ...state.isSubmittingByProject,
          [projectId]: false,
        },
      }));
      return response.data;
    }

    set((state) => ({
      isSubmittingByProject: {
        ...state.isSubmittingByProject,
        [projectId]: false,
      },
      errorByProject: { ...state.errorByProject, [projectId]: response.message },
    }));
    return null;
  },

  clearError: (projectId) =>
    set((state) => ({
      errorByProject: { ...state.errorByProject, [projectId]: null },
    })),
}));
