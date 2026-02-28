import type { Metadata } from "next";
import { cookies } from "next/headers";
import { Space_Grotesk } from "next/font/google";
import "./globals.css";
import ClientProviders from "@/components/layout/ClientProviders";

const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
  variable: "--font-space-grotesk",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Velocira — AI-Powered Documentation Platform",
  description:
    "Generate professional project documentation in minutes with AI. SRS, ERD, API specs, and more.",
  keywords: ["documentation", "AI", "SRS", "project management", "Velocira", "PyraFlow"],
  authors: [{ name: "PyraFlow" }],
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const themeCookie = cookieStore.get("theme")?.value;

  type Theme = "dark" | "light";
  const initialTheme: Theme = themeCookie === "light" ? "light" : "dark";

  return (
    <html
      lang="en"
      dir="ltr"
      className={`${initialTheme} ${spaceGrotesk.variable}`}
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
