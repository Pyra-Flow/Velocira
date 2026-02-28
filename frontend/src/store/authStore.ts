import { create } from "zustand";
import Cookies from "js-cookie";
import { authApi, type UserDto, type AuthResponse } from "@/lib/api";

interface AuthState {
  user: UserDto | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;

  // Actions
  setUser: (user: UserDto | null) => void;
  setError: (error: string | null) => void;
  login: (email: string, password: string) => Promise<boolean>;
  register: (data: {
    fullName: string;
    email: string;
    password: string;
    universityName?: string;
  }) => Promise<{ success: boolean; needsVerification?: boolean }>;
  googleLogin: (idToken: string) => Promise<boolean>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
  fetchUser: () => Promise<void>;
  initialize: () => Promise<void>;
}

function storeTokens(data: AuthResponse) {
  Cookies.set("accessToken", data.accessToken, { expires: 1, secure: true, sameSite: "strict" });
  Cookies.set("refreshToken", data.refreshToken, { expires: 30, secure: true, sameSite: "strict" });
}

function clearTokens() {
  Cookies.remove("accessToken");
  Cookies.remove("refreshToken");
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  error: null,

  setUser: (user) =>
    set({ user, isAuthenticated: !!user, isLoading: false }),

  setError: (error) => set({ error }),

  login: async (email, password) => {
    set({ isLoading: true, error: null });
    const res = await authApi.login({ email, password });
    if (res.success && res.data) {
      storeTokens(res.data);
      set({
        user: res.data.user,
        isAuthenticated: true,
        isLoading: false,
        error: null,
      });
      return true;
    }
    set({ isLoading: false, error: res.message });
    return false;
  },

  register: async (data) => {
    set({ isLoading: true, error: null });
    const res = await authApi.register(data);
    if (res.success && res.data) {
      const needsVerification = !res.data.emailVerified;
      set({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
      });
      return { success: true, needsVerification };
    }
    set({ isLoading: false, error: res.message });
    return { success: false };
  },

  googleLogin: async (idToken) => {
    set({ isLoading: true, error: null });
    const res = await authApi.googleAuth(idToken);
    if (res.success && res.data) {
      storeTokens(res.data);
      set({
        user: res.data.user,
        isAuthenticated: true,
        isLoading: false,
        error: null,
      });
      return true;
    }
    set({ isLoading: false, error: res.message });
    return false;
  },

  logout: async () => {
    const refreshToken = Cookies.get("refreshToken");
    if (refreshToken) {
      await authApi.logout(refreshToken);
    }
    clearTokens();
    set({ user: null, isAuthenticated: false, isLoading: false, error: null });
  },

  logoutAll: async () => {
    await authApi.logoutAll();
    clearTokens();
    set({ user: null, isAuthenticated: false, isLoading: false, error: null });
  },

  fetchUser: async () => {
    const res = await authApi.me();
    if (res.success && res.data) {
      set({ user: res.data, isAuthenticated: true, isLoading: false });
    } else {
      clearTokens();
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },

  initialize: async () => {
    const token = Cookies.get("accessToken");
    if (token) {
      await get().fetchUser();
    } else {
      set({ isLoading: false });
    }
  },
}));
