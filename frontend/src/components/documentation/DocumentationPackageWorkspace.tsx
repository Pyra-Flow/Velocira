"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  CheckCircle2,
  Download,
  FileDown,
  Files,
  GitBranch,
  Loader2,
  Search,
  Sparkles,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import { useSignalMotion } from "@/components/ui/Animations";
import {
  documentationPackageApi,
  downloadDocumentationExport,
  srsApi,
  type DocumentationArtifactType,
  type DocumentationExportLayout,
  type DocumentationExportRequest,
  type DocumentationExportFormat,
  type DocumentationExportResponse,
  type DocumentationExportTemplate,
  type DocumentationExportTheme,
  type DocumentationPackageResponse,
  type DocumentationTraceResponse,
  type SrsVersionResponse,
} from "@/lib/api";

const exportFormats: Array<{ value: DocumentationExportFormat; label: string }> = [
  { value: "ZIP", label: "Complete ZIP" }, { value: "PDF", label: "PDF" }, { value: "DOCX", label: "Word" }, { value: "MARKDOWN", label: "Markdown" }, { value: "OPENAPI_JSON", label: "OpenAPI JSON" }, { value: "OPENAPI_YAML", label: "OpenAPI YAML" }, { value: "UML_SOURCE", label: "UML source" }, { value: "ERD_SOURCE", label: "ERD source" },
];

const styledExportFormats = new Set<DocumentationExportFormat>(["ZIP", "PDF", "DOCX"]);

const exportTemplates: Array<{ value: DocumentationExportTemplate; label: string; description: string }> = [
  { value: "EXECUTIVE", label: "Executive", description: "A polished summary with callouts and clear milestones." },
  { value: "TECHNICAL", label: "Technical", description: "Architecture-forward detail for implementation teams." },
  { value: "MINIMAL", label: "Minimal", description: "A restrained, print-friendly reading experience." },
];

const exportThemes: Array<{
  value: DocumentationExportTheme;
  label: string;
  barClass: string;
  textClass: string;
  borderClass: string;
  surfaceClass: string;
  softSurfaceClass: string;
}> = [
  { value: "SIGNAL", label: "Signal", barClass: "bg-accent", textClass: "text-accent", borderClass: "border-accent/45", surfaceClass: "bg-accent-light", softSurfaceClass: "bg-accent-light/55" },
  { value: "OCEAN", label: "Ocean", barClass: "bg-sky-500", textClass: "text-sky-700 dark:text-sky-300", borderClass: "border-sky-500/45", surfaceClass: "bg-sky-500/10", softSurfaceClass: "bg-sky-500/5" },
  { value: "VIOLET", label: "Violet", barClass: "bg-violet-500", textClass: "text-violet-700 dark:text-violet-300", borderClass: "border-violet-500/45", surfaceClass: "bg-violet-500/10", softSurfaceClass: "bg-violet-500/5" },
  { value: "EMERALD", label: "Emerald", barClass: "bg-emerald-500", textClass: "text-emerald-700 dark:text-emerald-300", borderClass: "border-emerald-500/45", surfaceClass: "bg-emerald-500/10", softSurfaceClass: "bg-emerald-500/5" },
  { value: "MONOCHROME", label: "Monochrome", barClass: "bg-slate-500", textClass: "text-slate-700 dark:text-slate-200", borderClass: "border-slate-500/45", surfaceClass: "bg-slate-500/10", softSurfaceClass: "bg-slate-500/5" },
];

const exportLayouts: Array<{ value: DocumentationExportLayout; label: string; description: string }> = [
  { value: "STANDARD", label: "Standard", description: "Balanced reading and reference layout." },
  { value: "COMPACT", label: "Compact", description: "Dense technical hand-off with less whitespace." },
  { value: "PRESENTATION", label: "Presentation", description: "Generous hierarchy for stakeholder review." },
];

type Props = { projectId: string; generationUnlocked: boolean; refreshVersion?: number; onUpdated?: () => void };
type ActiveAction = "generate" | "export" | null;
type TraceFilter = "ALL" | "USE_CASE" | "ENTITY" | "API";

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
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

function formatBytes(byteSize: number) {
  if (byteSize < 1024) return `${byteSize} B`;
  if (byteSize < 1024 * 1024) return `${(byteSize / 1024).toFixed(1)} KB`;
  return `${(byteSize / (1024 * 1024)).toFixed(1)} MB`;
}

function packageStatus(status: string) {
  return status === "CHANGES_REQUESTED" ? "NEEDS_REVIEW" : status;
}

function exportStatus(status: string) {
  if (status === "READY") return "APPROVED";
  if (status === "FAILED") return "FAILED";
  return "DRAFT";
}

