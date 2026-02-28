import en from "./en.json";

export const defaultLocale = "en" as const;
export type Locale = "en";

const messages = { en };

export function getMessages(locale: Locale) {
  return messages[locale] ?? messages.en;
}

// Flatten nested keys for easy access
type NestedKeyOf<T> = T extends object
  ? {
      [K in keyof T & string]: T[K] extends object
        ? `${K}.${NestedKeyOf<T[K]>}`
        : K;
    }[keyof T & string]
  : never;

export type TranslationKey = NestedKeyOf<typeof en>;

// Deep access utility
function getNestedValue(obj: Record<string, unknown>, path: string): string {
  const keys = path.split(".");
  let current: unknown = obj;
  for (const key of keys) {
    if (current && typeof current === "object" && key in current) {
      current = (current as Record<string, unknown>)[key];
    } else {
      return path;
    }
  }
  return typeof current === "string" ? current : path;
}

export function t(
  locale: Locale,
  key: string,
  params?: Record<string, string | number>
): string {
  let value = getNestedValue(
    messages[locale] as unknown as Record<string, unknown>,
    key
  );
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      value = value.replace(`{${k}}`, String(v));
    });
  }
  return value;
}
