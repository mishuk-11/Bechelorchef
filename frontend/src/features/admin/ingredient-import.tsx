"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FileUp, X } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Card, StatTile, TableWrap } from "@/components/ui/display";
import { Alert } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { IngredientImportGuide } from "@/features/admin/import-guides";
import { ALLOWED_IMPORT_MAX_BYTES } from "@/schemas";
import type { IngredientImportResult, IngredientImportStatus } from "@/types/api";

const STATUS: Record<IngredientImportStatus, { label: string; tone: "herb" | "neutral" | "saffron" | "danger" }> = {
  NEW: { label: "Will be added", tone: "herb" },
  CREATED: { label: "Added", tone: "herb" },
  EXISTS: { label: "Already exists", tone: "neutral" },
  DUPLICATE: { label: "Duplicate in file", tone: "saffron" },
  INVALID: { label: "Invalid", tone: "danger" },
};


const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/** Admin → Ingredients → "Bulk add": upload a CSV/JSON file, preview the outcome of every row, then add the new ones. */
export function IngredientBulkImport({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string>();
  const [result, setResult] = useState<IngredientImportResult | null>(null);

  const preview = useMutation({
    mutationFn: (f: File) => adminApi.previewIngredientImport(f),
    onSuccess: setResult,
  });
  const runImport = useMutation({
    mutationFn: (f: File) => adminApi.importIngredients(f),
    onSuccess: (r) => {
      setResult(r);
      notify(r.newCount > 0 ? `${plural(r.newCount, "ingredient")} added.` : "No new ingredients were added.", r.newCount > 0 ? "success" : "info");
      qc.invalidateQueries({ queryKey: ["admin", "ingredients"] });
      qc.invalidateQueries({ queryKey: ["admin", "ingredient-categories"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  function choose(f: File | undefined) {
    setClientError(undefined);
    setResult(null);
    preview.reset();
    runImport.reset();
    if (!f) return setFile(null);
    if (!/\.(csv|json)$/i.test(f.name)) return setClientError("Choose a .csv or .json file.");
    if (f.size === 0) return setClientError("The file is empty.");
    if (f.size > ALLOWED_IMPORT_MAX_BYTES) return setClientError("The file is larger than 5 MB.");
    setFile(f);
  }

  const done = result && !result.dryRun;
  return (
    <Card className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Bulk add ingredients</h2>
          <p className="text-sm text-muted">Upload a CSV or JSON file (up to 500 ingredients, 5 MB). Nothing is saved until you confirm the preview. Names that already exist are skipped.</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close bulk add" className="rounded-lg p-1 hover:bg-sunken">
          <X aria-hidden className="h-5 w-5" />
        </button>
      </div>

      <IngredientImportGuide />

      <div className="flex flex-wrap items-center gap-3">
        <label htmlFor="bulk-ingredients-file" className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-line bg-surface px-4 text-sm font-medium hover:bg-sunken">
          <FileUp aria-hidden className="h-4 w-4" /> Choose file
        </label>
        <input id="bulk-ingredients-file" type="file" accept=".csv,.json,text/csv,application/json" className="sr-only"
          data-testid="bulk-ingredients-file" onChange={(e) => { choose(e.target.files?.[0]); e.target.value = ""; }} />
        <span className="text-sm" data-testid="bulk-ingredients-chosen">{file ? `${file.name} (${Math.ceil(file.size / 1024)} KB)` : "No file chosen"}</span>
        <Button disabled={!file} loading={preview.isPending} onClick={() => file && preview.mutate(file)} data-testid="bulk-ingredients-preview">
          Check file
        </Button>
      </div>
      {clientError && <Alert tone="error">{clientError}</Alert>}
      {preview.isError && <Alert tone="error"><span data-testid="bulk-ingredients-error">{errorMessage(preview.error)}</span></Alert>}

      {result && (
        <div className="flex flex-col gap-4" data-testid="bulk-ingredients-summary">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatTile label={done ? "Added" : "New"} value={<span data-testid="bulk-ingredients-new">{result.newCount}</span>} />
            <StatTile label="Already exist" value={result.existingCount} />
            <StatTile label="Duplicates in file" value={result.duplicateCount} />
            <StatTile label="Invalid" value={<span data-testid="bulk-ingredients-invalid">{result.invalidCount}</span>} />
          </div>
          {done ? (
            <Alert tone={result.newCount > 0 ? "success" : "info"}>
              <span data-testid="bulk-ingredients-done">
                {plural(result.newCount, "ingredient")} added from {result.filename}.
                {result.totalRows - result.newCount > 0 && ` ${plural(result.totalRows - result.newCount, "row")} skipped (see the list below).`}
              </span>
            </Alert>
          ) : (
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="success" disabled={result.newCount === 0} loading={runImport.isPending}
                onClick={() => file && runImport.mutate(file)} data-testid="bulk-ingredients-confirm">
                {result.newCount === 0 ? "Nothing new to add" : `Add ${plural(result.newCount, "ingredient")}`}
              </Button>
              <span className="text-sm text-muted">Preview only — nothing has been saved yet.</span>
            </div>
          )}
          <div className="max-h-96 overflow-y-auto">
            <TableWrap label="Bulk ingredient rows">
              <thead><tr><th>Row</th><th>Name</th><th>Category</th><th>Default unit</th><th>Result</th><th>Note</th></tr></thead>
              <tbody data-testid="bulk-ingredients-rows">
                {result.rows.map((r) => (
                  <tr key={r.rowNumber}>
                    <td>{r.rowNumber}</td>
                    <td className="font-medium">{r.name ?? "—"}</td>
                    <td>{r.category ?? "—"}</td>
                    <td>{r.defaultUnit ?? "—"}</td>
                    <td><Badge tone={STATUS[r.status].tone}>{STATUS[r.status].label}</Badge></td>
                    <td className="text-muted">{r.message}</td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
          </div>
        </div>
      )}
    </Card>
  );
}
