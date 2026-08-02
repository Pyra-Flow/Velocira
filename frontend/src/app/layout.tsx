import type { Metadata, Viewport } from "next";
import { cookies } from "next/headers";
import "./globals.css";
import ClientProviders from "@/components/layout/ClientProviders";

export const metadata: Metadata = {
  title: "Velocira — Linked Project Documentation",
  description:
    "Build reviewed, traceable software requirements and linked documentation packages.",
  keywords: ["documentation", "AI", "SRS", "project management", "Velocira", "PyraFlow"],
  authors: [{ name: "PyraFlow" }],
  icons: {
    icon: [{ url: "/velocira-logo.png", type: "image/png" }],
    apple: "/velocira-logo.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#0A1020",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const themeCookie = cookieStore.get("theme")?.value;

  type Theme = "dark" | "light";
  // Signal Forge is deliberately dark-first. A saved light preference still
  // wins, but a first visit starts in the polished primary theme.
  const initialTheme: Theme = themeCookie === "light" ? "light" : "dark";

  return (
    <html
      lang="en"
      dir="ltr"
      className={initialTheme}
      suppressHydrationWarning
    >
      <body
        className="antialiased bg-background text-foreground"
        suppressHydrationWarning
      >
        <ClientProviders initialTheme={initialTheme}>
          {children}
        </ClientProviders>
      </body>
    </html>
  );
}
