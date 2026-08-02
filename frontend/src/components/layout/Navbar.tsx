"use client";

import { useState, useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
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
import VelociraLogo from "@/components/branding/VelociraLogo";

export default function Navbar() {
  const { t } = useLocale();
  const { theme, toggleTheme } = useTheme();
  const { isAuthenticated, user } = useAuthStore();
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement | null>(null);
  const profileTriggerRef = useRef<HTMLButtonElement | null>(null);
  const mobilePanelRef = useRef<HTMLDivElement | null>(null);
  const lastMobileFocusRef = useRef<HTMLElement | null>(null);

  /* ── scroll detection ─────────────────────────────────── */
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!mobileOpen) return;
    lastMobileFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const frame = window.requestAnimationFrame(() => {
      mobilePanelRef.current?.querySelector<HTMLElement>("a[href], button:not([disabled])")?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      lastMobileFocusRef.current?.focus();
    };
  }, [mobileOpen]);

  /* ── outside-click handler for profile dropdown ───────── */
  useEffect(() => {
    if (!profileOpen) return;
    const onOutsideClick = (event: MouseEvent) => {
      if (!profileRef.current) return;
      if (!profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      setProfileOpen(false);
      window.requestAnimationFrame(() => profileTriggerRef.current?.focus());
    };
    document.addEventListener("mousedown", onOutsideClick);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onOutsideClick);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [profileOpen]);

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
    "flex h-9 w-9 items-center justify-center rounded-md border border-border bg-transparent text-foreground-secondary transition-colors duration-150 hover:border-accent hover:bg-accent-light hover:text-accent";

  const closeMenus = () => {
    setMobileOpen(false);
    setProfileOpen(false);
  };

  const handleMobileMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      setMobileOpen(false);
      return;
    }
    if (event.key !== "Tab") return;
    const panel = mobilePanelRef.current;
    if (!panel) return;
    const focusable = Array.from(panel.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), [tabindex]:not([tabindex='-1'])"));
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || !panel.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
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
      className="fixed inset-x-0 top-0 z-50 border-b border-nav-border bg-utility"
    >
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div
          className={cn(
            "relative overflow-visible transition-colors duration-150",
            scrolled
              ? "border-nav-border"
              : "border-transparent"
          )}
        >
          <div className="relative flex h-16 items-center justify-between px-3 sm:px-4">
            {/* ── Logo ──────────────────────────────────── */}
            <Link
              href={isAuthenticated ? "/home" : "/"}
              onClick={closeMenus}
              className="group flex items-center gap-3"
            >
              <span className="relative flex h-9 w-9 items-center justify-center rounded-md border border-border bg-background-secondary">
                <VelociraLogo size={29} priority />
              </span>
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
            <div className="absolute left-1/2 hidden -translate-x-1/2 md:flex items-center gap-0.5 border-l border-border-subtle pl-4">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={closeMenus}
                  aria-current={isActive(link.href) ? "page" : undefined}
                  className={cn(
                    "relative inline-flex items-center justify-center overflow-hidden rounded-lg px-4 py-2 text-sm font-medium leading-none transition-all duration-200",
                    isActive(link.href)
                      ? "text-accent"
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
                      className="absolute inset-0 rounded-sm border border-accent/35 bg-accent-light"
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
                aria-label={t("nav.theme")}
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
                    ref={profileTriggerRef}
                    onClick={() => setProfileOpen((prev) => !prev)}
                    whileTap={{ scale: 0.97 }}
                    type="button"
                    aria-expanded={profileOpen}
                    aria-haspopup="menu"
                    aria-controls="navbar-profile-menu"
                    aria-label="Open account menu"
                    className="group flex items-center gap-2 rounded-md border border-border bg-transparent px-2.5 py-1.5 transition-colors duration-150 hover:border-accent hover:bg-accent-light"
                  >
                    <div className="flex h-8 w-8 items-center justify-center rounded-sm bg-accent text-sm font-bold text-on-primary">
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
                        id="navbar-profile-menu"
                        role="menu"
                        aria-label="Account menu"
                        initial={{ opacity: 0, scale: 0.97, y: -6 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.97, y: -6 }}
                        className={cn(
                          "absolute top-full mt-2 w-56 rounded-md border border-border bg-card p-1.5",
                          "right-0"
                        )}
                      >
                        {profileLinks.map((item) => (
                          <Link
                            key={item.href}
                            href={item.href}
                            onClick={closeMenus}
                            role="menuitem"
                            className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-foreground-secondary transition-colors hover:bg-card-hover hover:text-foreground"
                          >
                            <item.icon className="h-4 w-4" />
                            {item.label}
                          </Link>
                        ))}

                        <div className="my-1 h-px bg-border" />

                        <Link
                          href="/logout"
                          onClick={closeMenus}
                          role="menuitem"
                          className="flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-error transition-colors hover:bg-card-hover"
                        >
                          <LogOut className="h-4 w-4" />
                          {t("nav.logout")}
                        </Link>
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
              aria-expanded={mobileOpen}
              aria-controls="site-mobile-navigation"
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
              <div ref={mobilePanelRef} id="site-mobile-navigation" role="navigation" aria-label="Primary navigation" tabIndex={-1} onKeyDown={handleMobileMenuKeyDown} className="border border-nav-border bg-utility p-3">
                {/* Nav links */}
                <div className="space-y-1">
                  {navLinks.map((link) => (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={closeMenus}
                      aria-current={isActive(link.href) ? "page" : undefined}
                      className={cn(
                        "block rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                        isActive(link.href)
                        ? "bg-accent-light text-accent"
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
                        aria-current={isActive("/settings") ? "page" : undefined}
                        className={cn(
                          "flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                          isActive("/settings")
                            ? "bg-accent-light text-accent"
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
                          aria-current={isActive("/admin") ? "page" : undefined}
                          className={cn(
                            "flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors",
                            isActive("/admin")
                            ? "bg-accent-light text-accent"
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
                  <Link
                    href="/logout"
                    onClick={closeMenus}
                    className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border border-error/35 bg-error/10 px-4 py-2.5 text-sm font-medium text-error transition-colors hover:bg-error/15"
                  >
                    <LogOut className="h-4 w-4" />
                    {t("nav.logout")}
                  </Link>
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
