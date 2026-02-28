"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  Zap,
  Menu,
  X,
  Sun,
  Moon,
  LogOut,
  LayoutDashboard,
  FolderKanban,
  Settings,
  ShieldCheck,
  ChevronDown,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useTheme } from "@/providers/ThemeProvider";
import { useAuthStore } from "@/store/authStore";
import { cn } from "@/lib/utils";
import Button from "@/components/ui/Button";

export default function Navbar() {
  const { t } = useLocale();
  const { theme, toggleTheme } = useTheme();
  const { isAuthenticated, user, logout } = useAuthStore();
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement | null>(null);

  /* ── scroll detection ─────────────────────────────────── */
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  /* ── close mobile menu on route change ────────────────── */
  useEffect(() => {
    setMobileOpen(false);
    setProfileOpen(false);
  }, [pathname]);

  /* ── outside-click handler for profile dropdown ───────── */
  useEffect(() => {
    const onOutsideClick = (event: MouseEvent) => {
      if (!profileRef.current) return;
      if (!profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener("mousedown", onOutsideClick);
    return () => document.removeEventListener("mousedown", onOutsideClick);
  }, []);

  /* ── nav links ────────────────────────────────────────── */
  const navLinks = isAuthenticated
    ? [
        { href: "/home", label: t("nav.home") },
        { href: "/projects", label: t("nav.projects") },
        { href: "/dashboard", label: t("nav.dashboard") },
      ]
    : [
        { href: "/", label: t("nav.home") },
        { href: "/features", label: t("nav.features") },
        { href: "/pricing", label: t("nav.pricing") },
        { href: "/docs", label: t("nav.docs") },
      ];

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

  const iconButtonClass =
    "flex h-10 w-10 items-center justify-center rounded-xl border border-border/50 bg-background/30 text-foreground-secondary transition-all duration-200 hover:border-border-hover hover:text-foreground hover:bg-card hover:shadow-sm";

  const closeMenus = () => {
    setMobileOpen(false);
    setProfileOpen(false);
  };

  /* ── profile dropdown items ───────────────────────────── */
  const profileLinks = [
    { href: "/dashboard", label: t("nav.dashboard"), icon: LayoutDashboard },
    { href: "/projects", label: t("nav.projects"), icon: FolderKanban },
    { href: "/settings", label: t("nav.settings"), icon: Settings },
    ...(user?.role === "ADMIN"
      ? [{ href: "/admin", label: t("nav.admin"), icon: ShieldCheck }]
      : []),
  ];

  return (
    <motion.nav
      initial={{ y: -90 }}
      animate={{ y: 0 }}
      transition={{ type: "spring", stiffness: 110, damping: 18 }}
      className="fixed inset-x-0 top-0 z-50"
    >
      <div className="mx-auto max-w-7xl px-4 py-3 sm:px-6 lg:px-8">
        <div
          className={cn(
            "glass relative rounded-2xl border overflow-visible transition-all duration-500",
            scrolled
              ? "border-nav-border shadow-lg shadow-black/[0.04] backdrop-blur-xl"
              : "border-nav-border/50 backdrop-blur-md"
          )}
        >
          <div className="relative flex h-16 items-center justify-between px-3 sm:px-4">
            {/* ── Logo ──────────────────────────────────── */}
            <Link
              href={isAuthenticated ? "/home" : "/"}
              onClick={closeMenus}
              className="group flex items-center gap-3"
            >
              <motion.div
                whileHover={{ rotate: 8, scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                transition={{ duration: 0.25 }}
                className="relative flex h-10 w-10 items-center justify-center rounded-xl border border-primary/25 bg-primary/10"
              >
                <Zap className="h-5 w-5 text-primary transition-transform group-hover:scale-110" />
              </motion.div>
              <div className="flex items-center gap-2.5">
                <div>
                  <span className="block text-lg font-bold font-display tracking-wide text-foreground">
                    Velocira
                  </span>
                  <span className="hidden text-[9px] uppercase tracking-[0.2em] text-foreground-secondary/60 sm:block">
                    PyraFlow Suite
                  </span>
                </div>
              </div>
            </Link>

            {/* ── Desktop center nav ────────────────────── */}
            <div className="absolute left-1/2 hidden -translate-x-1/2 md:flex items-center rounded-xl border border-border/50 bg-background/35 p-1 gap-0.5">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={closeMenus}
                  className={cn(
                    "relative inline-flex items-center justify-center overflow-hidden rounded-lg px-4 py-2 text-sm font-medium leading-none transition-all duration-200",
                    isActive(link.href)
                      ? "text-on-primary"
                      : "text-foreground-secondary hover:text-foreground"
                  )}
                >
                  {isActive(link.href) && (
                    <motion.div
                      layoutId="nav-active"
                      transition={{
                        type: "spring",
                        stiffness: 380,
                        damping: 30,
                      }}
                      className="absolute inset-0 rounded-lg bg-primary"
                    />
                  )}
                  <span className="relative z-10">{link.label}</span>
                </Link>
              ))}
            </div>

            {/* ── Desktop right actions ─────────────────── */}
            <div className="hidden md:flex items-center gap-2">
              {/* Theme toggle */}
              <motion.button
                whileTap={{ scale: 0.9, rotate: 180 }}
                onClick={toggleTheme}
                className={iconButtonClass}
                title={t("nav.theme")}
                type="button"
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={theme}
                    initial={{ scale: 0, rotate: -90 }}
                    animate={{ scale: 1, rotate: 0 }}
                    exit={{ scale: 0, rotate: 90 }}
                    transition={{ duration: 0.2 }}
                  >
                    {theme === "dark" ? (
                      <Sun className="h-4 w-4" />
                    ) : (
                      <Moon className="h-4 w-4" />
                    )}
                  </motion.span>
                </AnimatePresence>
              </motion.button>

              {isAuthenticated ? (
                /* ── Profile dropdown ──────────────────── */
                <div ref={profileRef} className="relative">
                  <motion.button
                    onClick={() => setProfileOpen((prev) => !prev)}
                    whileTap={{ scale: 0.97 }}
                    type="button"
                    className="group flex items-center gap-2 rounded-xl border border-border/50 bg-background/30 px-2.5 py-1.5 transition-all duration-200 hover:border-border-hover hover:bg-card hover:shadow-sm"
                  >
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-on-primary">
                      {user?.fullName?.trim().charAt(0).toUpperCase() || "U"}
                    </div>
                    <span className="max-w-[120px] truncate text-sm font-medium text-foreground">
                      {user?.fullName?.split(" ")[0]}
                    </span>
                    <motion.div
                      animate={{ rotate: profileOpen ? 180 : 0 }}
                      transition={{ duration: 0.2 }}
                    >
                      <ChevronDown className="h-3.5 w-3.5 text-foreground-secondary" />
                    </motion.div>
                  </motion.button>

                  <AnimatePresence>
                    {profileOpen && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.97, y: -6 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.97, y: -6 }}
                        className={cn(
                          "absolute top-full mt-2 w-56 rounded-xl border border-border bg-card p-1.5 shadow-sm",
                          "right-0"
                        )}
                      >
                        {profileLinks.map((item) => (
                          <Link
                            key={item.href}
                            href={item.href}
                            onClick={closeMenus}
                            className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-foreground-secondary transition-colors hover:bg-card-hover hover:text-foreground"
                          >
                            <item.icon className="h-4 w-4" />
                            {item.label}
                          </Link>
                        ))}

                        <div className="my-1 h-px bg-border" />

                        <button
                          onClick={() => {
                            setProfileOpen(false);
                            logout();
                          }}
                          type="button"
                          className="flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-error transition-colors hover:bg-card-hover"
                        >
                          <LogOut className="h-4 w-4" />
                          {t("nav.logout")}
                        </button>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              ) : (
                /* ── Public auth buttons ───────────────── */
                <div className="flex items-center gap-2">
                  <Link href="/login">
                    <Button variant="ghost" size="sm">
                      {t("nav.login")}
                    </Button>
                  </Link>
                  <Link href="/register">
                    <Button size="sm">{t("nav.register")}</Button>
                  </Link>
                </div>
              )}
            </div>

            {/* ── Mobile hamburger ──────────────────────── */}
            <button
              onClick={() => setMobileOpen((prev) => !prev)}
              className={cn(iconButtonClass, "md:hidden")}
              type="button"
              aria-label={mobileOpen ? "Close menu" : "Open menu"}
            >
              {mobileOpen ? (
                <X className="h-5 w-5" />
              ) : (
                <Menu className="h-5 w-5" />
              )}
            </button>
          </div>
        </div>
      </div>

      {/* ── Mobile menu ───────────────────────────────────── */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden md:hidden"
          >
            <div className="mx-auto -mt-1 max-w-7xl px-4 sm:px-6 lg:px-8">
              <div className="glass rounded-2xl border border-nav-border p-3 shadow-sm">
                {/* Nav links */}
                <div className="space-y-1">
                  {navLinks.map((link) => (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={closeMenus}
                      className={cn(
                        "block rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                        isActive(link.href)
                          ? "bg-primary text-on-primary"
                          : "text-foreground-secondary hover:bg-card hover:text-foreground"
                      )}
                    >
                      {link.label}
                    </Link>
                  ))}

                  {/* Extra authenticated links in mobile */}
                  {isAuthenticated && (
                    <>
                      <div className="my-2 h-px bg-border" />
                      <Link
                        href="/settings"
                        onClick={closeMenus}
                        className={cn(
                          "flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                          isActive("/settings")
                            ? "bg-primary text-on-primary"
                            : "text-foreground-secondary hover:bg-card hover:text-foreground"
                        )}
                      >
                        <Settings className="h-4 w-4" />
                        {t("nav.settings")}
                      </Link>
                      {user?.role === "ADMIN" && (
                        <Link
                          href="/admin"
                          onClick={closeMenus}
                          className={cn(
                            "flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                            isActive("/admin")
                              ? "bg-primary text-on-primary"
                              : "text-foreground-secondary hover:bg-card hover:text-foreground"
                          )}
                        >
                          <ShieldCheck className="h-4 w-4" />
                          {t("nav.admin")}
                        </Link>
                      )}
                    </>
                  )}
                </div>

                <div className="my-3 h-px bg-border" />

                {/* Theme toggle */}
                <div className="flex items-center gap-2">
                  <button
                    onClick={toggleTheme}
                    className={iconButtonClass}
                    type="button"
                    aria-label="Toggle theme"
                  >
                    {theme === "dark" ? (
                      <Sun className="h-4 w-4" />
                    ) : (
                      <Moon className="h-4 w-4" />
                    )}
                  </button>
                </div>

                {/* Auth actions */}
                {isAuthenticated ? (
                  <button
                    onClick={() => {
                      setMobileOpen(false);
                      logout();
                    }}
                    type="button"
                    className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border border-error/35 bg-error/10 px-4 py-2.5 text-sm font-medium text-error transition-colors hover:bg-error/15"
                  >
                    <LogOut className="h-4 w-4" />
                    {t("nav.logout")}
                  </button>
                ) : (
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <Link href="/login">
                      <Button variant="outline" size="sm" className="w-full">
                        {t("nav.login")}
                      </Button>
                    </Link>
                    <Link href="/register">
                      <Button size="sm" className="w-full">
                        {t("nav.register")}
                      </Button>
                    </Link>
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.nav>
  );
}
