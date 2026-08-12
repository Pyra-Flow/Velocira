"use client";

import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  FolderKanban,
  Command,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Settings,
  ShieldCheck,
  Sun,
  X,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useTheme } from "@/providers/ThemeProvider";
import { cn } from "@/lib/utils";
import VelociraLogo from "@/components/branding/VelociraLogo";
import Tooltip from "@/components/ui/Tooltip";

const navigation = [
  { href: "/dashboard", label: "Overview", icon: LayoutDashboard },
  { href: "/projects", label: "Projects", icon: FolderKanban },
];

function workspaceContext(pathname: string) {
  if (pathname === "/dashboard") return "Workspace / Overview";
  if (pathname === "/projects/new") return "Projects / New project";
  if (/^\/projects\/[^/]+/.test(pathname)) return "Projects / Project workspace";
  if (pathname.startsWith("/projects")) return "Projects / Library";
  if (pathname.startsWith("/settings")) return "Workspace / Settings";
  if (pathname.startsWith("/admin/users")) return "Admin / Users";
  if (pathname.startsWith("/admin")) return "Admin / Operations";
  return "Velocira / Workspace";
}

type Props = {
  onOpenCommandPalette?: () => void;
};

export default function WorkspaceHeader({ onOpenCommandPalette }: Props) {
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { theme, toggleTheme } = useTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const mobilePanelRef = useRef<HTMLDivElement>(null);
  const lastFocusedElementRef = useRef<HTMLElement | null>(null);
  const links = user?.role === "ADMIN"
    ? [...navigation, { href: "/admin", label: "Admin", icon: ShieldCheck }]
    : navigation;

  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);
  const closeMenu = () => setMenuOpen(false);
  const openMenu = () => {
    lastFocusedElementRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setMenuOpen(true);
  };

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeMenu();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [menuOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const panel = mobilePanelRef.current;
    const frame = window.requestAnimationFrame(() => panel?.focus());
    return () => {
      window.cancelAnimationFrame(frame);
      lastFocusedElementRef.current?.focus();
    };
  }, [menuOpen]);

  const handleMobileMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "Tab") return;
    const panel = mobilePanelRef.current;
    if (!panel) return;
    const focusable = Array.from(panel.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), [tabindex]:not([tabindex='-1'])"));
    if (focusable.length === 0) { event.preventDefault(); panel.focus(); return; }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || document.activeElement === panel || !panel.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
    if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  };

  const navigationLinks = (mobile = false) => (
    <nav className={mobile ? "workspace-header__mobile-nav" : "workspace-header__nav"} aria-label="Workspace navigation">
      {links.map((link) => {
        const Icon = link.icon;
        return (
          <Link key={link.href} href={link.href} onClick={closeMenu} aria-current={isActive(link.href) ? "page" : undefined} className={cn(mobile ? "workspace-header__mobile-link" : "workspace-header__nav-link", isActive(link.href) && "is-active")}>
            <Icon aria-hidden="true" />
            <span>{link.label}</span>
          </Link>
        );
      })}
    </nav>
  );

  return (
    <>
      <aside className="workspace-sidebar" aria-label="Velocira workspace">
        <Link href="/dashboard" className="workspace-header__brand" onClick={closeMenu}>
          <span className="workspace-header__brand-mark"><VelociraLogo size={31} priority /></span>
          <span>Velocira</span>
        </Link>

        <div className="workspace-sidebar__label">Workspace</div>
        {navigationLinks()}

        <div className="workspace-sidebar__footer">
          <Link href="/settings" aria-current={isActive("/settings") ? "page" : undefined} className={cn("workspace-header__nav-link", isActive("/settings") && "is-active")}>
            <Settings aria-hidden="true" />
            <span>Settings</span>
          </Link>
          <div className="workspace-sidebar__account">
            <span aria-hidden="true">{user?.fullName?.trim().charAt(0).toUpperCase() || "U"}</span>
            <div>
              <strong>{user?.fullName || "Account"}</strong>
              <small>{user?.role === "ADMIN" ? "Administrator" : "Workspace member"}</small>
            </div>
            <Link href="/logout" className="workspace-sidebar__signout" aria-label="Sign out" title="Sign out"><LogOut aria-hidden="true" /></Link>
          </div>
        </div>
      </aside>

      <header className="workspace-header">
        <div className="workspace-header__inner">
          <p className="workspace-header__context">{workspaceContext(pathname)}</p>
          <div className="workspace-header__actions">
            {onOpenCommandPalette && <Tooltip label="Quick actions · Ctrl+K"><button type="button" className="workspace-header__icon-action workspace-header__command" onClick={onOpenCommandPalette} aria-label="Open quick actions"><Command aria-hidden="true" /></button></Tooltip>}
            <Tooltip label={theme === "dark" ? "Use light theme" : "Use dark theme"}><button type="button" className="workspace-header__icon-action" onClick={toggleTheme} aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}>
              {theme === "dark" ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
            </button></Tooltip>
            <button type="button" className="workspace-header__menu-toggle" aria-expanded={menuOpen} aria-controls="workspace-mobile-navigation" aria-label={menuOpen ? "Close navigation" : "Open navigation"} onClick={() => menuOpen ? closeMenu() : openMenu()}>
              {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
            </button>
          </div>
        </div>
      </header>

      {menuOpen && (
        <div className="workspace-mobile-menu" id="workspace-mobile-navigation">
          <button type="button" className="workspace-mobile-menu__scrim" aria-label="Close navigation" onClick={closeMenu} />
          <div ref={mobilePanelRef} tabIndex={-1} onKeyDown={handleMobileMenuKeyDown} className="workspace-mobile-menu__panel" role="dialog" aria-modal="true" aria-label="Workspace navigation">
            <div className="workspace-mobile-menu__brand">
              <span className="workspace-header__brand-mark"><VelociraLogo size={28} priority /></span>
              <span>Velocira</span>
            </div>
            {navigationLinks(true)}
            <div className="workspace-mobile-menu__divider" />
            <Link href="/settings" onClick={closeMenu} aria-current={isActive("/settings") ? "page" : undefined} className={cn("workspace-header__mobile-link", isActive("/settings") && "is-active")}><Settings aria-hidden="true" />Settings</Link>
            <button type="button" onClick={toggleTheme} className="workspace-header__mobile-link"><span>{theme === "dark" ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}</span>{theme === "dark" ? "Use light theme" : "Use dark theme"}</button>
            <Link href="/logout" onClick={closeMenu} className="workspace-header__mobile-link workspace-header__mobile-link--logout"><LogOut aria-hidden="true" />Sign out</Link>
          </div>
        </div>
      )}
    </>
  );
}
