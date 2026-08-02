"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Lock, LogOut, Save, Trash2, User } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { userApi, type UpdateProfileRequest } from "@/lib/api";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import SignalMeter from "@/components/ui/SignalMeter";
import WorkspacePageHeader from "@/components/workspace/WorkspacePageHeader";
import { FadeIn, PageTransition } from "@/components/ui/Animations";

export default function SettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, logout, logoutAll, setUser } = useAuthStore();
  const [name, setName] = useState("");
  const [university, setUniversity] = useState("");
  const [bio, setBio] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [company, setCompany] = useState("");
  const [location, setLocation] = useState("");
  const [website, setWebsite] = useState("");
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteMessage, setDeleteMessage] = useState<string | null>(null);
  const profileCompletion = useMemo(() => {
    const values = [name, university, bio, jobTitle, company, location, website];
    return Math.round((values.filter((value) => value.trim().length > 0).length / values.length) * 100);
  }, [bio, company, jobTitle, location, name, university, website]);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) router.replace("/login");
  }, [isAuthenticated, isLoading, router]);

  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;
    void (async () => {
      try {
        const response = await userApi.getProfile();
        if (!active || !response.success || !response.data) return;
        const profile = response.data;
        setName(profile.fullName);
        setUniversity(profile.universityName ?? "");
        setBio(profile.bio ?? "");
        setJobTitle(profile.jobTitle ?? "");
        setCompany(profile.company ?? "");
        setLocation(profile.location ?? "");
        setWebsite(profile.websiteUrl ?? "");
      } catch {
        if (active) setProfileMessage("We could not load your latest profile details.");
      }
    })();
    return () => { active = false; };
  }, [isAuthenticated]);

  const saveProfile = async () => {
    setProfileLoading(true);
    setProfileMessage(null);
    const body: UpdateProfileRequest = {
      fullName: name.trim() || undefined,
      universityName: university.trim() || undefined,
      bio: bio.trim() || undefined,
      jobTitle: jobTitle.trim() || undefined,
      company: company.trim() || undefined,
      location: location.trim() || undefined,
      websiteUrl: website.trim() || undefined,
    };
    try {
      const response = await userApi.updateProfile(body);
      if (!response.success || !response.data) {
        setProfileMessage(response.message);
        return;
      }
      if (user) setUser({ ...user, fullName: response.data.fullName, universityName: response.data.universityName });
      setProfileMessage("Profile saved.");
    } catch {
      setProfileMessage("We could not save your profile. Please try again.");
    } finally {
      setProfileLoading(false);
    }
  };

  const changePassword = async () => {
    setPasswordMessage(null);
    if (!currentPassword || !newPassword) {
      setPasswordMessage("Enter your current password and a new password.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordMessage("The new passwords do not match.");
      return;
    }
    setPasswordLoading(true);
    try {
      const response = await userApi.changePassword({ currentPassword, newPassword });
      if (!response.success) {
        setPasswordMessage(response.message);
        return;
      }
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordMessage("Password updated. Other sessions have been revoked.");
    } catch {
      setPasswordMessage("We could not update your password. Please try again.");
    } finally {
      setPasswordLoading(false);
    }
  };

  const signOutEverywhere = async () => {
    await logoutAll();
    router.replace("/login");
  };

  const deleteAccount = async () => {
    setDeleteLoading(true);
    setDeleteMessage(null);
    try {
      const response = await userApi.deleteAccount();
      if (!response.success) {
        setDeleteMessage(response.message);
        return;
      }
      await logout();
      router.replace("/login");
    } catch {
      setDeleteMessage("We could not delete your account. Please try again.");
    } finally {
      setDeleteLoading(false);
    }
  };

  if (isLoading || !isAuthenticated) return <div className="workspace-page"><div className="workspace-page__inner"><div className="workspace-skeleton h-36" /></div></div>;

  return (
    <PageTransition>
      <section className="workspace-page"><div className="workspace-page__inner max-w-3xl">
        <FadeIn><WorkspacePageHeader eyebrow="Personal workspace" title="Settings" description="Manage your profile, security controls, and active account access." /></FadeIn>
        <FadeIn delay={0.04} className="mb-6"><Card className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end"><div><p className="sf-meta text-accent">Workspace readiness</p><h2 className="mt-2 text-lg font-semibold text-foreground">Profile signal</h2><p className="mt-1 text-sm text-foreground-secondary">A complete profile helps keep your workspace context clear for teammates and reviews.</p></div><div className="min-w-52"><SignalMeter value={profileCompletion} label="Profile completeness" /></div></Card></FadeIn>
        <div className="space-y-6">
          <form onSubmit={(event) => { event.preventDefault(); void saveProfile(); }}><Card>
            <div className="mb-6 flex items-center gap-3"><User className="h-5 w-5 text-accent" /><div><h2 className="font-semibold text-foreground">Profile</h2><p className="text-sm text-foreground-secondary">Your profile information is stored in your account.</p></div></div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Full name" value={name} onChange={(event) => setName(event.target.value)} />
              <Input label="Email" value={user?.email ?? ""} disabled />
              <Input label="University" value={university} onChange={(event) => setUniversity(event.target.value)} />
              <Input label="Job title" value={jobTitle} onChange={(event) => setJobTitle(event.target.value)} />
              <Input label="Company" value={company} onChange={(event) => setCompany(event.target.value)} />
              <Input label="Location" value={location} onChange={(event) => setLocation(event.target.value)} />
              <div className="sm:col-span-2"><Input label="Website" value={website} onChange={(event) => setWebsite(event.target.value)} /></div>
              <label className="sm:col-span-2 text-sm font-medium text-foreground-secondary">Bio<textarea value={bio} onChange={(event) => setBio(event.target.value)} rows={4} className="mt-1.5 w-full rounded-xl border border-input-border bg-input-bg px-4 py-3 text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-primary/20" /></label>
            </div>
            {profileMessage && <p role="status" className={`mt-4 text-sm ${profileMessage === "Profile saved." ? "text-success" : "text-error"}`}>{profileMessage}</p>}
            <Button type="submit" className="mt-5" loading={profileLoading} icon={<Save className="h-4 w-4" />}>Save Profile</Button>
          </Card></form>

          <form onSubmit={(event) => { event.preventDefault(); void changePassword(); }}><Card>
            <div className="mb-6 flex items-center gap-3"><Lock className="h-5 w-5 text-accent" /><div><h2 className="font-semibold text-foreground">Password</h2><p className="text-sm text-foreground-secondary">Change the password for a local account.</p></div></div>
            <div className="grid gap-4"><Input label="Current password" type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" /><Input label="New password" type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" /><Input label="Confirm new password" type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" /></div>
            {passwordMessage && <p role="status" className={`mt-4 text-sm ${passwordMessage.startsWith("Password updated") ? "text-success" : "text-error"}`}>{passwordMessage}</p>}
            <Button type="submit" className="mt-5" loading={passwordLoading}>Update Password</Button>
          </Card></form>

          <Card>
            <h2 className="font-semibold text-foreground">Sessions</h2><p className="mt-1 text-sm text-foreground-secondary">Sign out all active sessions if you suspect unauthorized access.</p>
            <Button className="mt-5" variant="outline" onClick={signOutEverywhere} icon={<LogOut className="h-4 w-4" />}>Sign Out All Devices</Button>
          </Card>

          <Card className="border-error/30">
            <div className="flex items-start gap-3"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-error" /><div><h2 className="font-semibold text-foreground">Delete account</h2><p className="mt-1 text-sm text-foreground-secondary">This permanently deletes your account and associated data.</p></div></div>
            {deleteMessage && <p className="mt-4 text-sm text-error">{deleteMessage}</p>}
            {deleteOpen ? <div className="mt-5 flex flex-wrap gap-3"><Button variant="danger" onClick={deleteAccount} loading={deleteLoading} icon={<Trash2 className="h-4 w-4" />}>Permanently Delete Account</Button><Button variant="ghost" onClick={() => setDeleteOpen(false)}>Cancel</Button></div> : <Button className="mt-5" variant="danger" onClick={() => setDeleteOpen(true)} icon={<Trash2 className="h-4 w-4" />}>Delete Account</Button>}
          </Card>
        </div>
      </div></section>
    </PageTransition>
  );
}
