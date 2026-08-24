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
  themeColor: "#f5f5f7",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const themeCookie = cookieStore.get("theme")?.value;

  type Theme = "dark" | "light";
  const initialTheme: Theme = themeCookie === "dark" ? "dark" : "light";

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
