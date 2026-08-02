"use client";

import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  CheckCircle2,
  FileText,
  GitBranch,
  Loader2,
  Search,
  Sparkles,
  Upload,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalMeter from "@/components/ui/SignalMeter";
import StatusBadge from "@/components/ui/StatusBadge";
import { useSignalMotion } from "@/components/ui/Animations";
import {
  knowledgeSourceApi,
  srsApi,
  type KnowledgeSourceResponse,
  type SrsRequirementResponse,
  type SrsVersionResponse,
  type StandardsProfileResponse,
} from "@/lib/api";

type Props = { projectId: string; generationUnlocked: boolean; onUpdated?: () => void };
type ActiveAction = "generate" | "upload" | "source" | null;
const EMPTY_REQUIREMENTS: SrsRequirementResponse[] = [];

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function sourceLabel(sourceId: string | null | undefined, sources: KnowledgeSourceResponse[]) {
  if (sourceId === "00000000-0000-0000-0000-000000000001") return "Confirmed project answers";
  return sources.find((source) => source.id === sourceId)?.title ?? sourceId ?? "Source reference unavailable";
}

function formatDate(value: string | null | undefined) {
  if (!value) return "Not recorded";
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return value;
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}

function requestErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function sourceStatus(status: KnowledgeSourceResponse["status"]) {
  if (status === "APPROVED") return "APPROVED";
  if (status === "PENDING_REVIEW") return "NEEDS_REVIEW";
  if (status === "QUARANTINED") return "FAILED";
  return "DRAFT";
}

