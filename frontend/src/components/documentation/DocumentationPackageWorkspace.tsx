"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Braces,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Database,
  Download,
  FileCode2,
  FileText,
  Files,
  GitBranch,
  Image as ImageIcon,
  Loader2,
  Maximize2,
  Network,
  PackageOpen,
  PanelTopOpen,
  Sparkles,
} from "lucide-react";
import styles from "./DocumentationPackageWorkspace.module.css";
import {
  documentationPackageApi,
  downloadDocumentationExport,
  fetchDocumentationArtifactPreview,
  srsApi,
  type DocumentationArtifactResponse,
  type DocumentationArtifactType,
  type DocumentationExportFormat,
  type DocumentationExportResponse,
  type DocumentationPackageResponse,
  type SrsVersionResponse,
} from "@/lib/api";

type Props = { projectId: string; generationUnlocked: boolean; refreshVersion?: number; onUpdated?: () => void };
type ActiveAction = "generate" | "export" | "preview" | null;
type WorkspaceMode = "atlas" | "handoff";

const artifactDetails: Record<DocumentationArtifactType, { label: string; group: "documents" | "visuals" | "sources"; format: string; Icon: typeof FileText }> = {
  SRS: { label: "Software requirements", group: "documents", format: "DOCX", Icon: FileText },
  USE_CASES: { label: "Use case map", group: "visuals", format: "SVG", Icon: Network },
  ERD: { label: "Entity relationship diagram", group: "visuals", format: "SVG", Icon: Database },
  OPENAPI: { label: "OpenAPI contract", group: "sources", format: "JSON", Icon: Braces },
  TRACEABILITY: { label: "Traceability matrix", group: "sources", format: "MD", Icon: GitBranch },
};

const groups: Array<{ key: "documents" | "visuals" | "sources"; label: string }> = [
  { key: "documents", label: "Core documents" },
  { key: "visuals", label: "Visuals" },
  { key: "sources", label: "Technical sources" },
];

function isDiagram(type: DocumentationArtifactType | ""): type is "USE_CASES" | "ERD" {
  return type === "USE_CASES" || type === "ERD";
}

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(value: string | null | undefined) {
  if (!value) return "Not recorded";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(date);
}

function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function sourceFor(artifact: DocumentationArtifactResponse) {
  return artifact.sourceContent?.trim() || artifact.content?.trim() || "No source content is available for this artifact.";
}

function previewLines(artifact: DocumentationArtifactResponse) {
  let inCode = false;
  return sourceFor(artifact).split(/\r?\n/).slice(0, 120).flatMap((raw, index) => {
    if (raw.startsWith("```")) { inCode = !inCode; return []; }
    return [{ raw, index, inCode }];
  }).filter(({ raw, index }) => raw.trim() || index < 48);
}

