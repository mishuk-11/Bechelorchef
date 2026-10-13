"use client";

import { useQuery } from "@tanstack/react-query";
import { SlidersHorizontal, X } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { Pagination } from "@/components/ui/display";
import { Field, Input, Select } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, LoadingBlock, Skeleton } from "@/components/ui/states";
import { RecipeCard, RecipeGrid } from "@/features/recipes/recipe-card";
import { errorMessage } from "@/lib/api/client";
import { catalogApi, recipeApi } from "@/lib/api/endpoints";

const DEFAULT_MAX_TIME = "20";

function Catalogue() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const q = params.get("q") ?? "";
  const tagId = params.get("tagId") ?? "";
  const difficulty = params.get("difficulty") ?? "";
  const source = params.get("source") ?? "";
  const maxCost = params.get("maxCostBdt") ?? "";
  const timeParam = params.get("maxTotalTimeMins") ?? DEFAULT_MAX_TIME; // "any" removes the quick filter
  const sort = params.get("sort") ?? "newest";
  const page = Number(params.get("page") ?? 0);
  const [text, setText] = useState(q);
  const [showFilters, setShowFilters] = useState(false);

  const tags = useQuery({ queryKey: ["tags"], queryFn: catalogApi.tags, staleTime: 300_000 });
  const results = useQuery({
    queryKey: ["recipes", "search", q, tagId, difficulty, source, maxCost, timeParam, sort, page],
    queryFn: () =>
      recipeApi.search({
        q: q || undefined,
        tagId: tagId ? Number(tagId) : undefined,
        difficulty: difficulty || undefined,
        source: source || undefined,
        maxCostBdt: maxCost ? Number(maxCost) : undefined,
        maxTotalTimeMins: timeParam === "any" ? undefined : Number(timeParam),
        sort,
        page,
        size: 12,
      }),
  });

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params.toString());
    Object.entries(changes).forEach(([k, v]) => (v === null || v === "" ? next.delete(k) : next.set(k, v)));
    if (!("page" in changes)) next.delete("page");
    router.push(`${pathname}?${next.toString()}`);
  }

  function submitSearch(e: FormEvent) {
    e.preventDefault();
    update({ q: text.trim() || null });
  }

  const tagName = tags.data?.find((t) => String(t.id) === tagId)?.name;
  const chips: { label: string; clear: Record<string, string | null> }[] = [
    ...(q ? [{ label: `“${q}”`, clear: { q: null } }] : []),
    ...(timeParam !== "any" ? [{ label: `≤ ${timeParam} min`, clear: { maxTotalTimeMins: "any" } }] : []),
    ...(tagId ? [{ label: tagName ?? "Tag", clear: { tagId: null } }] : []),
    ...(difficulty ? [{ label: difficulty.toLowerCase(), clear: { difficulty: null } }] : []),
    ...(source ? [{ label: source, clear: { source: null } }] : []),
    ...(maxCost ? [{ label: `≤ ৳${maxCost}`, clear: { maxCostBdt: null } }] : []),
  ];

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <h1 className="text-2xl font-bold">Recipes</h1>
        <form onSubmit={submitSearch} role="search" className="flex w-full gap-2 sm:max-w-md">
          <label htmlFor="recipe-search" className="sr-only">Search title or description</label>
          <Input id="recipe-search" value={text} onChange={(e) => setText(e.target.value)} placeholder="Search title or description" />
          <Button type="submit">Search</Button>
        </form>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => setShowFilters((s) => !s)} aria-expanded={showFilters} aria-controls="filters"
          icon={<SlidersHorizontal aria-hidden className="h-4 w-4" />}>Filters</Button>
        <ul className="flex flex-wrap gap-2" aria-label="Active filters">
          {chips.map((c) => (
            <li key={c.label}>
              <button type="button" onClick={() => update(c.clear)} aria-label={`Remove filter ${c.label}`}
                className="inline-flex items-center gap-1 rounded-full bg-brand-soft px-3 py-1 text-sm text-brand-strong">
                {c.label} <X aria-hidden className="h-3.5 w-3.5" />
              </button>
            </li>
          ))}
        </ul>
        <div className="ml-auto flex items-center gap-2">
          <label htmlFor="sort" className="text-sm text-muted">Sort</label>
          <Select id="sort" value={sort} onChange={(e) => update({ sort: e.target.value })} className="w-40">
            <option value="newest">Newest</option>
            <option value="time">Quickest</option>
            <option value="cost">Cheapest</option>
            <option value="rating">Top rated</option>
            <option value="popular">Most reviewed</option>
            <option value="title">Title A–Z</option>
          </Select>
        </div>
      </div>

      {showFilters && (
        <div id="filters" className="grid gap-4 rounded-2xl border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-5">
          <Field label="Max total time" htmlFor="f-time">
            <Select id="f-time" value={timeParam} onChange={(e) => update({ maxTotalTimeMins: e.target.value })}>
              <option value="15">≤ 15 min (Express)</option>
              <option value="20">≤ 20 min (Bachelor Quick)</option>
              <option value="30">≤ 30 min</option>
              <option value="60">≤ 60 min</option>
              <option value="any">Any time</option>
            </Select>
          </Field>
          <Field label="Tag" htmlFor="f-tag">
            <Select id="f-tag" value={tagId} onChange={(e) => update({ tagId: e.target.value })}>
              <option value="">All tags</option>
              {tags.data?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </Select>
          </Field>
          <Field label="Difficulty" htmlFor="f-difficulty">
            <Select id="f-difficulty" value={difficulty} onChange={(e) => update({ difficulty: e.target.value })}>
              <option value="">Any</option>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </Select>
          </Field>
          <Field label="Source" htmlFor="f-source">
            <Select id="f-source" value={source} onChange={(e) => update({ source: e.target.value })}>
              <option value="">Official & community</option>
              <option value="official">Official</option>
              <option value="community">Community</option>
            </Select>
          </Field>
          <Field label="Max cost (৳)" htmlFor="f-cost">
            <Input id="f-cost" type="number" min={0} step="1" defaultValue={maxCost}
              onBlur={(e) => update({ maxCostBdt: e.target.value })} />
          </Field>
        </div>
      )}

      {results.isPending ? (
        <RecipeGrid>{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-72" />)}</RecipeGrid>
      ) : results.isError ? (
        <ErrorState message={errorMessage(results.error)} onRetry={() => results.refetch()} />
      ) : results.data.items.length === 0 ? (
        <EmptyState title="No recipes match these filters" description="Try removing a filter or searching for something else." />
      ) : (
        <>
          <p className="text-sm text-muted" aria-live="polite">{results.data.totalElements} recipes</p>
          <RecipeGrid>{results.data.items.map((r) => <RecipeCard key={r.id} recipe={r} />)}</RecipeGrid>
          <Pagination page={results.data.page} totalPages={results.data.totalPages} onChange={(p) => update({ page: String(p) })} />
        </>
      )}
    </div>
  );
}

export default function RecipesPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <Catalogue />
    </Suspense>
  );
}
