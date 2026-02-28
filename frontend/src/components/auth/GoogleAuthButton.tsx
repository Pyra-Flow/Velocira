"use client";

import { useState, useCallback } from "react";
import { GoogleLogin, type CredentialResponse } from "@react-oauth/google";
import { useAuthStore } from "@/store/authStore";
import { useLocale } from "@/providers/LocaleProvider";
import { useTheme } from "@/providers/ThemeProvider";
import { useRouter } from "next/navigation";

interface GoogleAuthButtonProps {
  text?: string;
  onError?: (error: string) => void;
}

export default function GoogleAuthButton({
  text,
  onError,
}: GoogleAuthButtonProps) {
  const { googleLogin } = useAuthStore();
  const { t } = useLocale();
  const { theme } = useTheme();
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(false);

  const handleSuccess = useCallback(
    async (credentialResponse: CredentialResponse) => {
      if (!credentialResponse.credential) {
        onError?.(t("errors.generic"));
        return;
      }

      setIsLoading(true);
      try {
        // Send the Google ID token to our backend for server-side verification
        const success = await googleLogin(credentialResponse.credential);
        if (success) {
          router.push("/home");
        } else {
          onError?.(t("errors.generic"));
        }
      } catch {
        onError?.(t("errors.generic"));
      } finally {
        setIsLoading(false);
      }
    },
    [googleLogin, router, onError, t]
  );

  const handleError = useCallback(() => {
    onError?.("Google Sign-In failed. Please try again or use email login.");
  }, [onError]);

  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  const isConfigured = clientId && clientId !== "your-google-client-id-here";

  // If Google client ID is not configured, show a disabled placeholder
  if (!isConfigured) {
    return (
      <div className="w-full">
        <button
          type="button"
          disabled
          className="w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl border border-border bg-card text-foreground-secondary text-sm font-medium opacity-60 cursor-not-allowed"
        >
          <svg className="h-5 w-5" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
            />
            <path
              fill="#34A853"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="#FBBC05"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
            />
            <path
              fill="#EA4335"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
            />
          </svg>
          {text || t("auth.login.google")}
        </button>
        <p className="text-xs text-foreground-secondary/50 text-center mt-1.5">
          Configure NEXT_PUBLIC_GOOGLE_CLIENT_ID to enable
        </p>
      </div>
    );
  }

  return (
    <div className="w-full flex flex-col items-center">
      {isLoading ? (
        <div className="w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl border border-border bg-card text-foreground-secondary text-sm font-medium">
          <div className="h-5 w-5 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          Signing in with Google...
        </div>
      ) : (
        <div className="w-full [&>div]:!w-full [&_iframe]:!w-full">
          <GoogleLogin
            onSuccess={handleSuccess}
            onError={handleError}
            theme={theme === "dark" ? "filled_black" : "outline"}
            size="large"
            width="400"
            text="continue_with"
            shape="pill"
          />
        </div>
      )}
    </div>
  );
}
