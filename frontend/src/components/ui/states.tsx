import clsx from "clsx";
import { AlertTriangle, Loader2 } from "lucide-react";
import type { ReactNode } from "react";

export function Spinner({ className }: { className?: string }) {
  return <Loader2 aria-hidden className={clsx("animate-spin", className ?? "h-5 w-5")} />;
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={clsx("animate-pulse rounded-lg bg-sunken", className)} />;
}

export function LoadingBlock({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-12 text-muted">
      <Spinner /> <span>{label}</span>
    </div>
  );
}

export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-line bg-surface px-6 py-12 text-center">
      {icon && <div className="text-muted">{icon}</div>}
      <h3 className="text-lg font-semibold">{title}</h3>
      {description && <p className="max-w-md text-sm text-muted">{description}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-2xl border border-danger/30 bg-danger-soft px-6 py-8 text-center">
      <AlertTriangle aria-hidden className="h-6 w-6 text-danger" />
      <p className="text-sm text-ink">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="rounded-lg border border-line bg-surface px-3 py-1.5 text-sm font-medium hover:bg-sunken">
          Try again
        </button>
      )}
    </div>
  );
}

export function Alert({ tone = "info", children }: { tone?: "info" | "warning" | "error" | "success"; children: ReactNode }) {
  const tones = {
    info: "bg-info-soft border-blue-200",
    warning: "bg-saffron-soft border-amber-200",
    error: "bg-danger-soft border-red-200",
    success: "bg-herb-soft border-green-200",
  };
  return <div role={tone === "error" ? "alert" : "status"} className={clsx("rounded-lg border px-4 py-3 text-sm", tones[tone])}>{children}</div>;
}
