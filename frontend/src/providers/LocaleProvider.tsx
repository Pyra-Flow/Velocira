"use client";

import {
  createContext,
  useContext,
  useCallback,
  type ReactNode,
} from "react";
import { type Locale, defaultLocale, t } from "@/i18n";

interface LocaleContextType {
  locale: Locale;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LocaleContext = createContext<LocaleContextType | null>(null);

export function LocaleProvider({
  children,
}: {
  children: ReactNode;
  initialLocale?: Locale;
}) {
  const locale: Locale = defaultLocale;

  const translate = useCallback(
    (key: string, params?: Record<string, string | number>) =>
      t(locale, key, params),
    [locale]
  );

  return (
    <LocaleContext.Provider
      value={{
        locale,
        t: translate,
      }}
    >
      {children}
    </LocaleContext.Provider>
  );
}

export function useLocale() {
  const ctx = useContext(LocaleContext);
  if (!ctx) throw new Error("useLocale must be used within LocaleProvider");
  return ctx;
}
