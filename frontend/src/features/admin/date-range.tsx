"use client";

import { Field, Input } from "@/components/ui/field";

/** Inclusive local-date range (Asia/Dhaka) used by dashboard and analytics queries. */
export function DateRange({ from, to, onChange }: { from: string; to: string; onChange: (from: string, to: string) => void }) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <Field label="From" htmlFor="range-from">
        <Input id="range-from" type="date" value={from} max={to} onChange={(e) => onChange(e.target.value, to)} />
      </Field>
      <Field label="To" htmlFor="range-to">
        <Input id="range-to" type="date" value={to} min={from} onChange={(e) => onChange(from, e.target.value)} />
      </Field>
    </div>
  );
}

/** Simple accessible horizontal bar list (values are real counts from the API). */
export function BarList({ items, emptyText }: { items: { label: string; value: number; hint?: string }[]; emptyText: string }) {
  if (items.length === 0) return <p className="py-6 text-center text-sm text-muted">{emptyText}</p>;
  const max = Math.max(...items.map((i) => i.value), 1);
  return (
    <ul className="flex flex-col gap-2">
      {items.map((i) => (
        <li key={i.label} className="grid grid-cols-[minmax(0,10rem)_1fr_auto] items-center gap-3 text-sm">
          <span className="truncate" title={i.label}>{i.label}</span>
          <span className="h-2.5 rounded-full bg-sunken" aria-hidden>
            <span className="block h-full rounded-full bg-brand" style={{ width: `${(i.value / max) * 100}%` }} />
          </span>
          <span className="tabular-nums text-muted">{i.value}{i.hint ? ` ${i.hint}` : ""}</span>
        </li>
      ))}
    </ul>
  );
}
