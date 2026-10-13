"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileUp } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Card, Modal, Pagination, StatTile, TableWrap } from "@/components/ui/display";
import { Alert, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { apiDownload, errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { formatDate } from "@/lib/format";
import { RecipeImportGuide } from "@/features/admin/import-guides";
import { ALLOWED_IMPORT_MAX_BYTES } from "@/schemas";
import type { ImportIssue, ImportJobDetail, ImportPreview, ImportStatus } from "@/types/api";

const statusTone = (s: ImportStatus) =>
  s === "COMPLETED" ? "herb" : s === "COMPLETED_WITH_ERRORS" ? "saffron" : s === "FAILED" || s === "EXPIRED" ? "danger" : "info";

function IssuesTable({ issues, label }: { issues: ImportIssue[]; label: string }) {
  if (issues.length === 0) return <p className="text-sm text-muted">No issues.</p>;
  return (
    <TableWrap label={label}>
      <thead><tr><th>Row</th><th>Line</th><th>Severity</th><th>Field</th><th>Problem</th><th>Excerpt</th></tr></thead>
      <tbody data-testid="import-issues">
        {issues.map((i, idx) => (
          <tr key={idx}>
            <td>{i.rowNumber ?? "file"}</td>
            <td>{i.lineNumber ?? "—"}</td>
            <td><Badge tone={i.severity === "ERROR" ? "danger" : "saffron"}>{i.severity.toLowerCase()}</Badge></td>
            <td className="font-mono text-xs">{i.field ?? "—"}</td>
            <td>{i.message} <span className="text-xs text-muted">({i.code})</span></td>
            <td className="max-w-56 truncate font-mono text-xs text-muted" title={i.excerpt ?? undefined}>{i.excerpt ?? ""}</td>
          </tr>
        ))}
      </tbody>
    </TableWrap>
  );
}

function ResultSummary({ result }: { result: ImportJobDetail }) {
  const j = result.job;
  return (
    <Card className="flex flex-col gap-4" >
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-semibold">Import result</h2>
        <Badge tone={statusTone(j.status)}>{j.status}</Badge>
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4" data-testid="import-result">
        <StatTile label="Rows" value={j.totalRows} />
        <StatTile label="Imported" value={<span data-testid="imported-count">{j.importedRows}</span>} />
        <StatTile label="Rejected" value={<span data-testid="failed-count">{j.failedRows}</span>} />
        <StatTile label="Skipped (duplicates)" value={j.skippedRows} />
      </div>
      <p className="text-sm text-muted">{j.summary}</p>
      <IssuesTable issues={result.issues} label="Import issues" />
    </Card>
  );
}

function Uploader() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string>();
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportJobDetail | null>(null);
  const previewMutation = useMutation({
    mutationFn: (f: File) => adminApi.previewImport(f),
    onSuccess: (p) => { setPreview(p); setResult(null); qc.invalidateQueries({ queryKey: ["admin", "imports"] }); },
    onError: () => qc.invalidateQueries({ queryKey: ["admin", "imports"] }),
  });
  const confirmMutation = useMutation({
    mutationFn: (jobId: number) => adminApi.confirmImport(jobId),
    onSuccess: (r) => {
      setResult(r);
      setPreview(null);
      notify(`Import finished: ${r.job.importedRows} imported, ${r.job.failedRows} rejected.`, r.job.importedRows > 0 ? "success" : "error");
      qc.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  function choose(f: File | undefined) {
    setClientError(undefined);
    setPreview(null);
    setResult(null);
    previewMutation.reset();
    if (!f) return setFile(null);
    if (!/\.(csv|json)$/i.test(f.name)) return setClientError("Choose a .csv or .json file.");
    if (f.size === 0) return setClientError("The file is empty.");
    if (f.size > ALLOWED_IMPORT_MAX_BYTES) return setClientError("The file is larger than 5 MB.");
    setFile(f);
  }

  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">1. Upload a CSV or JSON file</h2>
        <p className="text-sm text-muted">Up to 500 recipes and 5 MB per file. <strong className="text-ink">Each recipe must take 60 minutes or less in total (prep + cook).</strong> Nothing is saved until you confirm the preview. Imported recipes are published as official.</p>
        <RecipeImportGuide />
        <div className="flex flex-wrap items-center gap-3">
          <label htmlFor="import-file" className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-line bg-surface px-4 text-sm font-medium hover:bg-sunken">
            <FileUp aria-hidden className="h-4 w-4" /> Choose file
          </label>
          <input id="import-file" type="file" accept=".csv,.json,text/csv,application/json" className="sr-only" data-testid="import-file"
            onChange={(e) => choose(e.target.files?.[0])} />
          <span className="text-sm" data-testid="chosen-file">{file ? `${file.name} (${Math.ceil(file.size / 1024)} KB)` : "No file chosen"}</span>
          <Button disabled={!file} loading={previewMutation.isPending} onClick={() => file && previewMutation.mutate(file)} data-testid="preview-import">
            Validate & preview
          </Button>
        </div>
        {clientError && <Alert tone="error">{clientError}</Alert>}
        {previewMutation.isError && <Alert tone="error"><span data-testid="import-file-error">{errorMessage(previewMutation.error)}</span></Alert>}
      </Card>

      {preview && (
        <Card className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold">2. Review the preview</h2>
          <div className="grid grid-cols-3 gap-3" data-testid="preview-summary">
            <StatTile label="Rows" value={preview.totalRows} />
            <StatTile label="Valid" value={<span data-testid="valid-count">{preview.validRows}</span>} />
            <StatTile label="Invalid" value={<span data-testid="invalid-count">{preview.invalidRows}</span>} />
          </div>
          <TableWrap label="Previewed rows">
            <thead><tr><th>Row</th><th>Title</th><th>Total time</th><th>Ingredients</th><th>Steps</th><th>Result</th></tr></thead>
            <tbody>
              {preview.rows.map((r) => (
                <tr key={r.rowNumber}>
                  <td>{r.rowNumber}</td><td>{r.title ?? "—"}</td><td>{r.totalTimeMins ?? "—"}</td><td>{r.ingredientCount}</td><td>{r.stepCount}</td>
                  <td>{r.valid ? <Badge tone="herb">valid</Badge> : <Badge tone="danger">invalid</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </TableWrap>
          <IssuesTable issues={preview.issues} label="Preview issues" />
          <div className="flex flex-wrap gap-2">
            <Button variant="success" disabled={preview.validRows === 0} loading={confirmMutation.isPending}
              onClick={() => confirmMutation.mutate(preview.jobId)} data-testid="confirm-import">
              Import {preview.validRows} valid recipe{preview.validRows === 1 ? "" : "s"}
            </Button>
            <Button variant="ghost" onClick={() => choose(undefined)}>Start over</Button>
          </div>
          {preview.validRows === 0 && <Alert tone="warning">No valid rows to import. Fix the file and upload it again.</Alert>}
        </Card>
      )}
      {result && <ResultSummary result={result} />}
    </div>
  );
}

function JobHistory() {
  const { notify } = useToast();
  const [page, setPage] = useState(0);
  const [openJob, setOpenJob] = useState<number | null>(null);
  const jobs = useQuery({ queryKey: ["admin", "imports", page], queryFn: () => adminApi.imports(page) });
  const detail = useQuery({ queryKey: ["admin", "import", openJob], queryFn: () => adminApi.importDetail(openJob!), enabled: openJob !== null });
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">Upload log</h2>
      {jobs.isPending ? <LoadingBlock /> : jobs.isError ? <ErrorState message={errorMessage(jobs.error)} onRetry={() => jobs.refetch()} />
        : jobs.data.items.length === 0 ? <p className="text-sm text-muted">No uploads yet.</p> : (
          <>
            <TableWrap label="Import jobs">
              <thead><tr><th>#</th><th>File</th><th>Uploaded by</th><th>Started</th><th>Status</th><th>Rows</th><th>Imported</th><th>Failed</th><th>Skipped</th><th></th></tr></thead>
              <tbody data-testid="import-jobs">
                {jobs.data.items.map((j) => (
                  <tr key={j.id}>
                    <td>{j.id}</td><td>{j.filename}</td><td>{j.uploadedBy?.displayName ?? "—"}</td><td>{formatDate(j.startedAt, true)}</td>
                    <td><Badge tone={statusTone(j.status)}>{j.status}</Badge></td>
                    <td>{j.totalRows}</td><td>{j.importedRows}</td><td>{j.failedRows}</td><td>{j.skippedRows}</td>
                    <td><Button size="sm" variant="secondary" onClick={() => setOpenJob(j.id)}>Details</Button></td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
            <Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} onChange={setPage} />
          </>
        )}
      <Modal open={openJob !== null} onClose={() => setOpenJob(null)} title={`Import job #${openJob ?? ""}`}>
        {detail.isPending ? <LoadingBlock /> : detail.isError ? <ErrorState message={errorMessage(detail.error)} /> : detail.data && (
          <div className="flex max-h-[60vh] flex-col gap-3 overflow-auto">
            <p className="text-sm">{detail.data.job.summary}</p>
            <Button variant="secondary" size="sm" className="self-start" icon={<Download aria-hidden className="h-4 w-4" />}
              onClick={() => apiDownload(`/admin/imports/${openJob}/errors.csv`, `import-${openJob}-issues.csv`).catch((e) => notify(errorMessage(e), "error"))}>
              Download issues as CSV
            </Button>
            <IssuesTable issues={detail.data.issues} label="Job issues" />
          </div>
        )}
      </Modal>
    </section>
  );
}

export default function AdminImportsPage() {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">Bulk recipe import</h1>
      <Uploader />
      <JobHistory />
    </div>
  );
}
