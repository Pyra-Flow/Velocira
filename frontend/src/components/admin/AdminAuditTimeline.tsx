import { Activity, CheckCircle2, Clock3, ShieldAlert, XCircle } from "lucide-react";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import { cn, formatRelativeTime } from "@/lib/utils";
import type { AuditLogResponse } from "@/lib/api";

type Props = {
  events: AuditLogResponse[];
  loading?: boolean;
  className?: string;
};

function formatAction(action: string) {
  return action
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function AdminAuditTimeline({ events, loading = false, className }: Props) {
  return (
    <Card className={cn("p-0", className)}>
      <div className="flex items-start justify-between gap-4 border-b border-border px-5 py-4 sm:px-6">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-accent/25 bg-accent-light text-accent">
            <Activity className="h-4 w-4" aria-hidden="true" />
          </span>
          <div>
            <p className="sf-meta text-accent">System record</p>
            <h2 id="admin-audit-timeline-title" className="mt-1 text-base font-semibold text-foreground">
              Audit activity
            </h2>
          </div>
        </div>
        {!loading && events.length > 0 && (
          <span className="sf-meta pt-1 text-foreground-secondary">
            {events.length} recent event{events.length === 1 ? "" : "s"}
          </span>
        )}
      </div>

      <section aria-labelledby="admin-audit-timeline-title" aria-busy={loading}>
        {loading ? (
          <div className="divide-y divide-border" aria-live="polite" aria-label="Loading audit activity">
            {[0, 1, 2].map((item) => (
              <div key={item} className="flex gap-3 px-5 py-4 sm:px-6" aria-hidden="true">
                <span className="workspace-skeleton h-8 w-8 shrink-0" />
                <div className="min-w-0 flex-1 space-y-2">
                  <span className="workspace-skeleton block h-3 w-2/5" />
                  <span className="workspace-skeleton block h-2.5 w-3/5" />
                </div>
              </div>
            ))}
            <p className="sr-only">Loading audit activity.</p>
          </div>
        ) : events.length === 0 ? (
          <div className="grid min-h-48 place-items-center px-5 py-10 text-center sm:px-6">
            <div>
              <ShieldAlert className="mx-auto h-7 w-7 text-foreground-secondary" aria-hidden="true" />
              <p className="mt-3 text-sm font-medium text-foreground">No audit activity yet</p>
              <p className="mt-1 max-w-sm text-sm leading-6 text-foreground-secondary">
                New administrative events will appear here when the platform records them.
              </p>
            </div>
          </div>
        ) : (
          <ol className="divide-y divide-border">
            {events.map((event) => {
              const EventIcon = event.success ? CheckCircle2 : XCircle;

              return (
                <li key={event.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start sm:justify-between sm:px-6">
                  <div className="flex min-w-0 gap-3">
                    <span
                      className={cn(
                        "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md border",
                        event.success
                          ? "border-accent/25 bg-accent-light text-accent"
                          : "border-error/30 bg-error/10 text-error"
                      )}
                    >
                      <EventIcon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-foreground">{formatAction(event.action)}</p>
                      <p className="mt-1 truncate font-mono text-[0.7rem] text-foreground-secondary" title={event.email}>
                        {event.email}
                      </p>
                      {event.details && (
                        <p className="mt-1 text-sm leading-5 text-foreground-secondary">{event.details}</p>
                      )}
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2 self-start sm:justify-end">
                    <span className="inline-flex items-center gap-1.5 font-mono text-[0.68rem] text-foreground-secondary">
                      <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
                      <time dateTime={event.createdAt} title={new Date(event.createdAt).toLocaleString()}>
                        {formatRelativeTime(event.createdAt)}
                      </time>
                    </span>
                    <StatusBadge status={event.success ? "APPROVED" : "FAILED"} label={event.success ? "Success" : "Failed"} />
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </Card>
  );
}
