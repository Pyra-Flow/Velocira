import { FileText, Gauge, Layers3, TimerReset } from "lucide-react";
import { formatRelativeTime } from "@/lib/utils";
import type { ProjectResponse } from "@/lib/api";
import SignalMeter from "@/components/ui/SignalMeter";
import StatusBadge from "@/components/ui/StatusBadge";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";

const nextAction: Record<ProjectResponse["status"], string> = {
  DRAFT: "Add the context that lets discovery begin.",
  DISCOVERY: "Continue the active discovery question.",
  READY_FOR_GENERATION: "Generate the SRS from the confirmed brief.",
  GENERATING: "Generation is running; keep this workspace open for results.",
  NEEDS_REVIEW: "Review requirements and resolve the remaining evidence gaps.",
  APPROVED: "The documentation package is ready to share or export.",
  FAILED: "Review the failed generation notice, then safely retry.",
  ARCHIVED: "This project is archived and remains available for reference.",
};

type Props = {
  project: ProjectResponse;
  className?: string;
};

export default function ProjectHealthPanel({ project, className }: Props) {
  const tone = project.status === "FAILED" ? "error" : project.status === "NEEDS_REVIEW" ? "warning" : "accent";

  return (
    <aside className={`sf-project-health ${className ?? ""}`.trim()} aria-label="Project health summary">
      <SignalForgeVisual name="traceability" decorative className="sf-project-health__visual" />
      <div className="sf-project-health__content">
        <div className="sf-project-health__title">
          <div><p>Project health</p><h2>Evidence, status, and the next move.</h2></div>
          <StatusBadge status={project.status} />
        </div>
        <SignalMeter value={project.progress} label="Workspace readiness" tone={tone} />
        <dl className="sf-project-health__metrics">
          <div><dt><FileText aria-hidden="true" />Linked documents</dt><dd>{project.documentCount}</dd></div>
          <div><dt><Layers3 aria-hidden="true" />Project type</dt><dd>{project.type.replaceAll("_", " ")}</dd></div>
          <div><dt><TimerReset aria-hidden="true" />Last signal</dt><dd>{formatRelativeTime(project.updatedAt)}</dd></div>
        </dl>
        <div className="sf-project-health__next"><Gauge aria-hidden="true" /><div><span>Next action</span><p>{nextAction[project.status]}</p></div></div>
      </div>
    </aside>
  );
}
