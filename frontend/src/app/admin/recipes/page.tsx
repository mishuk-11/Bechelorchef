"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Badge, Pagination, TableWrap } from "@/components/ui/display";
import { Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { RecipeActions } from "@/features/admin/recipe-actions";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { STATUS_LABEL, formatBdt, formatDate } from "@/lib/format";
import type { RecipeStatus } from "@/types/api";

export default function AdminRecipesPage() {
  const [status, setStatus] = useState("");
  const [source, setSource] = useState("");
  const [q, setQ] = useState("");
  const [text, setText] = useState("");
  const [page, setPage] = useState(0);
  const recipes = useQuery({
    queryKey: ["admin", "recipes", status, source, q, page],
    queryFn: () => adminApi.recipes({ status: status || undefined, source: source || undefined, q: q || undefined, page }),
  });
  function search(e: FormEvent) {
    e.preventDefault();
    setQ(text.trim());
    setPage(0);
  }
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Recipes</h1>
        <ButtonLink href="/admin/recipes/new"><Plus aria-hidden className="h-4 w-4" /> New official recipe</ButtonLink>
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <form onSubmit={search} role="search" className="flex gap-2">
          <label htmlFor="admin-recipe-q" className="sr-only">Search recipes</label>
          <Input id="admin-recipe-q" value={text} onChange={(e) => setText(e.target.value)} placeholder="Search title" className="w-56" />
          <Button type="submit" variant="secondary">Search</Button>
        </form>
        <label htmlFor="admin-status" className="sr-only">Status</label>
        <Select id="admin-status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="w-44">
          <option value="">All statuses</option>
          {(Object.keys(STATUS_LABEL) as RecipeStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </Select>
        <label htmlFor="admin-source" className="sr-only">Source</label>
        <Select id="admin-source" value={source} onChange={(e) => { setSource(e.target.value); setPage(0); }} className="w-40">
          <option value="">All sources</option>
          <option value="official">Official</option>
          <option value="community">Community</option>
        </Select>
      </div>
      {recipes.isPending ? <LoadingBlock /> : recipes.isError ? <ErrorState message={errorMessage(recipes.error)} onRetry={() => recipes.refetch()} />
        : recipes.data.items.length === 0 ? <EmptyState title="No recipes found" /> : (
          <>
            <TableWrap label="Recipes">
              <thead><tr><th>Title</th><th>Status</th><th>Source</th><th>Time</th><th>Cost</th><th>Created</th><th>Actions</th></tr></thead>
              <tbody data-testid="admin-recipe-rows">
                {recipes.data.items.map((r) => (
                  <tr key={r.id}>
                    <td className="max-w-64"><Link href={`/recipes/${r.id}`} className="font-medium hover:text-brand">{r.title}</Link></td>
                    <td><Badge tone={r.status === "APPROVED" ? "herb" : r.status === "PENDING" ? "saffron" : r.status === "REJECTED" ? "danger" : "neutral"}>{STATUS_LABEL[r.status]}</Badge></td>
                    <td>{r.official ? "Official" : r.author?.displayName ?? "Community"}</td>
                    <td>{r.totalTimeMins} min</td>
                    <td>{formatBdt(r.estimatedCostBdt)}</td>
                    <td>{formatDate(r.createdAt)}</td>
                    <td><RecipeActions recipe={r} /></td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
            <Pagination page={recipes.data.page} totalPages={recipes.data.totalPages} onChange={setPage} />
          </>
        )}
    </div>
  );
}
