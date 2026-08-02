import { Check, CircleDot } from "lucide-react";
import type { CSSProperties } from "react";
import { cn } from "@/lib/utils";
import type { ProjectStatus } from "@/lib/api";

const stages = ["Draft", "Discovery", "Ready", "Generation", "Review", "Approved"];

const stageForStatus: Record<ProjectStatus, number> = {
  DRAFT: 0,
  DISCOVERY: 1,
  READY_FOR_GENERATION: 2,
  GENERATING: 3,
  NEEDS_REVIEW: 4,
  APPROVED: 5,
  FAILED: -1,
  ARCHIVED: -1,
};

type Props = {
  status: ProjectStatus;
  className?: string;
};

export default function ProjectLifecycle({ status, className }: Props) {
  const current = stageForStatus[status];
  const progress = current < 0 ? 0 : (current / (stages.length - 1)) * 100;
  const statusLabel = status === "FAILED"
    ? "Blocked"
    : status === "ARCHIVED"
      ? "Archived"
      : `${stages[current]} stage`;

  return (
    <section className={cn("sf-lifecycle", className)} aria-label={`Project lifecycle — ${statusLabel}`} tabIndex={0}>
      <div className="sf-lifecycle__heading">
        <p>Lifecycle signal</p>
        <span>{statusLabel}</span>
      </div>
      <ol className="sf-lifecycle__steps" style={{ "--sf-lifecycle-progress": `${progress}%` } as CSSProperties}>
        {stages.map((stage, index) => {
          const complete = index < current;
          const active = index === current;
          return (
            <li key={stage} className={cn(complete && "is-complete", active && "is-active")} aria-current={active ? "step" : undefined}>
              <span className="sf-lifecycle__node" aria-hidden="true">
                {complete ? <Check /> : <CircleDot />}
              </span>
              <span>{stage}</span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