function RequirementDetail({ requirement, sources }: { requirement: SrsRequirementResponse; sources: KnowledgeSourceResponse[] }) {
  const traceLinks = requirement.traceLinks ?? [];

  return (
    <div className="mt-4 grid gap-4 border-t border-border pt-4 text-sm text-foreground-secondary lg:grid-cols-[minmax(0,1fr)_minmax(15rem,0.8fr)]">
      <div className="space-y-3">
        <p><span className="font-medium text-foreground">Acceptance:</span> {requirement.acceptanceCriteria || "Not recorded"}</p>
        <p><span className="font-medium text-foreground">Verification:</span> {requirement.verificationMethod || "Not recorded"}</p>
        {requirement.rationale && <p><span className="font-medium text-foreground">Rationale:</span> {requirement.rationale}</p>}
      </div>
      <div className="border-l-0 border-border lg:border-l lg:pl-4">
        <p className="sf-meta text-foreground-secondary">Trace evidence</p>
        {traceLinks.length > 0 ? (
          <ul className="mt-2 space-y-2">
            {traceLinks.map((link, index) => (
              <li key={`${link.sourceId ?? "source"}-${link.chunkId ?? "chunk"}-${link.linkType}-${index}`} className="border-l-2 border-accent/45 pl-3">
                <p className="text-foreground">{sourceLabel(link.sourceId, sources)}</p>
                <p className="sf-meta mt-1 text-foreground-secondary">{label(link.linkType)}{link.chunkId ? ` · Chunk ${link.chunkId}` : ""}</p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-sm text-foreground-secondary">No trace links recorded for this requirement.</p>
        )}
        {requirement.sourceDetail && <p className="mt-3 border-t border-border pt-3 text-xs leading-5 text-foreground-secondary"><span className="font-medium text-foreground">Source note:</span> {requirement.sourceDetail}</p>}
      </div>
    </div>
  );
}

function RequirementRow({ requirement, sources }: { requirement: SrsRequirementResponse; sources: KnowledgeSourceResponse[] }) {
  return (
    <details className="group border border-border bg-background-secondary/20 px-3 py-3 transition-colors duration-150 open:border-accent/45 open:bg-card-hover sm:px-4">
      <summary className="cursor-pointer list-none focus-ring">
        <span className="grid gap-3 md:grid-cols-[minmax(9rem,0.55fr)_minmax(0,1.8fr)_minmax(9rem,0.55fr)] md:items-center">
          <span className="flex flex-wrap items-center gap-2">
            <span className="sf-meta text-foreground">{requirement.requirementId}</span>
            <span className="border border-border bg-background px-1.5 py-0.5 text-[0.62rem] font-medium uppercase tracking-[0.1em] text-foreground-secondary">{label(requirement.type)}</span>
          </span>
          <span className="text-sm font-medium leading-5 text-foreground">{requirement.statement}</span>
          <span className="flex flex-wrap items-center gap-2 md:justify-end">
            <span className={`border px-1.5 py-0.5 text-[0.62rem] font-medium uppercase tracking-[0.1em] ${requirement.sourceKind === "CITATION" ? "border-accent/45 bg-accent-light text-accent" : "border-warning/45 bg-warning/10 text-warning"}`}>{label(requirement.sourceKind)}</span>
            <span className="sf-meta text-foreground-secondary">{requirement.traceLinks.length} trace {requirement.traceLinks.length === 1 ? "link" : "links"}</span>
          </span>
        </span>
      </summary>
      <RequirementDetail requirement={requirement} sources={sources} />
    </details>
  );
}

export default function SrsWorkspace({ projectId, generationUnlocked, onUpdated }: Props) {
  const [sources, setSources] = useState<KnowledgeSourceResponse[]>([]);
  const [profiles, setProfiles] = useState<StandardsProfileResponse[]>([]);
  const [versions, setVersions] = useState<SrsVersionResponse[]>([]);
  const [selectedProfile, setSelectedProfile] = useState("STARTER");
  const [selectedVersionId, setSelectedVersionId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [requirementQuery, setRequirementQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { shouldReduceMotion, transition } = useSignalMotion();

  const selectedVersion = useMemo(() => versions.find((version) => version.id === selectedVersionId) ?? versions[0] ?? null, [selectedVersionId, versions]);
  const selectedProfileInfo = profiles.find((profile) => profile.key === selectedProfile) ?? profiles[0];
  const requirements = selectedVersion?.requirements ?? EMPTY_REQUIREMENTS;
  const traceLinkedRequirements = useMemo(() => requirements.filter((requirement) => requirement.traceLinks.length > 0).length, [requirements]);
  const citedRequirements = useMemo(() => requirements.filter((requirement) => requirement.sourceKind === "CITATION").length, [requirements]);
  const traceLinkCount = useMemo(() => requirements.reduce((count, requirement) => count + requirement.traceLinks.length, 0), [requirements]);
  const filteredRequirements = useMemo(() => {
    const query = requirementQuery.trim().toLowerCase();
    if (!query) return requirements;
    return requirements.filter((requirement) => [
      requirement.requirementId,
      requirement.statement,
      requirement.type,
      requirement.priority,
      requirement.sourceKind,
      requirement.verificationMethod,
    ].some((value) => value?.toLowerCase().includes(query)));
  }, [requirementQuery, requirements]);
  const citationCoverage = selectedVersion?.citationCoverage ?? selectedVersion?.validation.citationCoverage ?? selectedVersion?.validation.citation_coverage;
  const validationIssues = selectedVersion?.validation.issues ?? [];

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [sourceResult, profileResult, versionResult] = await Promise.all([knowledgeSourceApi.list(projectId), srsApi.profiles(projectId), srsApi.list(projectId)]);
      if (sourceResult.success && sourceResult.data) setSources(sourceResult.data);
      if (profileResult.success && profileResult.data) {
        setProfiles(profileResult.data);
        setSelectedProfile((current) => profileResult.data!.some((profile) => profile.key === current) ? current : profileResult.data![0]?.key ?? "STARTER");
      }
      if (versionResult.success && versionResult.data) {
        setVersions(versionResult.data);
        setSelectedVersionId((current) => current || versionResult.data![0]?.id || "");
      }
      setError([sourceResult, profileResult, versionResult].find((result) => !result.success)?.message ?? null);
    } catch (caught) {
      setError(requestErrorMessage(caught, "Unable to load document controls. Please try again."));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const refresh = async () => { await load(); onUpdated?.(); };
  const generate = async () => {
    setSubmitting(true); setActiveAction("generate"); setError(null); setSuccessMessage(null);
    try {
      const result = await srsApi.generate(projectId, selectedProfile);
      if (result.success && result.data) {
        await refresh();
        setSelectedVersionId(result.data.id);
        setSuccessMessage(`SRS version ${result.data.versionNumber} is ready for review.`);
      } else setError(result.message);
    } catch (caught) {
      setError(requestErrorMessage(caught, "The SRS could not be generated. Please try again."));
    } finally {
      setActiveAction(null); setSubmitting(false);
    }
  };
  const upload = async () => {
    if (!file) return;
    const uploadedFilename = file.name;
    setSubmitting(true); setActiveAction("upload"); setError(null); setSuccessMessage(null);
    try {
      const result = await knowledgeSourceApi.upload(projectId, file);
      if (!result.success) setError(result.message);
      else setSuccessMessage(`${uploadedFilename} was added to the project evidence register.`);
      setFile(null); await refresh();
    } catch (caught) {
      setError(requestErrorMessage(caught, "The source file could not be added. Please try again."));
    } finally {
      setActiveAction(null); setSubmitting(false);
    }
  };
  const updateSource = async (source: KnowledgeSourceResponse, action: "approve" | "reject" | "delete") => {
    setSubmitting(true); setActiveAction("source"); setError(null); setSuccessMessage(null);
    try {
      const result = action === "approve" ? await knowledgeSourceApi.approve(projectId, source.id)
        : action === "reject" ? await knowledgeSourceApi.reject(projectId, source.id)
          : await knowledgeSourceApi.delete(projectId, source.id);
      if (!result.success) setError(result.message);
      else setSuccessMessage(action === "approve" ? `${source.title} is now available for citation.` : action === "reject" ? `${source.title} was excluded from citation.` : `${source.title} was removed from the evidence register.`);
      await refresh();
    } catch (caught) {
      setError(requestErrorMessage(caught, "The source review could not be updated. Please try again."));
    } finally {
      setActiveAction(null); setSubmitting(false);
    }
  };

  if (loading) return <Card className="flex items-center gap-2 py-8 text-sm text-foreground-secondary" role="status" aria-live="polite"><Loader2 className="h-5 w-5 animate-spin text-accent" /> Loading document controls…</Card>;

  const operationLabel = activeAction === "generate" ? "Generating SRS" : activeAction === "upload" ? "Adding source evidence" : activeAction === "source" ? "Updating source review" : null;
  const validationTone = selectedVersion?.validation.valid === false ? "error" : validationIssues.length > 0 ? "warning" : "accent";

  return (
    <section className="space-y-4" aria-labelledby="srs-heading">
      <Card className="border-accent/25 bg-card p-5 sm:p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl"><div className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-accent" /><h2 id="srs-heading" className="text-xl font-semibold text-foreground">Software requirements</h2></div><p className="mt-2 text-sm leading-6 text-foreground-secondary">Generate a structured SRS directly from the answers above. No file upload, approval, or separate review page is required.</p></div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <label className="grid gap-1 text-sm text-foreground-secondary"><span className="sf-meta uppercase tracking-wide">Standards profile</span><select value={selectedProfile} onChange={(event) => setSelectedProfile(event.target.value)} disabled={submitting} className="min-w-48 rounded-md border border-input-border bg-input-bg px-3 py-2.5 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20">{profiles.map((profile) => <option key={profile.key} value={profile.key}>{profile.name}</option>)}</select></label>
            <Button onClick={() => void generate()} disabled={!generationUnlocked || submitting} loading={submitting && activeAction === "generate"} icon={<FileText className="h-4 w-4" />}>Generate SRS</Button>
          </div>
        </div>
        {!generationUnlocked && <p className="mt-4 border border-border bg-background-secondary/60 px-3 py-2.5 text-sm text-foreground-secondary">Finish the active discovery question above and this button unlocks automatically.</p>}
        {selectedProfileInfo && <p className="sf-meta mt-4 text-foreground-secondary">{selectedProfileInfo.description} · {selectedProfileInfo.controls.length} included checks</p>}
        <AnimatePresence initial={false}>
          {operationLabel && <motion.div key="srs-operation" initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex items-center gap-2 border border-accent/25 bg-accent-light px-3 py-2.5 text-sm text-accent" role="status" aria-live="polite"><Loader2 className="h-4 w-4 animate-spin" />{operationLabel}. Existing versions remain available.</motion.div>}
          {successMessage && <motion.div key={successMessage} initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex gap-3 border border-accent/35 bg-accent-light px-3 py-2.5 text-sm text-accent" role="status" aria-live="polite"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" /><p>{successMessage}</p></motion.div>}
          {error && <motion.div key={error} initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex gap-3 border border-error/25 bg-error/5 p-3 text-sm text-error" role="alert" aria-live="assertive"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /><div><p className="font-medium">The SRS was not generated</p><p className="mt-1 text-error/90">{error}</p><p className="mt-1 text-xs text-error/80">Nothing was saved. You can safely try again once the service is available.</p></div></motion.div>}
        </AnimatePresence>
      </Card>

      {selectedVersion && <Card className="space-y-5" aria-labelledby="srs-readiness-heading">
        <div className="flex flex-col gap-3 border-b border-border pb-4 lg:flex-row lg:items-center lg:justify-between">
          <div><div className="flex flex-wrap items-center gap-2"><h3 id="srs-readiness-heading" className="font-semibold text-foreground">Requirements readiness</h3><StatusBadge status={selectedVersion.status} label={label(selectedVersion.status)} /></div><p className="sf-meta mt-1 text-foreground-secondary">SRS v{selectedVersion.versionNumber} · Generated {formatDate(selectedVersion.generatedAt)}</p></div>
          {versions.length > 1 && <label className="grid gap-1 text-sm text-foreground-secondary"><span className="sf-meta uppercase tracking-wide">Version</span><select value={selectedVersion.id} onChange={(event) => setSelectedVersionId(event.target.value)} className="rounded-md border border-input-border bg-input-bg px-3 py-2 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20">{versions.map((version) => <option key={version.id} value={version.id}>Version {version.versionNumber}</option>)}</select></label>}
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Requirements</p><p className="mt-2 text-2xl font-semibold text-foreground">{requirements.length}</p><p className="mt-1 text-xs text-foreground-secondary">{traceLinkedRequirements} with recorded trace links</p></div>
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Citation register</p><p className="mt-2 text-2xl font-semibold text-foreground">{citedRequirements}</p><p className="mt-1 text-xs text-foreground-secondary">requirements marked as cited</p></div>
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Validation</p><p className={`mt-2 text-sm font-semibold ${selectedVersion.validation.valid === true ? "text-accent" : validationIssues.length > 0 ? "text-warning" : "text-foreground"}`}>{selectedVersion.validation.valid === true ? "Passed" : validationIssues.length > 0 ? `${validationIssues.length} issue${validationIssues.length === 1 ? "" : "s"} reported` : "Not reported"}</p><p className="mt-2 text-xs text-foreground-secondary">{selectedVersion.changeRequest ? "A change request is recorded" : "No change request recorded"}</p></div>
        </div>

        {citationCoverage != null ? <SignalMeter value={citationCoverage} tone={validationTone} label="Citation coverage" detail={`${traceLinkCount} recorded trace ${traceLinkCount === 1 ? "link" : "links"}`} /> : <p className="border-l-2 border-border pl-3 text-sm text-foreground-secondary">Citation coverage has not been reported for this version.</p>}

        {validationIssues.length > 0 && <div className="border-l-2 border-warning pl-3"><p className="sf-meta text-warning">Validation findings</p><ul className="mt-2 space-y-1 text-sm text-foreground-secondary">{validationIssues.map((issue, index) => <li key={`${issue}-${index}`}>{issue}</li>)}</ul></div>}
        {selectedVersion.changeRequest && <div className="border-l-2 border-warning pl-3"><p className="sf-meta text-warning">Change request</p><p className="mt-2 text-sm text-foreground-secondary">{selectedVersion.changeRequest}</p></div>}

        <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-end sm:justify-between">
          <div><h4 className="text-sm font-semibold text-foreground">Requirement trace register</h4><p className="mt-1 text-sm text-foreground-secondary">Review requirement evidence, acceptance criteria, and verification detail.</p></div>
          <label className="relative block sm:w-72"><span className="sr-only">Filter requirements</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-secondary" /><input value={requirementQuery} onChange={(event) => setRequirementQuery(event.target.value)} placeholder="Filter IDs or evidence" className="w-full border border-input-border bg-input-bg py-2 pl-9 pr-3 text-sm text-foreground placeholder:text-foreground-secondary focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20" /></label>
        </div>
        <p className="sf-meta text-foreground-secondary">{filteredRequirements.length} of {requirements.length} requirements shown</p>
        {filteredRequirements.length > 0 ? <div className="space-y-2">{filteredRequirements.map((requirement) => <RequirementRow key={requirement.id} requirement={requirement} sources={sources} />)}</div> : <div className="border border-dashed border-border px-4 py-8 text-center"><GitBranch className="mx-auto h-5 w-5 text-accent" /><p className="mt-3 text-sm font-medium text-foreground">No requirement matches this filter.</p><p className="mt-1 text-sm text-foreground-secondary">Clear the filter to review the generated trace register.</p></div>}
      </Card>}

      <details className="border border-border bg-card p-4">
        <summary className="cursor-pointer text-sm font-semibold text-foreground focus-ring">Add an optional source file for richer citations</summary>
        <p className="mt-2 text-sm text-foreground-secondary">Your discovery answers already work as the default source. Add text, Markdown, or JSON only when you want an additional reference.</p>
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center"><input aria-label="Optional project evidence file" type="file" accept=".txt,.md,.markdown,.json,text/plain,text/markdown,application/json" onChange={(event: ChangeEvent<HTMLInputElement>) => setFile(event.target.files?.[0] ?? null)} disabled={submitting} className="block min-w-0 text-sm text-foreground-secondary file:mr-3 file:rounded-md file:border-0 file:bg-accent-light file:px-3 file:py-2 file:text-sm file:font-medium file:text-accent" /><Button size="sm" variant="outline" onClick={() => void upload()} disabled={!file || submitting} loading={submitting && activeAction === "upload"} icon={<Upload className="h-4 w-4" />}>Add source</Button></div>
        {sources.length > 0 && <div className="mt-5 border-t border-border pt-4"><div className="mb-3 flex flex-wrap items-baseline justify-between gap-2"><h3 className="text-sm font-semibold text-foreground">Evidence register</h3><span className="sf-meta text-foreground-secondary">{sources.length} recorded source{sources.length === 1 ? "" : "s"}</span></div><div className="space-y-2">{sources.map((source) => <div key={source.id} className="flex flex-wrap items-center justify-between gap-3 border border-border px-3 py-3"><div className="min-w-0"><p className="truncate text-sm font-medium text-foreground">{source.title}</p><p className="sf-meta mt-1 text-foreground-secondary">{source.chunkCount} chunk{source.chunkCount === 1 ? "" : "s"} · Added {formatDate(source.createdAt)}</p></div><div className="flex flex-wrap items-center gap-2"><StatusBadge status={sourceStatus(source.status)} label={label(source.status)} />{source.status === "PENDING_REVIEW" && <><Button size="sm" variant="success" onClick={() => void updateSource(source, "approve")} disabled={submitting}>Use source</Button><Button size="sm" variant="ghost" onClick={() => void updateSource(source, "reject")} disabled={submitting}>Ignore</Button></>}<Button size="sm" variant="ghost" onClick={() => void updateSource(source, "delete")} disabled={submitting}>Remove</Button></div></div>)}</div></div>}
      </details>
    </section>
  );
}
