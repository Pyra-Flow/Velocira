"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  User,
  Shield,
  SlidersHorizontal,
  Bell,
  Save,
  Camera,
  Lock,
  Smartphone,
  Monitor,
  Globe,
  LogOut,
  Moon,
  Sun,
  Languages,
  FileDown,
  Mail,
  CheckCircle,
  AlertTriangle,
  Zap,
  Megaphone,
  CalendarDays,
  ChevronRight,
  Briefcase,
  MapPin,
  Link as LinkIcon,
  Loader2,
  Trash2,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useTheme } from "@/providers/ThemeProvider";
import { useAuthStore } from "@/store/authStore";
import { userApi, type UpdateProfileRequest } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  PageTransition,
} from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Types & Data                                                       */
/* ------------------------------------------------------------------ */

type SettingsTab = "profile" | "security" | "preferences" | "notifications";

const tabs: { key: SettingsTab; label: string; icon: React.ReactNode }[] = [
  { key: "profile", label: "Profile", icon: <User className="h-4 w-4" /> },
  { key: "security", label: "Security", icon: <Shield className="h-4 w-4" /> },
  { key: "preferences", label: "Preferences", icon: <SlidersHorizontal className="h-4 w-4" /> },
  { key: "notifications", label: "Notifications", icon: <Bell className="h-4 w-4" /> },
];

/* ------------------------------------------------------------------ */
/*  Toggle Switch Component                                            */
/* ------------------------------------------------------------------ */