function traceMatchesFilter(trace: DocumentationTraceResponse, filter: TraceFilter) {
  if (filter === "USE_CASE") return Boolean(trace.useCaseId);
  if (filter === "ENTITY") return Boolean(trace.entityId);
  if (filter === "API") return Boolean(trace.apiOperationId);
  return true;
}

function exportStyleSummary(item: DocumentationExportResponse) {
  if (!styledExportFormats.has(item.format)) return "";
  return [item.template, item.theme, item.layout]
    .filter((value): value is DocumentationExportTemplate | DocumentationExportTheme | DocumentationExportLayout => value != null)
    .map(label)
    .join(" · ");
}

function ExportStylePreview({
  template,
  theme,
  layout,
}: {
  template: DocumentationExportTemplate;
  theme: DocumentationExportTheme;
  layout: DocumentationExportLayout;
}) {
  const selectedTemplate = exportTemplates.find((item) => item.value === template) ?? exportTemplates[0];
  const selectedTheme = exportThemes.find((item) => item.value === theme) ?? exportThemes[0];
  const selectedLayout = exportLayouts.find((item) => item.value === layout) ?? exportLayouts[0];
  const verticalSpacing = layout === "PRESENTATION" ? "space-y-4" : layout === "COMPACT" ? "space-y-2" : "space-y-3";
  const titleSize = layout === "PRESENTATION" ? "text-xl" : "text-lg";

  return (
    <section className={`overflow-hidden border bg-background ${selectedTheme.borderClass}`} aria-label={`${selectedTemplate.label} ${selectedTheme.label} export preview`}>
      <div className={`h-1.5 ${selectedTheme.barClass}`} />
      <div className={`border-b px-4 py-3 ${selectedTheme.borderClass} ${selectedTheme.softSurfaceClass}`}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className={`sf-meta font-semibold uppercase tracking-[0.14em] ${selectedTheme.textClass}`}>Export presentation preview</p>
            <h5 className="mt-1 text-sm font-semibold text-foreground">{selectedTemplate.label} document package</h5>
          </div>
          <span className={`border px-2 py-1 text-[0.62rem] font-medium uppercase tracking-[0.08em] ${selectedTheme.borderClass} ${selectedTheme.surfaceClass} ${selectedTheme.textClass}`}>{selectedLayout.label}</span>
        </div>
      </div>

      <div className={`p-4 ${verticalSpacing}`}>
        <div className={`border-l-4 px-3 py-2.5 ${selectedTheme.borderClass} ${selectedTheme.softSurfaceClass}`}>
          <p className={`sf-meta font-semibold uppercase tracking-[0.14em] ${selectedTheme.textClass}`}>Velocira / documentation package</p>
          <p className={`mt-1.5 font-semibold text-foreground ${titleSize}`}>Project implementation brief</p>
          <p className="mt-1 text-xs leading-5 text-foreground-secondary">A cover, structured findings, and visual hand-off built from this package.</p>
        </div>

        <div className="grid gap-3 sm:grid-cols-[minmax(0,1.2fr)_minmax(9rem,0.8fr)]">
          <div>
            <p className={`sf-meta font-semibold uppercase tracking-[0.12em] ${selectedTheme.textClass}`}>01 / Architecture overview</p>
            <div className="mt-2 space-y-1.5">
              <div className="h-2 w-11/12 bg-foreground/75" />
              <div className="h-1.5 w-full bg-foreground-secondary/25" />
              <div className="h-1.5 w-4/5 bg-foreground-secondary/25" />
            </div>
          </div>
          <aside className={`border p-3 ${selectedTheme.borderClass} ${selectedTheme.surfaceClass}`}>
            <p className={`sf-meta font-semibold uppercase tracking-[0.1em] ${selectedTheme.textClass}`}>Implementation signal</p>
            <p className="mt-1.5 text-xs leading-5 text-foreground-secondary">Traceable requirements and delivery risks are called out without relying on color alone.</p>
          </aside>
        </div>

        <div className="grid gap-3 lg:grid-cols-[minmax(0,1.05fr)_minmax(10rem,0.95fr)]">
          <div className="overflow-hidden border border-border">
            <table className="w-full text-left text-[0.68rem]">
              <caption className="sr-only">Preview of a styled traceability table</caption>
              <thead className={selectedTheme.surfaceClass}>
                <tr className={`sf-meta ${selectedTheme.textClass}`}><th className="px-2.5 py-2 font-medium">Requirement</th><th className="px-2.5 py-2 font-medium">Status</th></tr>
              </thead>
              <tbody className="text-foreground-secondary">
                <tr className="border-t border-border"><td className="px-2.5 py-2 font-mono">FR-01</td><td className="px-2.5 py-2">Mapped</td></tr>
                <tr className="border-t border-border"><td className="px-2.5 py-2 font-mono">NFR-04</td><td className="px-2.5 py-2">Reviewed</td></tr>
              </tbody>
            </table>
          </div>
          <figure className={`border p-3 ${selectedTheme.borderClass} ${selectedTheme.softSurfaceClass}`}>
            <svg viewBox="0 0 180 72" role="img" aria-label="Architecture diagram placeholder" className={`h-auto w-full ${selectedTheme.textClass}`}>
              <rect x="8" y="20" width="46" height="28" rx="3" fill="none" stroke="currentColor" strokeWidth="2" />
              <rect x="126" y="20" width="46" height="28" rx="3" fill="none" stroke="currentColor" strokeWidth="2" />
              <path d="M57 34h64m-10-7 10 7-10 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <circle cx="90" cy="34" r="5" fill="currentColor" />
            </svg>
            <figcaption className="mt-1.5 text-xs text-foreground-secondary">Themed diagram treatment</figcaption>
          </figure>
        </div>

        <p className="text-xs leading-5 text-foreground-secondary"><span className="font-medium text-foreground">{selectedLayout.label}:</span> {selectedLayout.description} {selectedTemplate.description}</p>
      </div>
    </section>
  );
}

