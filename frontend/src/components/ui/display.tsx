"use client";

import clsx from "clsx";
import { ChevronLeft, ChevronRight, Star, X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";

type Tone = "neutral" | "brand" | "herb" | "saffron" | "danger" | "info";
const tones: Record<Tone, string> = {
  neutral: "bg-sunken text-ink",
  brand: "bg-brand-soft text-brand-strong",
  herb: "bg-herb-soft text-herb",
  saffron: "bg-saffron-soft text-amber-900",
  danger: "bg-danger-soft text-danger",
  info: "bg-info-soft text-blue-900",
};

export function Badge({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return <span className={clsx("inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium", tones[tone], className)}>{children}</span>;
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx("rounded-2xl border border-line bg-surface p-5 shadow-sm", className)}>{children}</div>;
}

export function Stars({ value, label }: { value: number; label?: string }) {
  const rounded = Math.round(value);
  return (
    <span className="inline-flex items-center gap-0.5" aria-label={label ?? `${value.toFixed(1)} out of 5`} role="img">
      {[1, 2, 3, 4, 5].map((i) => (
        <Star key={i} aria-hidden className={clsx("h-4 w-4", i <= rounded ? "fill-saffron text-saffron" : "text-line")} />
      ))}
    </span>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-center gap-3 py-4">
      <button type="button" onClick={() => onChange(page - 1)} disabled={page <= 0}
        className="inline-flex h-9 items-center gap-1 rounded-lg border border-line bg-surface px-3 text-sm disabled:opacity-50">
        <ChevronLeft aria-hidden className="h-4 w-4" /> Previous
      </button>
      <span className="text-sm text-muted" aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}
        className="inline-flex h-9 items-center gap-1 rounded-lg border border-line bg-surface px-3 text-sm disabled:opacity-50">
        Next <ChevronRight aria-hidden className="h-4 w-4" />
      </button>
    </nav>
  );
}

/** Accessible modal built on the native <dialog> element (focus trap and Esc handled by the browser). */
export function Modal({ open, onClose, title, children, footer }: { open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog ref={ref} onClose={onClose} aria-labelledby="modal-title"
      className="m-auto w-[min(92vw,32rem)] rounded-2xl border border-line bg-surface p-0 text-ink shadow-xl">
      <div className="flex items-center justify-between border-b border-line px-5 py-3">
        <h2 id="modal-title" className="text-base font-semibold">{title}</h2>
        <button type="button" onClick={onClose} aria-label="Close" className="rounded-lg p-1 hover:bg-sunken">
          <X aria-hidden className="h-5 w-5" />
        </button>
      </div>
      <div className="px-5 py-4">{children}</div>
      {footer && <div className="flex justify-end gap-2 border-t border-line px-5 py-3">{footer}</div>}
    </dialog>
  );
}

/** Horizontally scrollable table wrapper so admin tables never clip on narrow screens. */
export function TableWrap({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-line bg-surface" role="region" aria-label={label} tabIndex={0}>
      <table className="w-full min-w-[640px] text-left text-sm [&_td]:px-4 [&_td]:py-3 [&_th]:px-4 [&_th]:py-3 [&_thead]:bg-sunken [&_thead]:text-xs [&_thead]:uppercase [&_thead]:tracking-wide [&_thead]:text-muted [&_tbody_tr]:border-t [&_tbody_tr]:border-line">
        {children}
      </table>
    </div>
  );
}

export function StatTile({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-2xl border border-line bg-surface p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
