"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Modal, Pagination, Stars, TableWrap } from "@/components/ui/display";
import { Field, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { RecipeActions } from "@/features/admin/recipe-actions";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { formatDate } from "@/lib/format";
import type { AdminReview, Report } from "@/types/api";

function RecipeQueue() {
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ["admin", "moderation", "recipes", page], queryFn: () => adminApi.recipes({ status: "PENDING", page }) });
  if (q.isPending) return <LoadingBlock />;
  if (q.isError) return <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />;
  if (q.data.items.length === 0) return <EmptyState title="No recipes waiting" description="New community submissions appear here." />;
  return (
    <>
      <TableWrap label="Pending recipes">
        <thead><tr><th>Recipe</th><th>Submitted by</th><th>Time</th><th>Submitted</th><th>Decision</th></tr></thead>
        <tbody>
          {q.data.items.map((r) => (
            <tr key={r.id}>
              <td><Link href={`/recipes/${r.id}`} className="font-medium hover:text-brand">{r.title}</Link></td>
              <td>{r.author?.displayName ?? "—"}</td>
              <td>{r.totalTimeMins} min</td>
              <td>{formatDate(r.createdAt, true)}</td>
              <td><RecipeActions recipe={r} /></td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Pagination page={q.data.page} totalPages={q.data.totalPages} onChange={setPage} />
    </>
  );
}

function ReviewDecision({ review, onDone }: { review: AdminReview; onDone: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [action, setAction] = useState<"approve" | "reject" | "remove" | null>(null);
  const [reason, setReason] = useState("");
  const run = useMutation({
    mutationFn: () => adminApi.moderateReview(review.id, action!, reason.trim() || undefined),
    onSuccess: () => {
      notify(`Review ${action === "approve" ? "approved" : action === "reject" ? "rejected" : "removed"}.`);
      qc.invalidateQueries({ queryKey: ["admin"] });
      setAction(null);
      onDone();
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  return (
    <div className="flex flex-wrap gap-1">
      {review.status !== "APPROVED" && <Button size="sm" variant="success" onClick={() => setAction("approve")} data-testid={`review-approve-${review.id}`}>Approve</Button>}
      {review.status === "PENDING" && <Button size="sm" variant="danger" onClick={() => setAction("reject")}>Reject</Button>}
      {review.status !== "REMOVED" && <Button size="sm" variant="secondary" onClick={() => setAction("remove")}>Remove</Button>}
      <Modal open={action !== null} onClose={() => setAction(null)} title={`${action ?? ""} review`}
        footer={<><Button variant="ghost" onClick={() => setAction(null)}>Cancel</Button>
          <Button loading={run.isPending} disabled={action !== "approve" && !reason.trim()} onClick={() => run.mutate()}>Confirm</Button></>}>
        <Field label={action === "approve" ? "Note (optional)" : "Reason (recorded)"} htmlFor={`rv-${review.id}`} required={action !== "approve"}>
          <Textarea id={`rv-${review.id}`} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </Modal>
    </div>
  );
}

function ReviewQueue() {
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ["admin", "moderation", "reviews", status, page], queryFn: () => adminApi.reviewQueue(status, page) });
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <label htmlFor="review-status" className="text-sm text-muted">Show</label>
        <Select id="review-status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="w-44">
          <option value="PENDING">Pending</option>
          <option value="FLAGGED">Flagged (open reports)</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="REMOVED">Removed</option>
        </Select>
      </div>
      {q.isPending ? <LoadingBlock /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : q.data.items.length === 0 ? <EmptyState title="Nothing here" /> : (
          <>
            <ul className="flex flex-col gap-3">
              {q.data.items.map((r) => (
                <li key={r.id} className="flex flex-col gap-2 rounded-2xl border border-line bg-surface p-4">
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <Stars value={r.rating} />
                    <Link href={`/recipes/${r.recipeId}`} className="font-medium hover:text-brand">{r.recipeTitle}</Link>
                    <Badge>{r.status.toLowerCase()}</Badge>
                    <span className="text-muted">by {r.author.displayName} ({r.authorEmail}) · {formatDate(r.createdAt, true)}</span>
                  </div>
                  {r.comment && <p className="whitespace-pre-line text-sm">{r.comment}</p>}
                  {r.moderationReason && <p className="text-xs text-muted">Moderator note: {r.moderationReason}</p>}
                  <ReviewDecision review={r} onDone={() => q.refetch()} />
                </li>
              ))}
            </ul>
            <Pagination page={q.data.page} totalPages={q.data.totalPages} onChange={setPage} />
          </>
        )}
    </div>
  );
}

function ReportRow({ report }: { report: Report }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [action, setAction] = useState<"DISMISS" | "REMOVE_CONTENT" | null>(null);
  const [note, setNote] = useState("");
  const run = useMutation({
    mutationFn: () => adminApi.resolveReport(report.id, action!, note.trim()),
    onSuccess: () => {
      notify(action === "DISMISS" ? "Report dismissed." : "Content removed and report resolved.");
      setAction(null);
      qc.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  return (
    <li className="flex flex-col gap-2 rounded-2xl border border-line bg-surface p-4 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="danger">{report.targetType}</Badge>
        {report.recipeId ? <Link href={`/recipes/${report.recipeId}`} className="font-medium hover:text-brand">{report.targetSummary}</Link> : <span>{report.targetSummary}</span>}
        <span className="ml-auto text-muted">{formatDate(report.createdAt, true)}</span>
      </div>
      <p><span className="text-muted">Reason from {report.reporter.displayName}:</span> {report.reason}</p>
      {report.status === "OPEN" && (
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={() => setAction("DISMISS")}>Dismiss</Button>
          <Button size="sm" variant="danger" onClick={() => setAction("REMOVE_CONTENT")}>Remove content</Button>
        </div>
      )}
      <Modal open={action !== null} onClose={() => setAction(null)} title={action === "DISMISS" ? "Dismiss report" : "Remove reported content"}
        footer={<><Button variant="ghost" onClick={() => setAction(null)}>Cancel</Button>
          <Button loading={run.isPending} disabled={!note.trim()} variant={action === "DISMISS" ? "primary" : "danger"} onClick={() => run.mutate()}>Confirm</Button></>}>
        <Field label="Resolution note (recorded)" htmlFor={`rep-${report.id}`} required>
          <Textarea id={`rep-${report.id}`} value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </Modal>
    </li>
  );
}

function ReportQueue() {
  const [status, setStatus] = useState("OPEN");
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ["admin", "moderation", "reports", status, page], queryFn: () => adminApi.reports(status, page) });
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2">
        <label htmlFor="report-status" className="text-sm text-muted">Show</label>
        <Select id="report-status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="w-40">
          <option value="OPEN">Open</option>
          <option value="ACTIONED">Actioned</option>
          <option value="DISMISSED">Dismissed</option>
        </Select>
      </div>
      {q.isPending ? <LoadingBlock /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : q.data.items.length === 0 ? <EmptyState title="No reports" /> : (
          <>
            <ul className="flex flex-col gap-3">{q.data.items.map((r) => <ReportRow key={r.id} report={r} />)}</ul>
            <Pagination page={q.data.page} totalPages={q.data.totalPages} onChange={setPage} />
          </>
        )}
    </div>
  );
}

const TABS = [
  { id: "recipes", label: "Recipes" },
  { id: "reviews", label: "Reviews" },
  { id: "reports", label: "Reports" },
] as const;

function Moderation() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const tab = TABS.some((t) => t.id === params.get("tab")) ? params.get("tab")! : "recipes";
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-2xl font-bold">Moderation</h1>
      <div role="tablist" aria-label="Moderation queues" className="flex gap-1 border-b border-line">
        {TABS.map((t) => (
          <button key={t.id} role="tab" type="button" aria-selected={tab === t.id} aria-controls={`panel-${t.id}`} id={`tab-${t.id}`}
            onClick={() => router.replace(`${pathname}?tab=${t.id}`)}
            className={clsx("-mb-px border-b-2 px-4 py-2 text-sm font-medium", tab === t.id ? "border-brand text-brand" : "border-transparent text-muted hover:text-ink")}>
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === "recipes" ? <RecipeQueue /> : tab === "reviews" ? <ReviewQueue /> : <ReportQueue />}
      </div>
    </div>
  );
}

export default function ModerationPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <Moderation />
    </Suspense>
  );
}
