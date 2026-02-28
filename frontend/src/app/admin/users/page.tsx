"use client";

import { useState, useEffect, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Users,
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
import { useLocale } from "@/providers/LocaleProvider";
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
} from "@/components/ui/Animations";

type FilterTab = "all" | "active" | "suspended";

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function AdminUsersPage() {
  const router = useRouter();
  const { t } = useLocale();
  const { user, isAuthenticated, isLoading } = useAuthStore();

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

  useEffect(() => {
    if (isAuthenticated && user?.role === "ADMIN") {
      fetchUsers(0);
    }
  }, [isAuthenticated, user?.role, fetchUsers]);

  /* ---- Debounced search ---- */
  useEffect(() => {
    const timer = setTimeout(() => {
      if (isAuthenticated && user?.role === "ADMIN") {
        fetchUsers(0);
      }
    }, 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

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
      next.has(id) ? next.delete(id) : next.add(id);
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
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  if (user?.role !== "ADMIN") {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-background-secondary">
        <ShieldCheck className="h-16 w-16 text-red-400" />
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
      <main className="min-h-screen bg-background-secondary">
        <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
          {/* ---------- Header ---------- */}
          <FadeIn>
            <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <Link
                  href="/admin"
                  className="flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-card text-foreground-secondary hover:text-primary hover:border-primary/30 transition-colors"
                >
                  <ArrowLeft className="h-5 w-5" />
                </Link>
                <div>
                  <h1 className="text-3xl font-display font-bold gradient-text">
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

          {/* ---------- Filters + Bulk Actions ---------- */}
          <FadeIn delay={0.05}>
            <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex gap-2">
                {filterTabs.map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setFilter(tab.key)}
                    className={`inline-flex items-center gap-1.5 rounded-xl px-4 py-2 text-sm font-medium transition-colors cursor-pointer ${
                      filter === tab.key
                        ? "bg-primary/10 text-primary border border-primary/20"
                        : "border border-border text-foreground-secondary hover:text-foreground hover:border-border-hover"
                    }`}
                  >
                    {tab.label}
                    <span
                      className={`rounded-full px-1.5 py-0.5 text-xs ${
                        filter === tab.key
                          ? "bg-primary/20 text-primary"
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
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="flex items-center gap-3"
                  >
                    <span className="text-sm text-foreground-secondary">
                      {selected.size} selected
                    </span>
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
                <div className="flex items-center justify-center py-20">
                  <Loader2 className="h-6 w-6 animate-spin text-primary" />
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border text-left text-foreground-secondary bg-background-secondary">
                        <th className="p-4 font-medium w-10">
                          <input
                            type="checkbox"
                            checked={
                              filtered.length > 0 &&
                              selected.size === filtered.length
                            }
                            onChange={toggleSelectAll}
                            className="h-4 w-4 rounded border-border accent-primary cursor-pointer"
                          />
                        </th>
                        <th className="p-4 font-medium">User</th>
                        <th className="p-4 font-medium">Email</th>
                        <th className="p-4 font-medium">Role</th>
                        <th className="p-4 font-medium">Status</th>
                        <th className="p-4 font-medium text-center">Projects</th>
                        <th className="p-4 font-medium">Joined</th>
                        <th className="p-4 font-medium text-right">Actions</th>
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
                          className="group hover:bg-card transition-colors"
                        >
                          {/* Checkbox */}
                          <td className="p-4">
                            <input
                              type="checkbox"
                              checked={selected.has(u.id)}
                              onChange={() => toggleSelect(u.id)}
                              className="h-4 w-4 rounded border-border accent-primary cursor-pointer"
                            />
                          </td>

                          {/* Avatar + Name */}
                          <td className="p-4">
                            <div className="flex items-center gap-3">
                              <div
                                className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-semibold ${
                                  u.role === "ADMIN"
                                    ? "bg-violet-500/15 text-violet-400"
                                    : "bg-primary/10 text-primary"
                                }`}
                              >
                                {getInitials(u.fullName)}
                              </div>
                              <span className="font-medium text-foreground whitespace-nowrap">
                                {u.fullName}
                              </span>
                            </div>
                          </td>

                          {/* Email */}
                          <td className="p-4 text-foreground-secondary whitespace-nowrap">
                            {u.email}
                          </td>

                          {/* Role */}
                          <td className="p-4">
                            <button
                              onClick={() =>
                                changeRole(
                                  u.id,
                                  u.role === "ADMIN" ? "USER" : "ADMIN"
                                )
                              }
                              disabled={actionLoading === u.id}
                              className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium cursor-pointer transition-opacity hover:opacity-80 ${
                                u.role === "ADMIN"
                                  ? "bg-violet-500/10 text-violet-400"
                                  : "bg-blue-500/10 text-blue-400"
                              }`}
                              title="Click to toggle role"
                            >
                              {u.role === "ADMIN" && (
                                <Shield className="h-3 w-3" />
                              )}
                              {u.role}
                            </button>
                          </td>

                          {/* Status */}
                          <td className="p-4">
                            <span
                              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                u.active
                                  ? "bg-emerald-500/10 text-emerald-400"
                                  : "bg-red-500/10 text-red-400"
                              }`}
                            >
                              <Circle className="h-1.5 w-1.5 fill-current" />
                              {u.active ? "Active" : "Suspended"}
                            </span>
                          </td>

                          {/* Projects */}
                          <td className="p-4 text-center text-foreground">
                            {u.projectCount}
                          </td>

                          {/* Joined */}
                          <td className="p-4 text-foreground-secondary whitespace-nowrap">
                            {formatRelativeTime(u.createdAt)}
                          </td>

                          {/* Actions */}
                          <td className="p-4">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                onClick={() => setViewUser(u)}
                                className="rounded-lg p-2 text-foreground-secondary hover:text-primary hover:bg-primary/10 transition-colors cursor-pointer"
                                title="View"
                              >
                                <Eye className="h-4 w-4" />
                              </button>
                              <button
                                onClick={() =>
                                  toggleSuspend(u.id, u.active)
                                }
                                disabled={actionLoading === u.id}
                                className={`rounded-lg p-2 transition-colors cursor-pointer ${
                                  u.active
                                    ? "text-foreground-secondary hover:text-amber-400 hover:bg-amber-500/10"
                                    : "text-foreground-secondary hover:text-emerald-400 hover:bg-emerald-500/10"
                                }`}
                                title={u.active ? "Suspend" : "Activate"}
                              >
                                {actionLoading === u.id ? (
                                  <Loader2 className="h-4 w-4 animate-spin" />
                                ) : u.active ? (
                                  <Ban className="h-4 w-4" />
                                ) : (
                                  <UserCheck className="h-4 w-4" />
                                )}
                              </button>
                              <button
                                onClick={() => setDeleteTarget(u)}
                                className="rounded-lg p-2 text-foreground-secondary hover:text-red-400 hover:bg-red-500/10 transition-colors cursor-pointer"
                                title="Delete"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </td>
                        </motion.tr>
                      ))}

                      {filtered.length === 0 && (
                        <tr>
                          <td
                            colSpan={8}
                            className="py-16 text-center text-foreground-secondary"
                          >
                            No users found.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Pagination */}
              <div className="flex items-center justify-between border-t border-border px-4 py-3">
                <p className="text-sm text-foreground-secondary">
                  Page{" "}
                  <span className="font-medium text-foreground">
                    {currentPage + 1}
                  </span>{" "}
                  of{" "}
                  <span className="font-medium text-foreground">
                    {totalPages || 1}
                  </span>{" "}
                  ({totalElements.toLocaleString()} users)
                </p>
                <div className="flex items-center gap-2">
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
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setViewUser(null)}
            >
              <motion.div
                className="w-full max-w-lg rounded-2xl border border-border bg-card shadow-2xl overflow-hidden"
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                onClick={(e) => e.stopPropagation()}
              >
                {/* Modal Header */}
                <div className="flex items-start justify-between border-b border-border p-6">
                  <div className="flex items-center gap-4">
                    <div
                      className={`flex h-14 w-14 items-center justify-center rounded-full text-lg font-bold ${
                        viewUser.role === "ADMIN"
                          ? "bg-violet-500/15 text-violet-400"
                          : "bg-primary/10 text-primary"
                      }`}
                    >
                      {getInitials(viewUser.fullName)}
                    </div>
                    <div>
                      <h2 className="text-xl font-display font-bold text-foreground">
                        {viewUser.fullName}
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        {viewUser.email}
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => setViewUser(null)}
                    className="rounded-lg p-2 text-foreground-secondary hover:text-foreground hover:bg-background-secondary transition-colors cursor-pointer"
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
                        className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          viewUser.active
                            ? "bg-emerald-500/10 text-emerald-400"
                            : "bg-red-500/10 text-red-400"
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
                    <div className="rounded-xl border border-border bg-background-secondary p-3">
                      <p className="text-xs text-foreground-secondary mb-1">
                        University
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {viewUser.universityName}
                      </p>
                    </div>
                  )}

                  <div className="rounded-xl border border-border bg-background-secondary p-3">
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
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setDeleteTarget(null)}
            >
              <motion.div
                className="w-full max-w-md rounded-2xl border border-border bg-card shadow-2xl"
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                onClick={(e) => e.stopPropagation()}
              >
                <div className="p-6 text-center">
                  <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-red-500/10">
                    <AlertTriangle className="h-7 w-7 text-red-400" />
                  </div>
                  <h2 className="text-xl font-display font-bold text-foreground mb-2">
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
      </main>
    </PageTransition>
  );
}
