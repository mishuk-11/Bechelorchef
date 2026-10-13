"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Card, Pagination, TableWrap } from "@/components/ui/display";
import { ErrorState, LoadingBlock } from "@/components/ui/states";
import { BarList, DateRange } from "@/features/admin/date-range";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { dhakaDate, formatDate } from "@/lib/format";
import type { ActivityPoint } from "@/types/api";

function ActivityTable({ points }: { points: ActivityPoint[] }) {
  const totals = points.reduce((t, p) => ({ signups: t.signups + p.signups, cookEvents: t.cookEvents + p.cookEvents, reviews: t.reviews + p.reviews, recipesSubmitted: t.recipesSubmitted + p.recipesSubmitted }),
    { signups: 0, cookEvents: 0, reviews: 0, recipesSubmitted: 0 });
  const active = points.filter((p) => p.signups + p.cookEvents + p.reviews + p.recipesSubmitted > 0).reverse();
  return (
    <TableWrap label="Daily activity">
      <thead><tr><th>Date (Dhaka)</th><th>Sign-ups</th><th>Cook events</th><th>Reviews</th><th>Recipes submitted</th></tr></thead>
      <tbody>
        <tr className="font-semibold"><td>Total</td><td>{totals.signups}</td><td>{totals.cookEvents}</td><td>{totals.reviews}</td><td>{totals.recipesSubmitted}</td></tr>
        {active.length === 0 ? <tr><td colSpan={5} className="text-center text-muted">No activity in this range.</td></tr> : active.map((p) => (
          <tr key={p.date}><td>{p.date}</td><td>{p.signups}</td><td>{p.cookEvents}</td><td>{p.reviews}</td><td>{p.recipesSubmitted}</td></tr>
        ))}
      </tbody>
    </TableWrap>
  );
}

function AuditLog() {
  const [page, setPage] = useState(0);
  const logs = useQuery({ queryKey: ["admin", "audit", page], queryFn: () => adminApi.auditLogs(page) });
  if (logs.isPending) return <LoadingBlock />;
  if (logs.isError) return <ErrorState message={errorMessage(logs.error)} onRetry={() => logs.refetch()} />;
  return (
    <>
      <TableWrap label="Audit log">
        <thead><tr><th>When</th><th>Admin</th><th>Action</th><th>Target</th><th>Details</th></tr></thead>
        <tbody>
          {logs.data.items.length === 0 ? <tr><td colSpan={5} className="text-center text-muted">No admin actions yet.</td></tr> : logs.data.items.map((l) => (
            <tr key={l.id}>
              <td>{formatDate(l.createdAt, true)}</td><td>{l.actorEmail ?? "system"}</td><td className="font-mono text-xs">{l.action}</td>
              <td>{l.targetType} {l.targetId ?? ""}</td><td className="max-w-72 truncate" title={l.details ?? undefined}>{l.details ?? ""}</td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
      <Pagination page={logs.data.page} totalPages={logs.data.totalPages} onChange={setPage} />
    </>
  );
}

export default function AnalyticsPage() {
  const [from, setFrom] = useState(dhakaDate(-29));
  const [to, setTo] = useState(dhakaDate(0));
  const recipes = useQuery({ queryKey: ["admin", "analytics", "recipes", from, to], queryFn: () => adminApi.recipeAnalytics(from, to) });
  const ingredients = useQuery({ queryKey: ["admin", "analytics", "ingredients", from, to], queryFn: () => adminApi.ingredientAnalytics(from, to) });
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-2xl font-bold">Analytics</h1>
        <DateRange from={from} to={to} onChange={(f, t) => { setFrom(f); setTo(t); }} />
      </div>
      {recipes.isError ? <ErrorState message={errorMessage(recipes.error)} onRetry={() => recipes.refetch()} /> : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <h2 className="mb-3 font-semibold">Popular ingredients (by cook events)</h2>
            {ingredients.isPending ? <LoadingBlock /> : ingredients.isError ? <ErrorState message={errorMessage(ingredients.error)} /> : (
              <BarList emptyText="No cook events in this range." items={ingredients.data.popular.map((i) => ({ label: i.name, value: i.cookCount, hint: `· in ${i.recipeCount} recipes` }))} />
            )}
          </Card>
          <Card>
            <h2 className="mb-3 font-semibold">Most cooked recipes</h2>
            {recipes.isPending ? <LoadingBlock /> : (
              <BarList emptyText="No cook events in this range." items={recipes.data.mostCooked.map((r) => ({ label: r.title, value: r.cookCount }))} />
            )}
          </Card>
          <Card className="lg:col-span-2">
            <h2 className="mb-3 font-semibold">Highest rated (approved reviews)</h2>
            {recipes.isPending ? <LoadingBlock /> : recipes.data.topRated.length === 0 ? <p className="text-sm text-muted">No approved reviews yet.</p> : (
              <ol className="grid gap-2 text-sm sm:grid-cols-2">
                {recipes.data.topRated.map((r) => (
                  <li key={r.recipeId} className="flex justify-between gap-2 rounded-lg bg-canvas px-3 py-2">
                    <Link href={`/recipes/${r.recipeId}`} className="truncate hover:text-brand">{r.title}</Link>
                    <span className="tabular-nums text-muted">★ {Number(r.ratingAvg).toFixed(2)} · {r.ratingCount} reviews · {r.cookCount} cooks</span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      )}
      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">User activity</h2>
        {recipes.isPending ? <LoadingBlock /> : recipes.data && <ActivityTable points={recipes.data.activity} />}
      </section>
      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">Admin audit log</h2>
        <AuditLog />
      </section>
      <p className="text-sm text-muted">Import job history with row-level errors is on the <Link href="/admin/imports" className="text-brand underline">Bulk import</Link> page.</p>
    </div>
  );
}