function DocumentTextPreview({ artifact }: { artifact: DocumentationArtifactResponse }) {
  const visibleLines = previewLines(artifact);
  return (
    <div className={styles.documentBody} aria-label={`${artifact.title} document preview`}>
      <div className={styles.documentIdentity}>
        <span>Package atlas</span>
        <span>{artifact.sourceFormat}</span>
      </div>
      {visibleLines.map(({ raw, index, inCode }) => {
        const heading = /^(#{1,3})\s+(.+)$/.exec(raw);
        if (heading) {
          const level = heading[1].length;
          const Tag = level === 1 ? "h2" : level === 2 ? "h3" : "h4";
          return <Tag key={`${raw}-${index}`} className={level === 1 ? styles.documentTitle : styles.documentHeading}>{heading[2]}</Tag>;
        }
        if (/^\s*[-*]\s+/.test(raw)) return <p key={`${raw}-${index}`} className={styles.documentBullet}>{raw.replace(/^\s*[-*]\s+/, "")}</p>;
        if (!raw.trim()) return <div key={`space-${index}`} className={styles.documentGap} />;
        return <p key={`${raw}-${index}`} className={inCode ? styles.documentCode : styles.documentParagraph}>{raw}</p>;
      })}
    </div>
  );
}

function ArtifactIcon({ type, className }: { type: DocumentationArtifactType; className?: string }) {
  const Icon = artifactDetails[type].Icon;
  return <Icon className={className} aria-hidden="true" />;
}

export default function DocumentationPackageWorkspace({ projectId, generationUnlocked, refreshVersion = 0, onUpdated }: Props) {
  const [srsVersions, setSrsVersions] = useState<SrsVersionResponse[]>([]);
  const [packages, setPackages] = useState<DocumentationPackageResponse[]>([]);
  const [exports, setExports] = useState<DocumentationExportResponse[]>([]);
  const [selectedPackageId, setSelectedPackageId] = useState("");
  const [selectedArtifactType, setSelectedArtifactType] = useState<DocumentationArtifactType | "">("SRS");
  const [mode, setMode] = useState<WorkspaceMode>("atlas");
  const [selectedTraceIndex, setSelectedTraceIndex] = useState(0);
  const [selectedDiagramUrl, setSelectedDiagramUrl] = useState<string | null>(null);
  const [handoffDiagramUrl, setHandoffDiagramUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedPackage = useMemo(() => packages.find((item) => item.id === selectedPackageId) ?? packages[0] ?? null, [packages, selectedPackageId]);
  const latestSrs = srsVersions[0] ?? null;
  const selectedArtifact = useMemo(() => {
    if (!selectedPackage) return null;
    return selectedPackage.artifacts.find((artifact) => artifact.type === selectedArtifactType)
      ?? selectedPackage.artifacts.find((artifact) => artifact.type === "SRS")
      ?? selectedPackage.artifacts[0]
      ?? null;
  }, [selectedArtifactType, selectedPackage]);
  const selectedTrace = selectedPackage?.traceLinks[selectedTraceIndex] ?? selectedPackage?.traceLinks[0] ?? null;
  const selectedDetail = selectedArtifact ? artifactDetails[selectedArtifact.type] : null;
  const selectedDiagramType = selectedArtifact && isDiagram(selectedArtifact.type) ? selectedArtifact.type : null;
  const selectedExport = exports.find((item) => item.format === "PDF") ?? exports.find((item) => item.format === "DOCX") ?? exports[0] ?? null;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [srsResult, packageResult] = await Promise.all([srsApi.list(projectId), documentationPackageApi.list(projectId)]);
      if (srsResult.success && srsResult.data) setSrsVersions(srsResult.data);
      if (packageResult.success && packageResult.data) {
        setPackages(packageResult.data);
        setSelectedPackageId((current) => current || packageResult.data?.[0]?.id || "");
      }
      const failed = [srsResult, packageResult].find((result) => !result.success);
      if (failed) setError(failed.message);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load the documentation package.");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  const loadExports = useCallback(async (packageId: string) => {
    const result = await documentationPackageApi.exports(projectId, packageId);
    if (result.success && result.data) setExports(result.data);
    else if (!result.success) setError(result.message);
  }, [projectId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load, refreshVersion]);
  useEffect(() => {
    if (!selectedPackage?.id) return;
    const timer = window.setTimeout(() => { void loadExports(selectedPackage.id); }, 0);
    return () => window.clearTimeout(timer);
  }, [loadExports, selectedPackage?.id]);

  useEffect(() => {
    if (!selectedPackage || !selectedArtifact || !isDiagram(selectedArtifact.type)) return;
    let active = true;
    let createdUrl: string | null = null;
    void fetchDocumentationArtifactPreview(projectId, selectedPackage.id, selectedArtifact.type).then((result) => {
      if (!active) { if ("url" in result) URL.revokeObjectURL(result.url); return; }
      if ("error" in result) setError(result.error);
      else { createdUrl = result.url; setSelectedDiagramUrl(result.url); }
    });
    return () => { active = false; if (createdUrl) URL.revokeObjectURL(createdUrl); };
  }, [projectId, selectedArtifact, selectedPackage]);

  useEffect(() => {
    if (!selectedPackage || !selectedPackage.artifacts.some((artifact) => artifact.type === "ERD")) return;
    let active = true;
    let createdUrl: string | null = null;
    void fetchDocumentationArtifactPreview(projectId, selectedPackage.id, "ERD").then((result) => {
      if (!active) { if ("url" in result) URL.revokeObjectURL(result.url); return; }
      if ("error" in result) setError(result.error);
      else { createdUrl = result.url; setHandoffDiagramUrl(result.url); }
    });
    return () => { active = false; if (createdUrl) URL.revokeObjectURL(createdUrl); };
  }, [projectId, selectedPackage]);

  const generate = async () => {
    if (!latestSrs) return;
    setActiveAction("generate"); setError(null); setNotice(null);
    try {
      const result = await documentationPackageApi.generate(projectId, latestSrs.id);
      if (!result.success || !result.data) { setError(result.message); return; }
      setPackages((current) => [result.data!, ...current]);
      setSelectedPackageId(result.data.id);
      setSelectedArtifactType("SRS");
      setNotice(`Package v${result.data.versionNumber} is ready to inspect.`);
      onUpdated?.();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "The package could not be generated."); }
    finally { setActiveAction(null); }
  };

  const createAndDownload = async (format: DocumentationExportFormat) => {
    if (!selectedPackage) return;
    setActiveAction("export"); setError(null); setNotice(null);
    try {
      const result = await documentationPackageApi.export(projectId, selectedPackage.id, {
        format, template: "TECHNICAL", theme: "COMMAND", layout: "STANDARD",
      });
      if (!result.success || !result.data) { setError(result.message); return; }
      setExports((current) => [result.data!, ...current]);
      const downloadError = await downloadDocumentationExport(projectId, selectedPackage.id, result.data.id, result.data.filename);
      if (downloadError) setError(downloadError);
      else setNotice(`${result.data.filename} is ready.`);
    } catch (caught) { setError(caught instanceof Error ? caught.message : "The export could not be created."); }
    finally { setActiveAction(null); }
  };

  const downloadDiagram = async (type: "USE_CASES" | "ERD") => {
    if (!selectedPackage) return;
    setActiveAction("preview"); setError(null); setNotice(null);
    const result = await fetchDocumentationArtifactPreview(projectId, selectedPackage.id, type);
    if ("error" in result) { setError(result.error); setActiveAction(null); return; }
    const link = document.createElement("a");
    link.href = result.url; link.download = result.filename; document.body.appendChild(link); link.click(); link.remove();
    window.setTimeout(() => URL.revokeObjectURL(result.url), 0);
    setNotice(`${result.filename} is ready.`); setActiveAction(null);
  };

  if (loading) return <section className={styles.loading} role="status"><Loader2 aria-hidden="true" /> Loading documentation package…</section>;

  if (!selectedPackage) {
    return (
      <section className={styles.empty} aria-labelledby="package-heading">
        <PackageOpen aria-hidden="true" />
        <div><p>Documents / package atlas</p><h2 id="package-heading">No package is ready to inspect.</h2><span>Generate the reviewed package to open its documents, diagrams, and handoff files here.</span></div>
        <button type="button" onClick={() => void generate()} disabled={!generationUnlocked || !latestSrs || activeAction === "generate"}><Sparkles aria-hidden="true" /> Generate package</button>
      </section>
    );
  }

  const requirementsChecked = selectedPackage.validation.requirementsChecked ?? selectedPackage.traceLinks.length;
  const valid = selectedPackage.validation.valid !== false;

  return (
    <section className={styles.commandCenter} aria-labelledby="package-heading">
      <header className={styles.header}>
        <div className={styles.headerTitle}><Files aria-hidden="true" /><div><p>Package atlas</p><h2 id="package-heading">Documentation package <span>v{selectedPackage.versionNumber}</span></h2></div></div>
        <div className={styles.headerMeta}><span className={valid ? styles.validated : styles.needsReview}><CheckCircle2 aria-hidden="true" /> {valid ? "All deliverables validated" : "Review findings recorded"}</span><span><strong>{requirementsChecked}</strong> linked requirements</span></div>
        <div className={styles.modeSwitch} role="group" aria-label="Documentation view"><button type="button" aria-pressed={mode === "atlas"} onClick={() => setMode("atlas")}>Documents</button><button type="button" aria-pressed={mode === "handoff"} onClick={() => setMode("handoff")}>Traceable handoff</button></div>
      </header>

      {(notice || error || activeAction) && <div className={error ? styles.alertError : styles.alert} role={error ? "alert" : "status"}>{activeAction && <Loader2 aria-hidden="true" className={styles.spin} />}{error ?? notice ?? "Preparing documentation artifact…"}</div>}

      {mode === "atlas" ? (
        <div className={styles.atlasLayout}>
          <nav className={styles.documentMap} aria-label="Generated deliverables">
            <div className={styles.mapHeading}><span>Document map</span><span>{selectedPackage.artifacts.length}</span></div>
            {groups.map((group) => {
              const artifacts = selectedPackage.artifacts.filter((artifact) => artifactDetails[artifact.type].group === group.key);
              if (!artifacts.length) return null;
              return <section key={group.key} className={styles.mapGroup}><h3>{group.label}</h3>{artifacts.map((artifact) => {
                const details = artifactDetails[artifact.type]; const active = selectedArtifact?.type === artifact.type;
                return <button key={artifact.type} type="button" aria-pressed={active} className={active ? styles.mapRowActive : styles.mapRow} onClick={() => setSelectedArtifactType(artifact.type)}><ArtifactIcon type={artifact.type} /><span>{details.label}</span><small>{details.format}</small><Check aria-label={artifact.validation.valid === false ? "Needs review" : "Validated"} /></button>;
              })}</section>;
            })}
            <div className={styles.mapFooter}><span>Package status</span><strong>{label(selectedPackage.status)}</strong><span>Generated {formatDate(selectedPackage.generatedAt)}</span></div>
          </nav>

          <article className={styles.viewer} aria-label="Selected document viewer">
            <div className={styles.viewerHeader}><div><ArtifactIcon type={selectedArtifact!.type} /><span>{selectedDetail!.label}</span><small>{selectedDetail!.format}</small></div><button type="button" title="Open traceable handoff" onClick={() => setMode("handoff")}><Maximize2 aria-hidden="true" /> Trace links</button></div>
            <div className={styles.viewerCanvas}>
              {selectedArtifact && isDiagram(selectedArtifact.type) ? (
                selectedDiagramUrl ? <img src={selectedDiagramUrl} alt={`${selectedDetail?.label} generated from this package`} className={styles.diagramPreview} /> : <div className={styles.previewLoading}><Loader2 className={styles.spin} /> Rendering package diagram…</div>
              ) : selectedArtifact ? <DocumentTextPreview artifact={selectedArtifact} /> : null}
            </div>
            <div className={styles.viewerFooter}><span>{isDiagram(selectedArtifact?.type ?? "") ? "SVG generated from the reviewed package snapshot" : "Preview rendered from the same reviewed source snapshot used by package exports"}</span><div><button type="button" aria-label="Previous trace link" onClick={() => setSelectedTraceIndex((index) => Math.max(0, index - 1))} disabled={selectedTraceIndex === 0}><ChevronLeft /></button><span>{Math.min(selectedTraceIndex + 1, Math.max(1, selectedPackage.traceLinks.length))} / {selectedPackage.traceLinks.length || 1}</span><button type="button" aria-label="Next trace link" onClick={() => setSelectedTraceIndex((index) => Math.min(Math.max(0, selectedPackage.traceLinks.length - 1), index + 1))} disabled={selectedTraceIndex >= selectedPackage.traceLinks.length - 1}><ChevronRight /></button></div></div>
          </article>

          <aside className={styles.inspector} aria-label="Document inspector">
            <div className={styles.inspectorTitle}><PanelTopOpen aria-hidden="true" /><div><span>Document inspector</span><strong>{selectedDetail?.label}</strong><small>{selectedDetail?.format} artifact</small></div></div>
            <dl className={styles.metadata}><div><dt>Package</dt><dd>v{selectedPackage.versionNumber}</dd></div><div><dt>Status</dt><dd className={valid ? styles.validated : styles.needsReview}>{valid ? "Validated" : "Review needed"}</dd></div><div><dt>Generated</dt><dd>{formatDate(selectedPackage.generatedAt)}</dd></div><div><dt>Source</dt><dd>{selectedArtifact?.sourceFormat}</dd></div><div><dt>Checksum</dt><dd title={selectedArtifact?.checksum}>{selectedArtifact?.checksum.slice(0, 12)}…</dd></div></dl>
            <section className={styles.linkedRequirements}><h3>Linked requirements</h3>{selectedPackage.traceLinks.slice(0, 5).map((trace) => <button key={trace.requirementId} type="button" onClick={() => { setSelectedTraceIndex(selectedPackage.traceLinks.indexOf(trace)); setMode("handoff"); }}><span>{trace.requirementId}</span><small>{trace.acceptanceCriterionId}</small></button>)}{selectedPackage.traceLinks.length === 0 && <p>No trace links were recorded.</p>}</section>
            <section className={styles.actions}><h3>Actions</h3><button type="button" onClick={() => void createAndDownload("PDF")} disabled={activeAction !== null}><Download aria-hidden="true" /> Download PDF</button><button type="button" onClick={() => void createAndDownload("DOCX")} disabled={activeAction !== null}><FileText aria-hidden="true" /> Download Word</button>{selectedDiagramType ? <button type="button" onClick={() => void downloadDiagram(selectedDiagramType)} disabled={activeAction !== null}><ImageIcon aria-hidden="true" /> Download SVG</button> : <button type="button" onClick={() => { const diagram = selectedPackage.artifacts.find((artifact) => artifact.type === "ERD") ?? selectedPackage.artifacts.find((artifact) => artifact.type === "USE_CASES"); if (diagram) setSelectedArtifactType(diagram.type); }}><Network aria-hidden="true" /> Open diagram</button>}</section>
            {selectedExport && <p className={styles.exportRecord}>Latest export · {selectedExport.filename} · {formatBytes(selectedExport.byteSize)}</p>}
          </aside>
        </div>
      ) : (
        <div className={styles.handoffLayout}>
          <aside className={styles.handoffLibrary} aria-label="Handoff deliverables"><h3>Generated deliverables</h3>{selectedPackage.artifacts.map((artifact) => <button key={artifact.type} type="button" className={selectedArtifact?.type === artifact.type ? styles.handoffRowActive : styles.handoffRow} onClick={() => { setSelectedArtifactType(artifact.type); setMode("atlas"); }}><ArtifactIcon type={artifact.type} /><span><strong>{artifactDetails[artifact.type].label}</strong><small>{artifactDetails[artifact.type].format} · {artifact.validation.valid === false ? "Needs review" : "Validated"}</small></span><Check /></button>)}<p>{selectedPackage.artifacts.length} of {selectedPackage.artifacts.length} deliverables</p></aside>
          <article className={styles.traceContent}><div className={styles.traceHeading}><GitBranch /><div><span>Traceable handoff</span><h3>{selectedTrace?.requirementId ?? "No trace link selected"}</h3></div></div>{selectedTrace ? <><div className={styles.requirementCallout}><span>{selectedTrace.requirementId}</span><strong>{selectedTrace.acceptanceCriterionId}</strong><p>Requirement evidence remains connected to its generated document and diagram artifacts.</p></div><DocumentTextPreview artifact={selectedPackage.artifacts.find((artifact) => artifact.type === "SRS") ?? selectedArtifact!} /></> : <p className={styles.noTrace}>No traceability data is available for this package.</p>}</article>
          <aside className={styles.linkedArtifacts}><section><h3><Network /> Linked architecture diagram</h3>{selectedPackage.artifacts.some((artifact) => artifact.type === "ERD") && handoffDiagramUrl ? <img src={handoffDiagramUrl} alt="Entity relationship diagram generated from the package" /> : <div className={styles.previewLoading}><Loader2 className={styles.spin} /> Rendering diagram…</div>}<footer>{selectedPackage.artifacts.find((artifact) => artifact.type === "ERD")?.title ?? "No diagram available"} <span>SVG</span></footer></section><section><h3><FileCode2 /> Linked implementation section</h3><div className={styles.implementationNote}><span>{selectedTrace?.apiOperationId ?? "Package record"}</span><p>The linked source artifact is generated from this package’s reviewed requirements and can be downloaded from the document inspector.</p></div></section></aside>
          <footer className={styles.bundle}><div><span>Handoff bundle ({selectedPackage.artifacts.length} core artifacts)</span><p>SRS, diagrams, OpenAPI, traceability, PDF, and Word derive from the same reviewed package snapshot.</p></div><button type="button" onClick={() => void createAndDownload("ZIP")} disabled={activeAction !== null}><PackageOpen /> Download handoff bundle</button></footer>
        </div>
      )}
    </section>
  );
}
