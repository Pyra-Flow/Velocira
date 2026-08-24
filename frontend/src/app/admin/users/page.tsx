"use client";

import { useState, useEffect, useCallback, useRef, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Search,
  ArrowLeft,
  Eye,
  Ban,
  Trash2,
  ChevronLeft,
  ChevronRight,
  X,
  Shield,
  Mail,
  Calendar,
  FolderKanban,
  Clock,
  AlertTriangle,
  Circle,
  UserCheck,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import {
  adminApi,
  type AdminUserResponse,
} from "@/lib/api";
import { formatRelativeTime, getInitials } from "@/lib/utils";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  PageTransition,
  useSignalMotion,
} from "@/components/ui/Animations";

type FilterTab = "all" | "active" | "suspended";

type UserActionControlsProps = {
  user: AdminUserResponse;
  actionLoading: string | null;
  onView: (user: AdminUserResponse) => void;
  onToggleStatus: (id: string, currentlyActive: boolean) => void;
  onDelete: (user: AdminUserResponse) => void;
  showLabels?: boolean;
};

function UserStatus({ active }: { active: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded border px-2 py-1 font-mono text-[10px] font-medium uppercase tracking-[0.08em] ${
        active
          ? "border-success/30 bg-success/10 text-success"
          : "border-error/30 bg-error/10 text-error"
      }`}
      role="status"
    >
      <Circle className="h-1.5 w-1.5 fill-current" aria-hidden="true" />
      {active ? "Active" : "Suspended"}
    </span>
  );
}

function UserRoleControl({
  user,
  actionLoading,
  onChangeRole,
}: {
  user: AdminUserResponse;
  actionLoading: string | null;
  onChangeRole: (id: string, role: "USER" | "ADMIN") => void;
}) {
  return (
    <button
      type="button"
      onClick={() =>
        onChangeRole(user.id, user.role === "ADMIN" ? "USER" : "ADMIN")
      }
      disabled={actionLoading === user.id}
      className={`inline-flex items-center gap-1 rounded border px-2 py-1 font-mono text-[10px] font-medium uppercase tracking-[0.08em] transition-opacity hover:opacity-80 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-60 ${
        user.role === "ADMIN"
          ? "border-info/30 bg-info/10 text-info"
          : "border-accent/30 bg-accent-light text-accent"
      }`}
      title="Click to change role"
      aria-label={`Change ${user.fullName}'s role from ${user.role}`}
    >
      {user.role === "ADMIN" && <Shield className="h-3 w-3" aria-hidden="true" />}
      {user.role}
    </button>
  );
}

