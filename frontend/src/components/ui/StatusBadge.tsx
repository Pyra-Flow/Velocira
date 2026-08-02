import { CheckCircle2, CircleDashed, Clock3, AlertTriangle, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

type StatusTone = "muted" | "info" | "ready" | "review" | "blocked";

const toneByStatus: Record<string, StatusTone> = {
  DRAFT: "muted",
  DISCOVERY: "info",
  READY_FOR_GENERATION: "ready",
  GENERATING: "info",
  NEEDS_REVIEW: "review",
  APPROVED: "ready",
  FAILED: "blocked",
  ARCHIVED: "muted",
};

const iconByTone = {
  muted: CircleDashed,
  info: Clock3,
  ready: CheckCircle2,
  review: AlertTriangle,
  blocked: XCircle,
};

const labelByStatus: Record<string, string> = {
  DRAFT: "Draft",
  DISCOVERY: "Discovery",
  READY_FOR_GENERATION: "Ready",
  GENERATING: "Generating",
  NEEDS_REVIEW: "Needs review",
  APPROVED: "Approved",
  FAILED: "Blocked",
  ARCHIVED: "Archived",
};

type Props = {
  status: string;
  label?: string;
  className?: string;
};

export default function StatusBadge({ status, label, className }: Props) {
  const tone = toneByStatus[status] ?? "muted";
  const Icon = iconByTone[tone];

  return (
    <span className={cn("status-badge", `status-badge--${tone}`, className)}>
      <Icon aria-hidden="true" />
      {label ?? labelByStatus[status] ?? status.replaceAll("_", " ")}
    </span>
  );
}
