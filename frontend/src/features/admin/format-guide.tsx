"use client";

import clsx from "clsx";
import { Download } from "lucide-react";
import { useState, type ReactNode } from "react";
import { buttonClasses } from "@/components/ui/button";

/** One row of a "fields" table: name, required?, what to write, example. */
export type FieldRow = { field: string; required: boolean | string; rules: ReactNode; example: string };

export interface FormatSpec {
  /** Short intro shown above the steps for this format. */
  intro: ReactNode;
  fields: FieldRow[];
  /** A small, complete example that can be copied. */
  example: string;
  /** Extra tips under the example. */
  notes?: ReactNode[];
  /** Path of a full sample file in /public, e.g. /samples/recipes-sample.csv */
  sampleHref: string;
}

/**
 * Collapsible "how to prepare your file" guide with CSV / JSON tabs: steps, a field table,
 * a worked example and a downloadable sample file. Shared by the recipe and ingredient importers.
 */
export function FormatGuide({ title, steps, csv, json, notice, defaultOpen = true }: {
  title: string;
  steps: ReactNode[];
  /** Highlighted rule shown above the steps (e.g. a hard limit that rejects rows). */
  notice?: ReactNode;
  csv: FormatSpec;
  json: FormatSpec;
  defaultOpen?: boolean;
}) {
  const [tab, setTab] = useState<"csv" | "json">("csv");
  const spec = tab === "csv" ? csv : json;
  return (
    <details className="rounded-xl border border-line bg-sunken/40 text-sm" open={defaultOpen} data-testid="format-guide">
      <summary className="cursor-pointer select-none px-4 py-3 font-medium text-brand">{title}</summary>
      <div className="flex flex-col gap-4 border-t border-line px-4 py-4">
        {notice && (
          <div role="note" className="rounded-lg border border-amber-300 bg-saffron-soft px-4 py-3 text-ink" data-testid="format-guide-notice">
            {notice}
          </div>
        )}
        <ol className="flex list-decimal flex-col gap-1 pl-5 text-ink">
          {steps.map((s, i) => <li key={i}>{s}</li>)}
        </ol>

        <div role="tablist" aria-label="File format" className="flex gap-1 self-start rounded-lg border border-line bg-surface p-1">
          {(["csv", "json"] as const).map((t) => (
            <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
              className={clsx("rounded-md px-3 py-1.5 text-sm font-medium", tab === t ? "bg-brand text-white" : "text-muted hover:bg-sunken")}>
              {t.toUpperCase()} format
            </button>
          ))}
        </div>

        <div role="tabpanel" className="flex flex-col gap-3">
          <div className="text-ink">{spec.intro}</div>
          <div className="overflow-x-auto rounded-lg border border-line bg-surface">
            <table className="w-full min-w-[640px] text-left text-sm [&_td]:px-3 [&_td]:py-2 [&_td]:align-top [&_th]:px-3 [&_th]:py-2">
              <thead className="bg-sunken text-xs uppercase tracking-wide text-muted">
                <tr><th>{tab === "csv" ? "Column" : "Key"}</th><th>Required?</th><th>What to write</th><th>Example</th></tr>
              </thead>
              <tbody className="[&_tr]:border-t [&_tr]:border-line">
                {spec.fields.map((f) => (
                  <tr key={f.field}>
                    <td><code className="font-mono text-xs font-semibold">{f.field}</code></td>
                    <td className={f.required === true ? "font-medium text-brand-strong" : "text-muted"}>
                      {f.required === true ? "Required" : f.required === false ? "Optional" : f.required}
                    </td>
                    <td className="text-ink">{f.rules}</td>
                    <td><code className="break-all font-mono text-xs text-muted">{f.example}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-1">
            <p className="font-medium text-ink">Example</p>
            <pre className="max-h-72 overflow-auto rounded-lg border border-line bg-surface p-3 font-mono text-xs leading-relaxed text-ink">{spec.example}</pre>
          </div>
          {spec.notes && spec.notes.length > 0 && (
            <ul className="flex list-disc flex-col gap-1 pl-5 text-muted">
              {spec.notes.map((n, i) => <li key={i}>{n}</li>)}
            </ul>
          )}
          <a href={spec.sampleHref} download className={clsx(buttonClasses("secondary", "sm"), "self-start")} data-testid={`sample-${tab}`}>
            <Download aria-hidden className="h-4 w-4" /> Download sample {tab.toUpperCase()} file
          </a>
        </div>
      </div>
    </details>
  );
}

/** Inline code helper for the guides. */
export const C = ({ children }: { children: ReactNode }) => <code className="rounded bg-surface px-1 font-mono text-xs">{children}</code>;