function TraceLinks({ trace }: { trace: DocumentationTraceResponse }) {
  const links = [
    trace.useCaseId ? { label: "Use case", value: trace.useCaseId, tone: "accent" } : null,
    trace.entityId ? { label: "Entity", value: trace.entityId, tone: "info" } : null,
    trace.apiOperationId ? { label: "API", value: trace.apiOperationId, tone: "warning" } : null,
  ].filter((item): item is { label: string; value: string; tone: "accent" | "info" | "warning" } => Boolean(item));

  if (links.length === 0) return <span className="text-xs text-foreground-secondary">No linked deliverable recorded</span>;

  return <div className="flex flex-wrap gap-1.5">{links.map((link) => <span key={`${link.label}-${link.value}`} className={`border px-1.5 py-0.5 text-[0.62rem] font-medium uppercase tracking-[0.08em] ${link.tone === "accent" ? "border-accent/45 bg-accent-light text-accent" : link.tone === "warning" ? "border-warning/45 bg-warning/10 text-warning" : "border-info/45 bg-info/10 text-info"}`}><span className="sr-only">{link.label}: </span>{link.value}</span>)}</div>;
}

export default function DocumentationPackageWorkspace({ projectId, generationUnlocked, refreshVersion = 0, onUpdated }: Props) {
  const [srsVersions, setSrsVersions] = useState<SrsVersionResponse[]>([]);
  const [packages, setPackages] = useState<DocumentationPackageResponse[]>([]);
  const [exports, setExports] = useState<DocumentationExportResponse[]>([]);
  const [selectedPackageId, setSelectedPackageId] = useState("");
  const [selectedArtifactType, setSelectedArtifactType] = useState<DocumentationArtifactType | "">("");
  const [exportFormat, setExportFormat] = useState<DocumentationExportFormat>("ZIP");
  const [exportTemplate, setExportTemplate] = useState<DocumentationExportTemplate>("EXECUTIVE");
  const [exportTheme, setExportTheme] = useState<DocumentationExportTheme>("SIGNAL");
  const [exportLayout, setExportLayout] = useState<DocumentationExportLayout>("STANDARD");
  const [traceFilter, setTraceFilter] = useState<TraceFilter>("ALL");
  const [traceQuery, setTraceQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { shouldReduceMotion, transition } = useSignalMotion();

  const selectedPackage = useMemo(() => packages.find((item) => item.id === selectedPackageId) ?? packages[0] ?? null, [packages, selectedPackageId]);
  const latestSrs = srsVersions[0] ?? null;
  const selectedArtifact = useMemo(() => selectedPackage?.artifacts.find((artifact) => artifact.type === selectedArtifactType) ?? selectedPackage?.artifacts[0] ?? null, [selectedArtifactType, selectedPackage]);
  const selectedPackageSrs = useMemo(() => selectedPackage ? srsVersions.find((version) => version.id === selectedPackage.srsVersionId) ?? null : null, [selectedPackage, srsVersions]);
  const supportsStyledExport = styledExportFormats.has(exportFormat);
  const validationIssues = selectedPackage?.validation.issues ?? [];
  const artifactValidationIssues = useMemo(() => selectedPackage?.artifacts.flatMap((artifact) => (artifact.validation.issues ?? []).map((issue) => ({ artifact: artifact.title, issue }))) ?? [], [selectedPackage]);
  const filteredTraces = useMemo(() => {
    const query = traceQuery.trim().toLowerCase();
    const traces = selectedPackage?.traceLinks ?? [];
    return traces.filter((trace) => {
      if (!traceMatchesFilter(trace, traceFilter)) return false;
      if (!query) return true;
      return [trace.requirementId, trace.useCaseId, trace.entityId, trace.apiOperationId, trace.acceptanceCriterionId, trace.sourceKind]
        .some((value) => value?.toLowerCase().includes(query));
    });
  }, [selectedPackage, traceFilter, traceQuery]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [srsResult, packageResult] = await Promise.all([srsApi.list(projectId), documentationPackageApi.list(projectId)]);
      if (srsResult.success && srsResult.data) setSrsVersions(srsResult.data);
      if (packageResult.success && packageResult.data) { setPackages(packageResult.data); setSelectedPackageId((current) => current || packageResult.data![0]?.id || ""); }
      setError([srsResult, packageResult].find((result) => !result.success)?.message ?? null);
    } catch (caught) {
      setError(requestErrorMessage(caught, "Unable to load package controls. Please try again."));
    } finally {
      setLoading(false);
    }
  }, [projectId]);
  const loadExports = useCallback(async (packageId: string) => {
    try {
      const result = await documentationPackageApi.exports(projectId, packageId);
      if (result.success && result.data) setExports(result.data); else setError(result.message);
    } catch (caught) {
      setError(requestErrorMessage(caught, "Unable to load package exports. Please try again."));
    }
  }, [projectId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load, refreshVersion]);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (selectedPackage?.id) void loadExports(selectedPackage.id);
      else setExports([]);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadExports, selectedPackage?.id]);

  const generate = async () => {
    if (!latestSrs) return;
    setSubmitting(true); setActiveAction("generate"); setError(null); setSuccessMessage(null);
    try {
      const result = await documentationPackageApi.generate(projectId, latestSrs.id);
      if (result.success && result.data) {
        setPackages((current) => [result.data!, ...current]);
        setSelectedPackageId(result.data.id);
        setSuccessMessage(`Documentation package v${result.data.versionNumber} is ready for review.`);
        onUpdated?.();
      } else setError(result.message);
    } catch (caught) {
      setError(requestErrorMessage(caught, "The documentation package could not be generated. Please try again."));
    } finally {
      setActiveAction(null); setSubmitting(false);
    }
  };
  const createExport = async () => {
    if (!selectedPackage) return;
    setSubmitting(true); setActiveAction("export"); setError(null); setSuccessMessage(null);
    try {
      const request: DocumentationExportRequest = supportsStyledExport
        ? { format: exportFormat, template: exportTemplate, theme: exportTheme, layout: exportLayout }
        : { format: exportFormat };
      const result = await documentationPackageApi.export(projectId, selectedPackage.id, request);
      if (result.success && result.data) {
        setExports((current) => [result.data!, ...current]);
        setSuccessMessage(`${result.data.filename} is ready to download.`);
      } else setError(result.message);
    } catch (caught) {
      setError(requestErrorMessage(caught, "The export could not be created. Please try again."));
    } finally {
      setActiveAction(null); setSubmitting(false);
    }
  };
  const download = async (item: DocumentationExportResponse) => {
    if (!selectedPackage) return;
    setError(null); setSuccessMessage(null);
    try {
      const downloadError = await downloadDocumentationExport(projectId, selectedPackage.id, item.id, item.filename);
      if (downloadError) setError(downloadError);
      else setSuccessMessage(`${item.filename} download started.`);
    } catch (caught) {
      setError(requestErrorMessage(caught, "The export download could not be started. Please try again."));
    }
  };

  if (loading) return <Card className="flex items-center gap-2 py-8 text-sm text-foreground-secondary" role="status" aria-live="polite"><Loader2 className="h-5 w-5 animate-spin text-accent" /> Loading package controls…</Card>;

  const operationLabel = activeAction === "generate" ? "Building package artifacts" : activeAction === "export" ? "Preparing export" : null;

  return (
    <section className="space-y-4" aria-labelledby="package-heading">
      <Card className="border-accent/25 bg-card p-5 sm:p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div><div className="flex items-center gap-2"><Files className="h-5 w-5 text-accent" /><h2 id="package-heading" className="text-xl font-semibold text-foreground">Full documentation package</h2></div><p className="mt-2 max-w-2xl text-sm leading-6 text-foreground-secondary">Generate use cases, ERD, OpenAPI, traceability, and export files in one action. No approval gate is required.</p></div><Button onClick={() => void generate()} disabled={!generationUnlocked || !latestSrs || submitting} loading={submitting && activeAction === "generate"} icon={<Sparkles className="h-4 w-4" />}>Generate package</Button></div>
        {!generationUnlocked && <p className="mt-4 border border-border bg-background-secondary/60 px-3 py-2.5 text-sm text-foreground-secondary">This unlocks automatically when the discovery questions are complete.</p>}
        {generationUnlocked && !latestSrs && <p className="mt-4 border border-border bg-background-secondary/60 px-3 py-2.5 text-sm text-foreground-secondary">Generate the SRS above first; the package uses it as its single source of truth.</p>}
        <AnimatePresence initial={false}>
          {operationLabel && <motion.div key="package-operation" initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex items-center gap-2 border border-accent/25 bg-accent-light px-3 py-2.5 text-sm text-accent" role="status" aria-live="polite"><Loader2 className="h-4 w-4 animate-spin" />{operationLabel}. Existing documents remain available.</motion.div>}
          {successMessage && <motion.div key={successMessage} initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex gap-3 border border-accent/35 bg-accent-light px-3 py-2.5 text-sm text-accent" role="status" aria-live="polite"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" /><p>{successMessage}</p></motion.div>}
          {error && <motion.div key={error} initial={shouldReduceMotion ? false : { opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={shouldReduceMotion ? undefined : { opacity: 0, y: -4 }} transition={transition} className="mt-4 flex gap-3 border border-error/25 bg-error/5 p-3 text-sm text-error" role="alert" aria-live="assertive"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /><div><p className="font-medium">The package action needs attention</p><p className="mt-1 text-error/90">{error}</p><p className="mt-1 text-xs text-error/80">Existing SRS versions, packages, and exports are unchanged.</p></div></motion.div>}
        </AnimatePresence>
      </Card>

      {selectedPackage && <Card className="space-y-5" aria-labelledby="package-review-heading">
        <div className="flex flex-col gap-3 border-b border-border pb-4 lg:flex-row lg:items-center lg:justify-between"><div><div className="flex flex-wrap items-center gap-2"><h3 id="package-review-heading" className="font-semibold text-foreground">Documentation review workspace</h3><StatusBadge status={packageStatus(selectedPackage.status)} label={label(selectedPackage.status)} /></div><p className="sf-meta mt-1 text-foreground-secondary">Package v{selectedPackage.versionNumber} · Generated {formatDate(selectedPackage.generatedAt)}{selectedPackageSrs ? ` · SRS v${selectedPackageSrs.versionNumber}` : ""}</p></div>{packages.length > 1 && <label className="grid gap-1 text-sm text-foreground-secondary"><span className="sf-meta uppercase tracking-wide">Package</span><select value={selectedPackage.id} onChange={(event) => setSelectedPackageId(event.target.value)} className="rounded-md border border-input-border bg-input-bg px-3 py-2 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20">{packages.map((item) => <option key={item.id} value={item.id}>Package v{item.versionNumber}</option>)}</select></label>}</div>

        <div className="grid gap-3 md:grid-cols-3">
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Artifacts</p><p className="mt-2 text-2xl font-semibold text-foreground">{selectedPackage.artifacts.length}</p><p className="mt-1 text-xs text-foreground-secondary">linked generated deliverables</p></div>
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Trace links</p><p className="mt-2 text-2xl font-semibold text-foreground">{selectedPackage.traceLinks.length}</p><p className="mt-1 text-xs text-foreground-secondary">{selectedPackage.validation.traceLinksChecked != null ? `${selectedPackage.validation.traceLinksChecked} checked by validation` : "Validation count not reported"}</p></div>
          <div className="border border-border bg-background-secondary/30 p-3"><p className="sf-meta text-foreground-secondary">Requirements checked</p><p className="mt-2 text-2xl font-semibold text-foreground">{selectedPackage.validation.requirementsChecked ?? "—"}</p><p className={`mt-1 text-xs ${selectedPackage.validation.valid === true ? "text-accent" : validationIssues.length > 0 || artifactValidationIssues.length > 0 ? "text-warning" : "text-foreground-secondary"}`}>{selectedPackage.validation.valid === true ? "Package validation passed" : validationIssues.length + artifactValidationIssues.length > 0 ? `${validationIssues.length + artifactValidationIssues.length} validation issue${validationIssues.length + artifactValidationIssues.length === 1 ? "" : "s"} reported` : "Validation status not reported"}</p></div>
        </div>

        <div className="grid gap-4 border-t border-border pt-5 xl:grid-cols-[minmax(12rem,0.55fr)_minmax(0,1.45fr)]">
          <div>
            <div className="mb-3 flex items-baseline justify-between gap-2">
              <h4 className="text-sm font-semibold text-foreground">Generated artifacts</h4>
              <span className="sf-meta text-foreground-secondary">{selectedPackage.artifacts.length} files</span>
            </div>
            <div role="group" aria-label="Generated documentation artifacts" className="flex gap-2 overflow-x-auto pb-1 xl:grid xl:overflow-visible">
              {selectedPackage.artifacts.map((artifact) => {
                const selected = selectedArtifact?.type === artifact.type;
                return (
                  <button
                    key={artifact.type}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => setSelectedArtifactType(artifact.type)}
                    className={`min-w-max border px-3 py-2 text-left text-sm transition-colors duration-150 focus-ring xl:min-w-0 ${selected ? "border-accent/55 bg-accent-light text-accent" : "border-border bg-background-secondary/30 text-foreground-secondary hover:border-accent/40 hover:text-foreground"}`}
                  >
                    <span className="block font-medium">{artifact.title}</span>
                    <span className="sf-meta mt-1 block text-[0.6rem] opacity-80">{artifact.type}</span>
                  </button>
                );
              })}
            </div>
          </div>
          {selectedArtifact ? (
            <section aria-label={`${selectedArtifact.title} preview`} className="border border-border bg-background-secondary/25">
              <div className="flex flex-col gap-2 border-b border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div><p className="text-sm font-semibold text-foreground">{selectedArtifact.title}</p><p className="sf-meta mt-1 text-foreground-secondary">{selectedArtifact.sourceFormat} · Checksum {selectedArtifact.checksum}</p></div>
                <span className={`border px-2 py-1 text-[0.62rem] font-medium uppercase tracking-[0.08em] ${selectedArtifact.validation.valid === true ? "border-accent/45 bg-accent-light text-accent" : (selectedArtifact.validation.issues?.length ?? 0) > 0 ? "border-warning/45 bg-warning/10 text-warning" : "border-border text-foreground-secondary"}`}>{selectedArtifact.validation.valid === true ? "Validated" : (selectedArtifact.validation.issues?.length ?? 0) > 0 ? "Review findings" : "Validation not reported"}</span>
              </div>
              <pre className="max-h-96 overflow-auto whitespace-pre-wrap px-4 py-4 font-mono text-xs leading-5 text-foreground-secondary">{selectedArtifact.content}</pre>
              {(selectedArtifact.validation.issues?.length ?? 0) > 0 && <div className="border-t border-border px-4 py-3"><p className="sf-meta text-warning">Artifact findings</p><ul className="mt-2 space-y-1 text-sm text-foreground-secondary">{selectedArtifact.validation.issues!.map((issue, index) => <li key={`${issue}-${index}`}>{issue}</li>)}</ul></div>}
            </section>
          ) : (
            <div className="border border-dashed border-border px-4 py-10 text-center text-sm text-foreground-secondary">No artifact preview is available for this package.</div>
          )}
        </div>

        <section className="border-t border-border pt-5" aria-labelledby="traceability-heading">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between"><div><div className="flex items-center gap-2"><GitBranch className="h-4 w-4 text-accent" /><h4 id="traceability-heading" className="text-sm font-semibold text-foreground">Traceability matrix</h4></div><p className="mt-1 text-sm text-foreground-secondary">Inspect each recorded requirement-to-deliverable relationship.</p></div><label className="relative block lg:w-72"><span className="sr-only">Filter trace links</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-secondary" /><input value={traceQuery} onChange={(event) => setTraceQuery(event.target.value)} placeholder="Filter requirements or IDs" className="w-full border border-input-border bg-input-bg py-2 pl-9 pr-3 text-sm text-foreground placeholder:text-foreground-secondary focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20" /></label></div>
          <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Traceability filters">{(["ALL", "USE_CASE", "ENTITY", "API"] as TraceFilter[]).map((filter) => <button key={filter} type="button" onClick={() => setTraceFilter(filter)} aria-pressed={traceFilter === filter} className={`border px-2.5 py-1.5 text-xs font-medium transition-colors duration-150 focus-ring ${traceFilter === filter ? "border-accent/55 bg-accent-light text-accent" : "border-border text-foreground-secondary hover:border-accent/40 hover:text-foreground"}`}>{filter === "ALL" ? "All links" : label(filter)}</button>)}</div>
          <p className="sf-meta mt-3 text-foreground-secondary">{filteredTraces.length} of {selectedPackage.traceLinks.length} recorded trace links shown</p>
          {filteredTraces.length > 0 ? <><div className="mt-3 hidden overflow-x-auto border border-border md:block"><table className="w-full min-w-[48rem] text-left text-sm"><thead className="border-b border-border bg-background-secondary/60"><tr className="sf-meta text-foreground-secondary"><th className="px-3 py-2.5 font-medium">Requirement</th><th className="px-3 py-2.5 font-medium">Linked deliverables</th><th className="px-3 py-2.5 font-medium">Acceptance</th><th className="px-3 py-2.5 font-medium">Source</th></tr></thead><tbody>{filteredTraces.map((trace, index) => <tr key={`${trace.requirementId}-${trace.acceptanceCriterionId}-${index}`} className="border-b border-border last:border-0"><td className="px-3 py-3 font-mono text-xs text-foreground">{trace.requirementId}</td><td className="px-3 py-3"><TraceLinks trace={trace} /></td><td className="px-3 py-3 font-mono text-xs text-foreground-secondary">{trace.acceptanceCriterionId}</td><td className="px-3 py-3"><span className="border border-border px-1.5 py-0.5 text-[0.62rem] font-medium uppercase tracking-[0.08em] text-foreground-secondary">{label(trace.sourceKind)}</span></td></tr>)}</tbody></table></div><div className="mt-3 space-y-2 md:hidden">{filteredTraces.map((trace, index) => <article key={`${trace.requirementId}-${trace.acceptanceCriterionId}-${index}`} className="border border-border bg-background-secondary/25 p-3"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-mono text-xs text-foreground">{trace.requirementId}</span><span className="border border-border px-1.5 py-0.5 text-[0.62rem] font-medium uppercase tracking-[0.08em] text-foreground-secondary">{label(trace.sourceKind)}</span></div><div className="mt-3"><TraceLinks trace={trace} /></div><p className="sf-meta mt-3 text-foreground-secondary">Acceptance · {trace.acceptanceCriterionId}</p></article>)}</div></> : <div className="mt-3 border border-dashed border-border px-4 py-9 text-center"><GitBranch className="mx-auto h-5 w-5 text-accent" /><p className="mt-3 text-sm font-medium text-foreground">No trace links match this view.</p><p className="mt-1 text-sm text-foreground-secondary">Adjust the filter to inspect the relationships recorded in this package.</p></div>}
        </section>

        <section className="grid gap-4 border-t border-border pt-5 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,0.62fr)]" aria-labelledby="review-signals-heading">
          <div><h4 id="review-signals-heading" className="text-sm font-semibold text-foreground">Review signals</h4><p className="mt-1 text-sm text-foreground-secondary">Validation findings stay attached to the package and its artifacts.</p>{validationIssues.length + artifactValidationIssues.length > 0 ? <ul className="mt-4 space-y-2">{validationIssues.map((issue, index) => <li key={`package-${issue}-${index}`} className="border-l-2 border-warning pl-3 text-sm text-foreground-secondary"><span className="font-medium text-foreground">Package:</span> {issue}</li>)}{artifactValidationIssues.map(({ artifact, issue }, index) => <li key={`${artifact}-${issue}-${index}`} className="border-l-2 border-warning pl-3 text-sm text-foreground-secondary"><span className="font-medium text-foreground">{artifact}:</span> {issue}</li>)}</ul> : <div className="mt-4 flex items-center gap-2 border border-accent/25 bg-accent-light px-3 py-2.5 text-sm text-accent"><CheckCircle2 className="h-4 w-4" />No validation issues reported by this package or its artifacts.</div>}</div>
          <div className="border border-border bg-background-secondary/25 p-4"><p className="sf-meta text-foreground-secondary">Review record</p><div className="mt-3 space-y-3 text-sm"><div><p className="text-foreground-secondary">Approved</p><p className="mt-1 text-foreground">{formatDate(selectedPackage.approvedAt)}</p></div><div><p className="text-foreground-secondary">Validation status</p><p className={`mt-1 font-medium ${selectedPackage.validation.valid === true ? "text-accent" : validationIssues.length + artifactValidationIssues.length > 0 ? "text-warning" : "text-foreground"}`}>{selectedPackage.validation.valid === true ? "Passed" : validationIssues.length + artifactValidationIssues.length > 0 ? "Findings reported" : "Not reported"}</p></div></div></div>
        </section>

        <section className="border-t border-border pt-5" aria-labelledby="exports-heading">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
            <div className="max-w-2xl">
              <h4 id="exports-heading" className="text-sm font-semibold text-foreground">Export package</h4>
              <p className="mt-1 text-sm leading-6 text-foreground-secondary">Create a polished document package or download a faithful source artifact from this reviewed package.</p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <label className="grid min-w-52 gap-1 text-sm text-foreground-secondary">
                <span className="sf-meta uppercase tracking-wide">Export format</span>
                <select value={exportFormat} onChange={(event) => setExportFormat(event.target.value as DocumentationExportFormat)} disabled={submitting} className="border border-input-border bg-input-bg px-3 py-2.5 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20">
                  {exportFormats.map((format) => <option key={format.value} value={format.value}>{format.label}</option>)}
                </select>
              </label>
            </div>
          </div>

          <fieldset disabled={!supportsStyledExport || submitting} aria-describedby="export-style-guidance" className={`mt-4 border p-4 ${supportsStyledExport ? "border-accent/30 bg-background-secondary/20" : "border-border bg-background-secondary/15"}`}>
            <legend className="px-1 text-sm font-semibold text-foreground">Document presentation</legend>
            <p id="export-style-guidance" className="mt-1 text-sm leading-5 text-foreground-secondary">{supportsStyledExport ? "Choose a template, color theme, and layout for the generated document package." : "Presentation choices apply to PDF, Word, and complete ZIP exports. Source artifacts remain direct, faithful downloads."}</p>
            <div className="mt-4 grid gap-3 md:grid-cols-3">
              <label className="grid gap-1 text-sm text-foreground-secondary">
                <span className="sf-meta uppercase tracking-wide">Template</span>
                <select value={exportTemplate} onChange={(event) => setExportTemplate(event.target.value as DocumentationExportTemplate)} className="border border-input-border bg-input-bg px-3 py-2.5 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:cursor-not-allowed disabled:opacity-55">
                  {exportTemplates.map((template) => <option key={template.value} value={template.value}>{template.label}</option>)}
                </select>
              </label>
              <label className="grid gap-1 text-sm text-foreground-secondary">
                <span className="sf-meta uppercase tracking-wide">Color theme</span>
                <select value={exportTheme} onChange={(event) => setExportTheme(event.target.value as DocumentationExportTheme)} className="border border-input-border bg-input-bg px-3 py-2.5 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:cursor-not-allowed disabled:opacity-55">
                  {exportThemes.map((theme) => <option key={theme.value} value={theme.value}>{theme.label}</option>)}
                </select>
              </label>
              <label className="grid gap-1 text-sm text-foreground-secondary">
                <span className="sf-meta uppercase tracking-wide">Layout</span>
                <select value={exportLayout} onChange={(event) => setExportLayout(event.target.value as DocumentationExportLayout)} className="border border-input-border bg-input-bg px-3 py-2.5 text-sm text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20 disabled:cursor-not-allowed disabled:opacity-55">
                  {exportLayouts.map((layout) => <option key={layout.value} value={layout.value}>{layout.label}</option>)}
                </select>
              </label>
            </div>
          </fieldset>

          {supportsStyledExport ? (
            <div className="mt-4">
              <ExportStylePreview template={exportTemplate} theme={exportTheme} layout={exportLayout} />
            </div>
          ) : (
            <div className="mt-4 flex gap-3 border border-dashed border-border bg-background-secondary/15 px-4 py-3 text-sm text-foreground-secondary" role="status">
              <Files className="mt-0.5 h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
              <p><span className="font-medium text-foreground">Direct source export.</span> This format preserves the original source exactly; no template, theme, or layout settings will be sent.</p>
            </div>
          )}

          <div className="mt-4 flex justify-end">
            <Button onClick={() => void createExport()} variant="outline" disabled={submitting} loading={submitting && activeAction === "export"} icon={<FileDown className="h-4 w-4" />}>Create export</Button>
          </div>

          <div className="mt-5">
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <h5 className="text-sm font-semibold text-foreground">Export register</h5>
              <span className="sf-meta text-foreground-secondary">{exports.length} file{exports.length === 1 ? "" : "s"}</span>
            </div>
            {exports.length > 0 ? (
              <div className="space-y-2">
                {exports.map((item) => {
                  const styleSummary = exportStyleSummary(item);
                  return (
                    <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 border border-border px-3 py-3">
                      <div className="min-w-0">
                        <p className="truncate font-mono text-xs text-foreground">{item.filename}</p>
                        <p className="sf-meta mt-1 text-foreground-secondary">{label(item.format)} · {formatBytes(item.byteSize)} · {formatDate(item.completedAt ?? item.createdAt)}{styleSummary ? <span className="text-foreground"> · {styleSummary}</span> : null}</p>
                      </div>
                      <div className="flex items-center gap-2"><StatusBadge status={exportStatus(item.status)} label={label(item.status)} /><Button size="sm" variant="outline" onClick={() => void download(item)} icon={<Download className="h-4 w-4" />}>Download</Button></div>
                    </div>
                  );
                })}
              </div>
            ) : <p className="border border-dashed border-border px-3 py-6 text-sm text-foreground-secondary">No exports have been created for this package.</p>}
          </div>
        </section>
      </Card>}
    </section>
  );
}