function Toggle({
  enabled,
  onToggle,
}: {
  enabled: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ${
        enabled ? "bg-primary" : "bg-background-secondary"
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform duration-200 ${
          enabled ? "translate-x-5" : "translate-x-0"
        }`}
      />
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  Tab Content Animations                                             */
/* ------------------------------------------------------------------ */

const tabVariants = {
  enter: { opacity: 0, x: 20 },
  center: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: -20 },
};

/* ------------------------------------------------------------------ */
/*  Main Component                                                     */
/* ------------------------------------------------------------------ */

export default function SettingsPage() {
  const { t } = useLocale();
  const { theme, toggleTheme } = useTheme();
  const { user, isAuthenticated, isLoading, logout, logoutAll } = useAuthStore();
  const router = useRouter();

  const [activeTab, setActiveTab] = useState<SettingsTab>("profile");

  /* ---- Profile state ---- */
  const [profileName, setProfileName] = useState(user?.fullName ?? "");
  const [profileEmail] = useState(user?.email ?? "");
  const [profileUniversity, setProfileUniversity] = useState(user?.universityName ?? "");
  const [profileBio, setProfileBio] = useState("");
  const [profileJobTitle, setProfileJobTitle] = useState("");
  const [profileCompany, setProfileCompany] = useState("");
  const [profileLocation, setProfileLocation] = useState("");
  const [profileWebsite, setProfileWebsite] = useState("");
  const [profileSaved, setProfileSaved] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);

  /* ---- Security state ---- */
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [passwordSaved, setPasswordSaved] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  /* ---- Preferences state ---- */
  const [exportFormat, setExportFormat] = useState<"pdf" | "docx" | "md">("pdf");
  const [emailNotifications, setEmailNotifications] = useState(true);

  /* ---- Notifications state ---- */
  const [notifEmailUpdates, setNotifEmailUpdates] = useState(true);
  const [notifProjectCompletion, setNotifProjectCompletion] = useState(true);
  const [notifSecurityAlerts, setNotifSecurityAlerts] = useState(true);
  const [notifMarketing, setNotifMarketing] = useState(false);
  const [notifWeeklyDigest, setNotifWeeklyDigest] = useState(true);

  /* ---- Auth guard ---- */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  /* ---- Load profile from API on mount ---- */
  useEffect(() => {
    if (isAuthenticated) {
      userApi.getProfile().then((res) => {
        if (res.success && res.data) {
          setProfileName(res.data.fullName);
          setProfileUniversity(res.data.universityName ?? "");
          setProfileBio(res.data.bio ?? "");
          setProfileJobTitle(res.data.jobTitle ?? "");
          setProfileCompany(res.data.company ?? "");
          setProfileLocation(res.data.location ?? "");
          setProfileWebsite(res.data.websiteUrl ?? "");
        }
      });
    }
  }, [isAuthenticated]);

  /* ---- Handlers ---- */
  const handleSaveProfile = async () => {
    setProfileLoading(true);
    setProfileError(null);
    const body: UpdateProfileRequest = {
      fullName: profileName || undefined,
      universityName: profileUniversity || undefined,
      bio: profileBio || undefined,
      jobTitle: profileJobTitle || undefined,
      company: profileCompany || undefined,
      location: profileLocation || undefined,
      websiteUrl: profileWebsite || undefined,
    };
    const res = await userApi.updateProfile(body);
    setProfileLoading(false);
    if (res.success) {
      setProfileSaved(true);
      setTimeout(() => setProfileSaved(false), 2000);
    } else {
      setProfileError(res.message);
    }
  };

  const handleChangePassword = async () => {
    if (!newPassword || newPassword !== confirmPassword) return;
    setPasswordLoading(true);
    setPasswordError(null);
    const res = await userApi.changePassword({
      currentPassword,
      newPassword,
    });
    setPasswordLoading(false);
    if (res.success) {
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordSaved(true);
      setTimeout(() => setPasswordSaved(false), 2000);
    } else {
      setPasswordError(res.message);
    }
  };

  const handleLogoutAll = async () => {
    await logoutAll();
    router.push("/login");
  };

  const handleDeleteAccount = async () => {
    setDeleteLoading(true);
    setDeleteError(null);
    try {
      const res = await userApi.deleteAccount();
      if (res.success) {
        await logout();
        router.push("/login");
      } else {
        setDeleteError(res.message);
      }
    } catch {
      setDeleteError("Failed to delete account. Please try again.");
    } finally {
      setDeleteLoading(false);
    }
  };

  /* ---- Loading state ---- */
  if (isLoading || !isAuthenticated) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full"
        />
      </div>
    );
  }

  /* ---------------------------------------------------------------- */
  /*  Tab Renderers                                                    */
  /* ---------------------------------------------------------------- */

  const renderProfile = () => (
    <motion.div
      key="profile"
      variants={tabVariants}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{ duration: 0.25 }}
      className="space-y-6"
    >
      {/* Avatar placeholder */}
      <Card>
        <div className="flex items-center gap-6">
          <div className="relative">
            <div className="h-20 w-20 rounded-2xl bg-primary/10 flex items-center justify-center text-primary font-display text-2xl font-bold">
              {profileName
                .split(" ")
                .map((n) => n[0])
                .join("")
                .slice(0, 2)
                .toUpperCase()}
            </div>
            <button className="absolute -bottom-1 -right-1 h-7 w-7 rounded-full bg-primary text-white flex items-center justify-center shadow-md cursor-pointer">
              <Camera className="h-3.5 w-3.5" />
            </button>
          </div>
          <div>
            <h3 className="text-lg font-semibold text-foreground">{profileName}</h3>
            <p className="text-sm text-foreground-secondary">{profileEmail}</p>
          </div>
        </div>
      </Card>

      {/* Form */}
      <Card>
        <h3 className="text-base font-semibold text-foreground mb-5">Personal Information</h3>
        <div className="space-y-4">
          <Input
            label="Full Name"
            value={profileName}
            onChange={(e) => setProfileName(e.target.value)}
            icon={<User className="h-4 w-4" />}
            placeholder="Your full name"
          />
          <Input
            label="Email Address"
            value={profileEmail}
            readOnly
            icon={<Mail className="h-4 w-4" />}
            placeholder="Email"
            className="opacity-60 cursor-not-allowed"
          />
          <Input
            label="University"
            value={profileUniversity}
            onChange={(e) => setProfileUniversity(e.target.value)}
            icon={<Globe className="h-4 w-4" />}
            placeholder="Your university"
          />
          <div>
            <label className="mb-1.5 block text-sm font-medium text-foreground">Bio</label>
            <textarea
              value={profileBio}
              onChange={(e) => setProfileBio(e.target.value)}
              placeholder="Tell us about yourself…"
              maxLength={500}
              rows={3}
              className="w-full rounded-xl border border-border bg-input-bg px-4 py-2.5 text-sm text-foreground placeholder:text-foreground-secondary focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/30 transition-colors resize-none"
            />
            <p className="text-xs text-foreground-secondary mt-1">{profileBio.length}/500</p>
          </div>
          <Input
            label="Job Title"
            value={profileJobTitle}
            onChange={(e) => setProfileJobTitle(e.target.value)}
            icon={<Briefcase className="h-4 w-4" />}
            placeholder="Your job title"
          />
          <Input
            label="Company"
            value={profileCompany}
            onChange={(e) => setProfileCompany(e.target.value)}
            icon={<Briefcase className="h-4 w-4" />}
            placeholder="Your company"
          />
          <Input
            label="Location"
            value={profileLocation}
            onChange={(e) => setProfileLocation(e.target.value)}
            icon={<MapPin className="h-4 w-4" />}
            placeholder="City, Country"
          />
          <Input
            label="Website URL"
            value={profileWebsite}
            onChange={(e) => setProfileWebsite(e.target.value)}
            icon={<LinkIcon className="h-4 w-4" />}
            placeholder="https://yoursite.com"
          />
        </div>
        {profileError && (
          <p className="mt-3 text-sm text-error flex items-center gap-1">
            <AlertTriangle className="h-3.5 w-3.5" /> {profileError}
          </p>
        )}
        <div className="flex items-center gap-3 mt-6">
          <Button
            variant="primary"
            icon={profileLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            onClick={handleSaveProfile}
            disabled={profileLoading}
          >
            {profileLoading ? "Saving…" : "Save Changes"}
          </Button>
          {profileSaved && (
            <motion.span
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              className="text-sm text-emerald-400 flex items-center gap-1"
            >
              <CheckCircle className="h-4 w-4" /> Saved
            </motion.span>
          )}
        </div>
      </Card>
    </motion.div>
  );

  const renderSecurity = () => (
    <motion.div
      key="security"
      variants={tabVariants}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{ duration: 0.25 }}
      className="space-y-6"
    >
      {/* Change Password */}
      <Card>
        <h3 className="text-base font-semibold text-foreground mb-5">Change Password</h3>
        <div className="space-y-4 max-w-md">
          <Input
            label="Current Password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            icon={<Lock className="h-4 w-4" />}
            placeholder="Enter current password"
          />
          <Input
            label="New Password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            icon={<Lock className="h-4 w-4" />}
            placeholder="Enter new password"
          />
          <Input
            label="Confirm New Password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            icon={<Lock className="h-4 w-4" />}
            placeholder="Confirm new password"
          />
          {newPassword && confirmPassword && newPassword !== confirmPassword && (
            <p className="text-sm text-red-400 flex items-center gap-1">
              <AlertTriangle className="h-3.5 w-3.5" /> Passwords do not match
            </p>
          )}
          {passwordError && (
            <p className="text-sm text-error flex items-center gap-1">
              <AlertTriangle className="h-3.5 w-3.5" /> {passwordError}
            </p>
          )}
        </div>
        <div className="flex items-center gap-3 mt-6">
          <Button
            variant="primary"
            icon={passwordLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            onClick={handleChangePassword}
            disabled={!currentPassword || !newPassword || newPassword !== confirmPassword || passwordLoading}
          >
            {passwordLoading ? "Updating…" : "Update Password"}
          </Button>
          {passwordSaved && (
            <motion.span
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              className="text-sm text-emerald-400 flex items-center gap-1"
            >
              <CheckCircle className="h-4 w-4" /> Updated
            </motion.span>
          )}
        </div>
      </Card>

      {/* Two-Factor Auth */}
      <Card>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
              <Smartphone className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">Two-Factor Authentication</h3>
              <p className="text-sm text-foreground-secondary">
                Add an extra layer of security to your account
              </p>
            </div>
          </div>
          <Toggle enabled={twoFactorEnabled} onToggle={() => setTwoFactorEnabled(!twoFactorEnabled)} />
        </div>
        {twoFactorEnabled && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            className="mt-4 p-4 rounded-xl bg-background-secondary border border-border"
          >
            <p className="text-sm text-foreground-secondary">
              Two-factor authentication is enabled. A QR code setup would appear here in production.
            </p>
          </motion.div>
        )}
      </Card>

      {/* Active Sessions — Logout All */}
      <Card>
        <h3 className="text-base font-semibold text-foreground mb-5">Active Sessions</h3>
        <p className="text-sm text-foreground-secondary mb-4">
          If you suspect unauthorized access, you can sign out of all other devices.
        </p>
        <Button
          variant="danger"
          size="sm"
          icon={<LogOut className="h-4 w-4" />}
          onClick={handleLogoutAll}
        >
          Sign Out All Devices
        </Button>
      </Card>

      {/* Danger Zone — Delete Account */}
      <Card className="border-red-500/20">
        <div className="flex items-center gap-4 mb-4">
          <div className="h-10 w-10 rounded-xl bg-red-500/10 flex items-center justify-center text-red-400">
            <AlertTriangle className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-foreground">Danger Zone</h3>
            <p className="text-sm text-foreground-secondary">
              Permanently delete your account and all associated data
            </p>
          </div>
        </div>
        {!deleteConfirmOpen ? (
          <Button
            variant="danger"
            size="sm"
            icon={<Trash2 className="h-4 w-4" />}
            onClick={() => setDeleteConfirmOpen(true)}
          >
            Delete Account
          </Button>
        ) : (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            className="p-4 rounded-xl bg-red-500/5 border border-red-500/20 space-y-3"
          >
            <p className="text-sm text-foreground">
              <strong>This action is irreversible.</strong> All your projects, documents, and account data will be permanently deleted.
            </p>
            {deleteError && (
              <p className="text-sm text-error flex items-center gap-1">
                <AlertTriangle className="h-3.5 w-3.5" /> {deleteError}
              </p>
            )}
            <div className="flex items-center gap-2">
              <Button
                variant="danger"
                size="sm"
                icon={deleteLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                onClick={handleDeleteAccount}
                disabled={deleteLoading}
              >
                {deleteLoading ? "Deleting…" : "Yes, Delete My Account"}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => { setDeleteConfirmOpen(false); setDeleteError(null); }}
              >
                Cancel
              </Button>
            </div>
          </motion.div>
        )}
      </Card>
    </motion.div>
  );

  const renderPreferences = () => (
    <motion.div
      key="preferences"
      variants={tabVariants}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{ duration: 0.25 }}
      className="space-y-6"
    >
      {/* Theme */}
      <Card>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
              {theme === "dark" ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />}
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">Theme</h3>
              <p className="text-sm text-foreground-secondary">
                {theme === "dark" ? "Dark mode is active" : "Light mode is active"}
              </p>
            </div>
          </div>
          <Toggle enabled={theme === "dark"} onToggle={toggleTheme} />
        </div>
      </Card>

      {/* Default Export Format */}
      <Card>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary shrink-0">
              <FileDown className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">Default Export Format</h3>
              <p className="text-sm text-foreground-secondary">
                Choose the default format for exported documents
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {(["pdf", "docx", "md"] as const).map((fmt) => (
              <button
                key={fmt}
                onClick={() => setExportFormat(fmt)}
                className={`px-4 py-2 text-sm font-medium rounded-xl border transition-all cursor-pointer uppercase ${
                  exportFormat === fmt
                    ? "bg-primary/10 border-primary text-primary"
                    : "border-border text-foreground-secondary hover:border-border-hover"
                }`}
              >
                {fmt}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Email Notifications Toggle */}
      <Card>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
              <Mail className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">Email Notifications</h3>
              <p className="text-sm text-foreground-secondary">
                Receive important updates via email
              </p>
            </div>
          </div>
          <Toggle
            enabled={emailNotifications}
            onToggle={() => setEmailNotifications(!emailNotifications)}
          />
        </div>
      </Card>
    </motion.div>
  );

  const notificationItems: {
    key: string;
    label: string;
    description: string;
    icon: React.ReactNode;
    enabled: boolean;
    onToggle: () => void;
  }[] = [
    {
      key: "email-updates",
      label: "Email Updates",
      description: "Get notified about important account changes",
      icon: <Mail className="h-5 w-5" />,
      enabled: notifEmailUpdates,
      onToggle: () => setNotifEmailUpdates(!notifEmailUpdates),
    },
    {
      key: "project-completion",
      label: "Project Completion Alerts",
      description: "Receive alerts when documentation generation completes",
      icon: <CheckCircle className="h-5 w-5" />,
      enabled: notifProjectCompletion,
      onToggle: () => setNotifProjectCompletion(!notifProjectCompletion),
    },
    {
      key: "security-alerts",
      label: "Security Alerts",
      description: "Get notified about suspicious login attempts or security events",
      icon: <AlertTriangle className="h-5 w-5" />,
      enabled: notifSecurityAlerts,
      onToggle: () => setNotifSecurityAlerts(!notifSecurityAlerts),
    },
    {
      key: "marketing",
      label: "Marketing Emails",
      description: "Receive product updates, tips, and promotional content",
      icon: <Megaphone className="h-5 w-5" />,
      enabled: notifMarketing,
      onToggle: () => setNotifMarketing(!notifMarketing),
    },
    {
      key: "weekly-digest",
      label: "Weekly Digest",
      description: "Get a weekly summary of your project activity",
      icon: <CalendarDays className="h-5 w-5" />,
      enabled: notifWeeklyDigest,
      onToggle: () => setNotifWeeklyDigest(!notifWeeklyDigest),
    },
  ];

  const renderNotifications = () => (
    <motion.div
      key="notifications"
      variants={tabVariants}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{ duration: 0.25 }}
      className="space-y-4"
    >
      {notificationItems.map((item) => (
        <Card key={item.key}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
                {item.icon}
              </div>
              <div>
                <h3 className="text-base font-semibold text-foreground">{item.label}</h3>
                <p className="text-sm text-foreground-secondary">{item.description}</p>
              </div>
            </div>
            <Toggle enabled={item.enabled} onToggle={item.onToggle} />
          </div>
        </Card>
      ))}
    </motion.div>
  );

  const tabContent: Record<SettingsTab, () => React.ReactNode> = {
    profile: renderProfile,
    security: renderSecurity,
    preferences: renderPreferences,
    notifications: renderNotifications,
  };

  /* ---------------------------------------------------------------- */
  /*  Render                                                           */
  /* ---------------------------------------------------------------- */

  return (
    <PageTransition>
      <section className="min-h-[calc(100vh-4rem)] py-12 px-4 sm:px-6 lg:px-8 max-w-5xl mx-auto">
        {/* ---- Header ---- */}
        <FadeIn>
          <div className="mb-8">
            <h1 className="text-3xl sm:text-4xl font-display font-bold text-foreground">
              Settings
            </h1>
            <p className="mt-1 text-foreground-secondary">
              Manage your account, preferences, and notifications
            </p>
          </div>
        </FadeIn>

        {/* ---- Layout: sidebar tabs + content ---- */}
        <FadeIn delay={0.1}>
          <div className="flex flex-col lg:flex-row gap-8">
            {/* Tab Navigation */}
            <nav className="lg:w-56 shrink-0">
              <div className="flex lg:flex-col gap-1 overflow-x-auto lg:overflow-visible pb-2 lg:pb-0">
                {tabs.map((tab) => {
                  const isActive = activeTab === tab.key;
                  return (
                    <button
                      key={tab.key}
                      onClick={() => setActiveTab(tab.key)}
                      className={`flex items-center gap-3 px-4 py-2.5 text-sm font-medium rounded-xl transition-all whitespace-nowrap cursor-pointer ${
                        isActive
                          ? "bg-primary/10 text-primary border border-primary/20"
                          : "text-foreground-secondary hover:text-foreground hover:bg-card border border-transparent"
                      }`}
                    >
                      {tab.icon}
                      {tab.label}
                      {isActive && (
                        <ChevronRight className="h-3.5 w-3.5 ml-auto hidden lg:block" />
                      )}
                    </button>
                  );
                })}
              </div>
            </nav>

            {/* Tab Content */}
            <div className="flex-1 min-w-0">
              <AnimatePresence mode="wait">
                {tabContent[activeTab]()}
              </AnimatePresence>
            </div>
          </div>
        </FadeIn>
      </section>
    </PageTransition>
  );
}
