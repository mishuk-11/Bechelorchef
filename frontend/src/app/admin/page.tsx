"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Badge, Card, StatTile, TableWrap } from "@/components/ui/display";
import { ErrorState, LoadingBlock } from "@/components/ui/states";
import { BarList, DateRange } from "@/features/admin/date-range";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { dhakaDate, formatDate } from "@/lib/format";

export default function AdminDashboard() {
  const [from, setFrom] = useState(dhakaDate(-29));
  const [to, setTo] = useState(dhakaDate(0));
  const summary = useQuery({ queryKey: ["admin", "dashboard", from, to], queryFn: () => adminApi.dashboard(from, to) });

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <DateRange from={from} to={to} onChange={(f, t) => { setFrom(f); setTo(t); }} />
      </div>
      {summary.isPending ? <LoadingBlock /> : summary.isError ? <ErrorState message={errorMessage(summary.error)} onRetry={() => summary.refetch()} /> : (
        <>
          <section aria-label="Key metrics" className="grid grid-cols-2 gap-3 md:grid-cols-4" data-testid="dashboard-stats">
            <StatTile label="Active users" value={summary.data.activeUsers} hint="Accounts with status Active (now)" />
            <StatTile label="New users" value={summary.data.newUsers} hint="Registered in range" />
            <StatTile label="Approved recipes" value={summary.data.approvedRecipes} hint="Published now" />
            <StatTile label="Recipes cooked" value={summary.data.cookEvents} hint={`${summary.data.activeCooks} cooks in range`} />
            <Link href="/admin/moderation"><StatTile label="Pending recipes" value={summary.data.pendingRecipes} hint="Awaiting moderation" /></Link>
            <Link href="/admin/moderation?tab=reviews"><StatTile label="Pending reviews" value={summary.data.pendingReviews} hint="Awaiting moderation" /></Link>
            <Link href="/admin/moderation?tab=reports"><StatTile label="Open reports" value={summary.data.openReports} hint="User flags" /></Link>
            <StatTile label="Average rating" value={summary.data.approvedReviewCount ? Number(summary.data.averageRating).toFixed(2) : "—"}
              hint={`${summary.data.approvedReviewCount} approved reviews`} />
          </section>
          <div className="grid gap-4 lg:grid-cols-3">
            <Card>
              <h2 className="mb-3 font-semibold">Popular ingredients</h2>
              <BarList emptyText="No cook events in this range."
                items={summary.data.popularIngredients.map((i) => ({ label: i.name, value: i.cookCount, hint: "cooks" }))} />
            </Card>
            <Card>
              <h2 className="mb-3 font-semibold">Most cooked</h2>
              <BarList emptyText="No cook events in this range."
                items={summary.data.mostCookedRecipes.map((r) => ({ label: r.title, value: r.cookCount, hint: "cooks" }))} />
            </Card>
            <Card>
              <h2 className="mb-3 font-semibold">Top rated</h2>
              {summary.data.topRatedRecipes.length === 0 ? <p className="py-6 text-center text-sm text-muted">No approved reviews yet.</p> : (
                <ol className="flex flex-col gap-2 text-sm">
                  {summary.data.topRatedRecipes.map((r) => (
                    <li key={r.recipeId} className="flex justify-between gap-2">
                      <Link href={`/recipes/${r.recipeId}`} className="truncate hover:text-brand">{r.title}</Link>
                      <span className="tabular-nums text-muted">★ {Number(r.ratingAvg).toFixed(2)} ({r.ratingCount})</span>
                    </li>
                  ))}
                </ol>
              )}
            </Card>
          </div>
          <section className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold">Recent imports</h2>
              <Link href="/admin/imports" className="text-sm text-brand underline">All imports</Link>
            </div>
            {summary.data.recentImports.length === 0 ? <p className="text-sm text-muted">No imports yet.</p> : (
              <TableWrap label="Recent imports">
                <thead><tr><th>File</th><th>Status</th><th>Imported</th><th>Failed</th><th>Started</th></tr></thead>
                <tbody>
                  {summary.data.recentImports.map((j) => (
                    <tr key={j.id}>
                      <td>{j.filename}</td>
                      <td><Badge tone={j.status === "COMPLETED" ? "herb" : j.status === "FAILED" ? "danger" : "info"}>{j.status}</Badge></td>
                      <td>{j.importedRows}</td><td>{j.failedRows}</td><td>{formatDate(j.startedAt, true)}</td>
                    </tr>
                  ))}
                </tbody>
              </TableWrap>
            )}
          </section>
        </>
      )}
    </div>
  );
}