function UserActionControls({
  user,
  actionLoading,
  onView,
  onToggleStatus,
  onDelete,
  showLabels = false,
}: UserActionControlsProps) {
  const iconButton =
    "inline-flex min-h-9 items-center justify-center gap-1.5 rounded-md border border-transparent px-2 text-foreground-secondary transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-60";

  return (
    <div
      className={`flex items-center ${showLabels ? "justify-start gap-2" : "justify-end gap-1"}`}
    >
      <button
        type="button"
        onClick={() => onView(user)}
        className={`${iconButton} hover:border-accent/30 hover:bg-accent-light hover:text-accent`}
        title="View user"
        aria-label={`View ${user.fullName}`}
      >
        <Eye className="h-4 w-4" aria-hidden="true" />
        {showLabels && <span className="text-xs font-medium">View</span>}
      </button>
      <button
        type="button"
        onClick={() => onToggleStatus(user.id, user.active)}
        disabled={actionLoading === user.id}
        className={`${iconButton} ${
          user.active
            ? "hover:border-warning/30 hover:bg-warning/10 hover:text-warning"
            : "hover:border-success/30 hover:bg-success/10 hover:text-success"
        }`}
        title={user.active ? "Suspend user" : "Activate user"}
        aria-label={`${user.active ? "Suspend" : "Activate"} ${user.fullName}`}
      >
        {actionLoading === user.id ? (
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        ) : user.active ? (
          <Ban className="h-4 w-4" aria-hidden="true" />
        ) : (
          <UserCheck className="h-4 w-4" aria-hidden="true" />
        )}
        {showLabels && (
          <span className="text-xs font-medium">
            {user.active ? "Suspend" : "Activate"}
          </span>
        )}
      </button>
      <button
        type="button"
        onClick={() => onDelete(user)}
        className={`${iconButton} hover:border-error/30 hover:bg-error/10 hover:text-error`}
        title="Delete user"
        aria-label={`Delete ${user.fullName}`}
      >
        <Trash2 className="h-4 w-4" aria-hidden="true" />
        {showLabels && <span className="text-xs font-medium">Delete</span>}
      </button>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function AdminUsersPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const { shouldReduceMotion, transition: signalTransition } = useSignalMotion();

  /* ---- State ---- */
  const [users, setUsers] = useState<AdminUserResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [isFirst, setIsFirst] = useState(true);
  const [isLast, setIsLast] = useState(true);
  const PAGE_SIZE = 10;

  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<FilterTab>("all");
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [viewUser, setViewUser] = useState<AdminUserResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminUserResponse | null>(null);
  const viewDialogRef = useRef<HTMLDivElement>(null);
  const deleteDialogRef = useRef<HTMLDivElement>(null);
  const lastModalFocusRef = useRef<HTMLElement | null>(null);

  /* ---- Auth guard ---- */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  /* ---- Fetch users ---- */
  const fetchUsers = useCallback(
    async (page = 0) => {
      setLoadingUsers(true);
      try {
        const res = await adminApi.listUsers({
          page,
          size: PAGE_SIZE,
          search: search.trim() || undefined,
        });
        if (res.success && res.data) {
          setUsers(res.data.content);
          setTotalElements(res.data.totalElements);
          setTotalPages(res.data.totalPages);
          setCurrentPage(res.data.number);
          setIsFirst(res.data.first);
          setIsLast(res.data.last);
        }
      } catch {
        /* silently fail */
      } finally {
        setLoadingUsers(false);
      }
    },
    [search]
  );

  /* ---- Debounced search ---- */
  useEffect(() => {
    const timer = setTimeout(() => {
      if (isAuthenticated && user?.role === "ADMIN") {
        fetchUsers(0);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [isAuthenticated, user?.role, fetchUsers]);

  const activeDialog = viewUser ? "details" : deleteTarget ? "delete" : null;

  /* ---- Modal keyboard dismissal and focus containment ---- */
  useEffect(() => {
    if (!activeDialog) return;
    lastModalFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const frame = window.requestAnimationFrame(() => {
      const dialog = activeDialog === "details" ? viewDialogRef.current : deleteDialogRef.current;
      dialog?.querySelector<HTMLElement>("[data-dialog-autofocus], button:not([disabled]), [href], input:not([disabled])")?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      lastModalFocusRef.current?.focus();
    };
  }, [activeDialog]);

  const handleDialogKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      if (deleteTarget) setDeleteTarget(null);
      else setViewUser(null);
      return;
    }
    if (event.key !== "Tab") return;
    const dialog = event.currentTarget;
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])"));
    if (focusable.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  /* ---- Filtered (client-side for active/suspended toggle) ---- */
  const filtered =
    filter === "all"
      ? users
      : filter === "active"
        ? users.filter((u) => u.active)
        : users.filter((u) => !u.active);

  /* ---- Actions ---- */
  const toggleSuspend = async (id: string, currentlyActive: boolean) => {
    setActionLoading(id);
    try {
      const res = currentlyActive
        ? await adminApi.suspendUser(id)
        : await adminApi.activateUser(id);
      if (res.success) {
        setUsers((prev) =>
          prev.map((u) =>
            u.id === id ? { ...u, active: !currentlyActive } : u
          )
        );
        if (viewUser?.id === id) {
          setViewUser({ ...viewUser, active: !currentlyActive });
        }
      }
    } catch {
      /* silently fail */
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteUser = async (id: string) => {
    setActionLoading(id);
    try {
      const res = await adminApi.deleteUser(id);
      if (res.success) {
        setUsers((prev) => prev.filter((u) => u.id !== id));
        setTotalElements((prev) => prev - 1);
        setDeleteTarget(null);
      }
    } catch {
      /* silently fail */
    } finally {
      setActionLoading(null);
    }
  };

  const changeRole = async (id: string, role: "USER" | "ADMIN") => {
    setActionLoading(id);
    try {
      const res = await adminApi.changeRole(id, role);
      if (res.success) {
        setUsers((prev) =>
          prev.map((u) => (u.id === id ? { ...u, role } : u))
        );
        if (viewUser?.id === id) {
          setViewUser({ ...viewUser, role });
        }
      }
    } catch {
      /* silently fail */
    } finally {
      setActionLoading(null);
    }
  };

  const toggleSelect = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selected.size === filtered.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(filtered.map((u) => u.id)));
    }
  };

  const suspendSelected = async () => {
    for (const id of selected) {
      const u = users.find((usr) => usr.id === id);
      if (u?.active) {
        await adminApi.suspendUser(id);
      }
    }
    setSelected(new Set());
    fetchUsers(currentPage);
  };

  /* ---- Loading / guards ---- */
  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background-secondary">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  if (user?.role !== "ADMIN") {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-background-secondary">
        <ShieldCheck className="h-16 w-16 text-error" />
        <h1 className="text-2xl font-display font-bold text-foreground">
          Access Denied
        </h1>
        <p className="text-foreground-secondary">
          You do not have permission to view this page.
        </p>
        <Link href="/">
          <Button variant="outline">Back to Home</Button>
        </Link>
      </div>
    );
  }

  const activeCount = users.filter((u) => u.active).length;
  const suspendedCount = users.filter((u) => !u.active).length;

  const filterTabs: { key: FilterTab; label: string; count: number }[] = [
    { key: "all", label: "All", count: users.length },
    { key: "active", label: "Active", count: activeCount },
    { key: "suspended", label: "Suspended", count: suspendedCount },
  ];

  return (
    <PageTransition>
      <section className="workspace-page">
        <div className="workspace-page__inner max-w-7xl">
          {/* ---------- Header ---------- */}
          <FadeIn>
            <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <Link
                  href="/admin"
                  className="flex h-10 w-10 items-center justify-center rounded-md border border-border bg-card text-foreground-secondary transition-colors hover:border-accent/30 hover:text-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                  aria-label="Back to admin dashboard"
                >
                  <ArrowLeft className="h-5 w-5" />
                </Link>
                <div>
                  <p className="workspace-page__eyebrow">System operations</p>
                  <h1 className="workspace-page__title text-3xl">
                    User Management
                  </h1>
                  <p className="text-sm text-foreground-secondary">
                    {totalElements.toLocaleString()} total users
                  </p>
                </div>
              </div>

              <div className="w-full max-w-xs">
                <Input
                  placeholder="Search users..."
                  icon={<Search className="h-4 w-4" />}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>
            </div>
          </FadeIn>

          <FadeIn delay={0.03}>
            <div className="sf-admin-metrics" aria-label="User directory summary">
              {[
                { label: "Directory total", value: totalElements, tone: "neutral" },
                { label: "Shown on this page", value: users.length, tone: "accent" },
                { label: "Active accounts", value: activeCount, tone: "success" },
                { label: "Suspended accounts", value: suspendedCount, tone: "warning" },
              ].map((metric) => <div key={metric.label} data-tone={metric.tone}><p>{metric.label}</p><strong>{metric.value.toLocaleString()}</strong></div>)}
            </div>
          </FadeIn>

          {/* ---------- Filters + Bulk Actions ---------- */}
          <FadeIn delay={0.05}>
            <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex flex-wrap gap-2" aria-label="User filters">
                {filterTabs.map((tab) => (
                  <button
                    type="button"
                    key={tab.key}
                    onClick={() => setFilter(tab.key)}
                    aria-pressed={filter === tab.key}
                    className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-2 font-mono text-[11px] font-medium uppercase tracking-[0.08em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${
                      filter === tab.key
                        ? "border-accent/30 bg-accent-light text-accent"
                        : "border-border text-foreground-secondary hover:border-border-hover hover:text-foreground"
                    }`}
                  >
                    {tab.label}
                    <span
                      className={`rounded px-1.5 py-0.5 font-mono text-[10px] ${
                        filter === tab.key
                          ? "bg-accent/20 text-accent"
                          : "bg-background-secondary text-foreground-secondary"
                      }`}
                    >
                      {tab.count}
                    </span>
                  </button>
                ))}
              </div>

              <AnimatePresence>
                {selected.size > 0 && (
                  <motion.div
                    initial={shouldReduceMotion ? false : { opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }}
                    transition={signalTransition}
                    className="flex w-full flex-col gap-3 rounded-md border border-accent/25 bg-accent-light px-3 py-2.5 sm:w-auto sm:flex-row sm:items-center"
                    aria-live="polite"
                    aria-atomic="true"
                  >
                    <div className="min-w-0">
                      <p className="font-mono text-[10px] font-medium uppercase tracking-[0.12em] text-accent">
                        Selection queue
                      </p>
                      <p className="text-sm text-foreground">
                        <span className="font-mono font-semibold text-accent">
                          {selected.size.toLocaleString()}
                        </span>{" "}
                        {selected.size === 1 ? "account selected" : "accounts selected"}
                      </p>
                    </div>
                    <p className="text-xs text-foreground-secondary sm:max-w-44">
                      Suspension applies to active accounts only.
                    </p>
                    <button
                      type="button"
                      onClick={() => setSelected(new Set())}
                      className="self-start rounded px-1 py-1 font-mono text-[10px] font-medium uppercase tracking-[0.08em] text-foreground-secondary transition-colors hover:text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:self-auto"
                    >
                      Clear
                    </button>
                    <Button
                      variant="danger"
                      size="sm"
                      icon={<Ban className="h-4 w-4" />}
                      onClick={suspendSelected}
                    >
                      Suspend Selected
                    </Button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </FadeIn>

          {/* ---------- Users Table ---------- */}
          <FadeIn delay={0.1}>
            <Card className="overflow-hidden p-0">
              {loadingUsers ? (
                <div className="flex flex-col items-center justify-center gap-3 py-20" role="status">
                  <Loader2 className="h-6 w-6 animate-spin text-accent" aria-hidden="true" />
                  <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-foreground-secondary">
                    Loading user records
                  </span>
                </div>
              ) : (
                <>
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full text-sm" aria-label="User records">
                      <thead>
                        <tr className="border-b border-border bg-background-secondary text-left font-mono text-[10px] font-medium uppercase tracking-[0.1em] text-foreground-secondary">
                          <th className="w-10 p-3" scope="col">
                          <input
                            type="checkbox"
                            checked={
                              filtered.length > 0 &&
                              selected.size === filtered.length
                            }
                            onChange={toggleSelectAll}
                            className="h-4 w-4 rounded-sm border-border accent-accent cursor-pointer"
                            aria-label="Select all filtered users"
                          />
                          </th>
                          <th className="p-3" scope="col">User</th>
                          <th className="p-3" scope="col">Email</th>
                          <th className="p-3" scope="col">Role</th>
                          <th className="p-3" scope="col">Status</th>
                          <th className="p-3 text-center" scope="col">Projects</th>
                          <th className="p-3" scope="col">Joined</th>
                          <th className="p-3 text-right" scope="col">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {filtered.map((u) => (
                          <motion.tr
                            key={u.id}
                            layout
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            exit={{ opacity: 0 }}
                            className={`group transition-colors hover:bg-background-secondary ${
                              selected.has(u.id) ? "bg-accent/[0.045]" : ""
                            }`}
                          >
                            <td className="p-3 align-middle">
                            <input
                              type="checkbox"
                              checked={selected.has(u.id)}
                              onChange={() => toggleSelect(u.id)}
                              className="h-4 w-4 rounded-sm border-border accent-accent cursor-pointer"
                              aria-label={`Select ${u.fullName}`}
                            />
                            </td>

                            <td className="p-3 align-middle">
                            <div className="flex items-center gap-3">
                              <div
                                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded text-xs font-semibold ${
                                  u.role === "ADMIN"
                                    ? "bg-info/10 text-info"
                                    : "bg-accent-light text-accent"
                                }`}
                              >
                                {getInitials(u.fullName)}
                              </div>
                              <div className="min-w-0">
                                <span className="block truncate font-medium text-foreground">
                                  {u.fullName}
                                </span>
                                <span className="block max-w-36 truncate font-mono text-[10px] text-foreground-secondary" title={u.id}>
                                  {u.id}
                                </span>
                              </div>
                            </div>
                            </td>

                            <td className="p-3 align-middle text-foreground-secondary">
                              <span className="block max-w-48 truncate" title={u.email}>
                                {u.email}
                              </span>
                            </td>

                            <td className="p-3 align-middle">
                              <UserRoleControl
                                user={u}
                                actionLoading={actionLoading}
                                onChangeRole={changeRole}
                              />
                            </td>

                            <td className="p-3 align-middle">
                              <UserStatus active={u.active} />
                            </td>

                            <td className="p-3 text-center align-middle font-mono text-xs text-foreground">
                              {u.projectCount}
                            </td>

                            <td className="p-3 align-middle">
                              <time
                                dateTime={u.createdAt}
                                title={u.createdAt}
                                className="whitespace-nowrap font-mono text-[11px] text-foreground-secondary"
                              >
                                {formatRelativeTime(u.createdAt)}
                              </time>
                            </td>

                            <td className="p-3 align-middle">
                              <UserActionControls
                                user={u}
                                actionLoading={actionLoading}
                                onView={setViewUser}
                                onToggleStatus={toggleSuspend}
                                onDelete={setDeleteTarget}
                              />
                            </td>
                          </motion.tr>
                        ))}

                        {filtered.length === 0 && (
                          <tr>
                            <td
                              colSpan={8}
                              className="py-16 text-center font-mono text-[11px] uppercase tracking-[0.12em] text-foreground-secondary"
                            >
                              No users match this view.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>

                  <div className="divide-y divide-border md:hidden">
                    {filtered.map((u) => (
                      <motion.article
                        key={u.id}
                        layout
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className={`p-4 transition-colors ${
                          selected.has(u.id) ? "bg-accent/[0.045]" : ""
                        }`}
                        aria-label={`${u.fullName} user record`}
                      >
                        <div className="flex items-start gap-3">
                          <input
                            type="checkbox"
                            checked={selected.has(u.id)}
                            onChange={() => toggleSelect(u.id)}
                            className="mt-2 h-4 w-4 shrink-0 rounded-sm border-border accent-accent cursor-pointer"
                            aria-label={`Select ${u.fullName}`}
                          />
                          <div
                            className={`flex h-10 w-10 shrink-0 items-center justify-center rounded text-sm font-semibold ${
                              u.role === "ADMIN"
                                ? "bg-info/10 text-info"
                                : "bg-accent-light text-accent"
                            }`}
                          >
                            {getInitials(u.fullName)}
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <p className="truncate font-medium text-foreground">{u.fullName}</p>
                              <UserStatus active={u.active} />
                            </div>
                            <p className="mt-1 truncate text-sm text-foreground-secondary">{u.email}</p>
                            <p className="mt-1 truncate font-mono text-[10px] text-foreground-secondary" title={u.id}>
                              {u.id}
                            </p>
                          </div>
                        </div>

                        <div className="mt-4 grid grid-cols-3 gap-x-3 gap-y-3 border-y border-border py-3">
                          <div>
                            <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-foreground-secondary">Role</p>
                            <div className="mt-1">
                              <UserRoleControl
                                user={u}
                                actionLoading={actionLoading}
                                onChangeRole={changeRole}
                              />
                            </div>
                          </div>
                          <div>
                            <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-foreground-secondary">Projects</p>
                            <p className="mt-1 font-mono text-sm text-foreground">{u.projectCount}</p>
                          </div>
                          <div>
                            <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-foreground-secondary">Joined</p>
                            <time
                              dateTime={u.createdAt}
                              title={u.createdAt}
                              className="mt-1 block whitespace-nowrap font-mono text-[11px] text-foreground"
                            >
                              {formatRelativeTime(u.createdAt)}
                            </time>
                          </div>
                        </div>

                        <div className="mt-3">
                          <p className="mb-1.5 font-mono text-[10px] uppercase tracking-[0.08em] text-foreground-secondary">
                            Row actions
                          </p>
                          <UserActionControls
                            user={u}
                            actionLoading={actionLoading}
                            onView={setViewUser}
                            onToggleStatus={toggleSuspend}
                            onDelete={setDeleteTarget}
                            showLabels
                          />
                        </div>
                      </motion.article>
                    ))}

                    {filtered.length === 0 && (
                      <div className="px-4 py-16 text-center font-mono text-[11px] uppercase tracking-[0.12em] text-foreground-secondary">
                        No users match this view.
                      </div>
                    )}
                  </div>
                </>
              )}

              {/* Pagination */}
              <div className="flex flex-col gap-3 border-t border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-foreground-secondary">
                  Page{" "}
                  <span className="font-mono font-medium text-foreground">
                    {currentPage + 1}
                  </span>{" "}
                  of{" "}
                  <span className="font-mono font-medium text-foreground">
                    {totalPages || 1}
                  </span>{" "}
                  ({totalElements.toLocaleString()} users)
                </p>
                <div className="flex items-center gap-2 self-end sm:self-auto">
                  <Button
                    variant="outline"
                    size="sm"
                    icon={<ChevronLeft className="h-4 w-4" />}
                    disabled={isFirst}
                    onClick={() => fetchUsers(currentPage - 1)}
                  >
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    icon={<ChevronRight className="h-4 w-4" />}
                    disabled={isLast}
                    onClick={() => fetchUsers(currentPage + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            </Card>
          </FadeIn>
        </div>

        {/* ================================================================ */}
        {/*  User Detail Modal                                               */}
        {/* ================================================================ */}
        <AnimatePresence>
          {viewUser && (
            <motion.div
              className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setViewUser(null)}
            >
              <motion.div
                ref={viewDialogRef}
                className="w-full max-w-lg overflow-hidden rounded-lg border border-border bg-card"
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                onClick={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="user-detail-title"
                tabIndex={-1}
                onKeyDown={handleDialogKeyDown}
              >
                {/* Modal Header */}
                <div className="flex items-start justify-between border-b border-border p-6">
                  <div className="flex items-center gap-4">
                    <div
                      className={`flex h-14 w-14 items-center justify-center rounded text-lg font-bold ${
                        viewUser.role === "ADMIN"
                          ? "bg-info/10 text-info"
                          : "bg-accent-light text-accent"
                      }`}
                    >
                      {getInitials(viewUser.fullName)}
                    </div>
                    <div>
                      <h2 id="user-detail-title" className="text-xl font-display font-bold text-foreground">
                        {viewUser.fullName}
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        {viewUser.email}
                      </p>
                    </div>
                  </div>
                  <button
                    data-dialog-autofocus
                    onClick={() => setViewUser(null)}
                    className="rounded-md p-2 text-foreground-secondary transition-colors cursor-pointer hover:bg-background-secondary hover:text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                    aria-label="Close user details"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                {/* Modal Body */}
                <div className="max-h-[60vh] overflow-y-auto p-6 space-y-6">
                  {/* Info Grid */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <Shield className="h-3 w-3" /> Role
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.role}
                      </p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <Circle className="h-3 w-3" /> Status
                      </p>
                      <span
                        className={`inline-flex items-center gap-1.5 rounded border px-2.5 py-0.5 text-xs font-medium ${
                          viewUser.active
                            ? "border-success/30 bg-success/10 text-success"
                            : "border-error/30 bg-error/10 text-error"
                        }`}
                      >
                        <Circle className="h-1.5 w-1.5 fill-current" />
                        {viewUser.active ? "Active" : "Suspended"}
                      </span>
                    </div>
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <Calendar className="h-3 w-3" /> Joined
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {formatRelativeTime(viewUser.createdAt)}
                      </p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <Clock className="h-3 w-3" /> Last Login
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.lastLoginAt
                          ? formatRelativeTime(viewUser.lastLoginAt)
                          : "Never"}
                      </p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <FolderKanban className="h-3 w-3" /> Projects
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.projectCount}
                      </p>
                    </div>
                    <div className="space-y-1">
                      <p className="text-xs text-foreground-secondary flex items-center gap-1">
                        <Mail className="h-3 w-3" /> Verified
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.emailVerified ? "Yes" : "No"}
                      </p>
                    </div>
                  </div>

                  {viewUser.universityName && (
                    <div className="rounded-md border border-border bg-background-secondary p-3">
                      <p className="text-xs text-foreground-secondary mb-1">
                        University
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.universityName}
                      </p>
                    </div>
                  )}

                  <div className="rounded-md border border-border bg-background-secondary p-3">
                    <p className="text-xs text-foreground-secondary mb-1">
                      Auth Provider
                    </p>
                    <p className="text-sm font-medium text-foreground">
                      {viewUser.authProvider}
                    </p>
                  </div>
                </div>

                {/* Modal Footer */}
                <div className="flex items-center justify-between gap-2 border-t border-border px-6 py-4">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      changeRole(
                        viewUser.id,
                        viewUser.role === "ADMIN" ? "USER" : "ADMIN"
                      )
                    }
                    disabled={actionLoading === viewUser.id}
                    icon={<Shield className="h-4 w-4" />}
                  >
                    {viewUser.role === "ADMIN"
                      ? "Demote to User"
                      : "Promote to Admin"}
                  </Button>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setViewUser(null)}
                    >
                      Close
                    </Button>
                    <Button
                      variant={viewUser.active ? "danger" : "primary"}
                      size="sm"
                      disabled={actionLoading === viewUser.id}
                      icon={
                        actionLoading === viewUser.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : viewUser.active ? (
                          <Ban className="h-4 w-4" />
                        ) : (
                          <UserCheck className="h-4 w-4" />
                        )
                      }
                      onClick={() =>
                        toggleSuspend(viewUser.id, viewUser.active)
                      }
                    >
                      {viewUser.active ? "Suspend" : "Activate"}
                    </Button>
                  </div>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* ================================================================ */}
        {/*  Delete Confirmation Modal                                       */}
        {/* ================================================================ */}
        <AnimatePresence>
          {deleteTarget && (
            <motion.div
              className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setDeleteTarget(null)}
            >
              <motion.div
                ref={deleteDialogRef}
                className="w-full max-w-md rounded-lg border border-border bg-card"
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                onClick={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="delete-user-title"
                tabIndex={-1}
                onKeyDown={handleDialogKeyDown}
              >
                <div className="p-6 text-center">
                  <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded bg-error/10">
                    <AlertTriangle className="h-7 w-7 text-error" />
                  </div>
                  <h2 id="delete-user-title" className="text-xl font-display font-bold text-foreground mb-2">
                    Delete User
                  </h2>
                  <p className="text-sm text-foreground-secondary mb-1">
                    Are you sure you want to delete{" "}
                    <span className="font-semibold text-foreground">
                      {deleteTarget.fullName}
                    </span>
                    ?
                  </p>
                  <p className="text-xs text-foreground-secondary">
                    This action cannot be undone. All user data, projects, and
                    documents will be permanently removed.
                  </p>
                </div>

                <div className="flex items-center justify-end gap-2 border-t border-border px-6 py-4">
                  <Button
                    data-dialog-autofocus
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeleteTarget(null)}
                  >
                    Cancel
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={actionLoading === deleteTarget.id}
                    icon={
                      actionLoading === deleteTarget.id ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Trash2 className="h-4 w-4" />
                      )
                    }
                    onClick={() => handleDeleteUser(deleteTarget.id)}
                  >
                    Delete User
                  </Button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </section>
    </PageTransition>
  );
}
