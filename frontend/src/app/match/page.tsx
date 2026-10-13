"use client";

import { useMutation } from "@tanstack/react-query";
import { CheckCircle2, CircleDashed, Info, Refrigerator } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Pagination } from "@/components/ui/display";
import { Checkbox, Field, Input, Select } from "@/components/ui/field";
import { Alert, EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { IngredientChips, IngredientSearch } from "@/features/recipes/ingredient-picker";
import { RecipeImage } from "@/features/recipes/recipe-card";
import { errorMessage } from "@/lib/api/client";
import { recipeApi } from "@/lib/api/endpoints";
import { DIFFICULTY_LABEL, formatBdt } from "@/lib/format";
import type { Difficulty, MatchResponse } from "@/types/api";

export default function MatchPage() {
  const [selected, setSelected] = useState<{ id: number; name: string }[]>([]);
  const [maxTime, setMaxTime] = useState(20);
  const [strict, setStrict] = useState(false);
  const [difficulty, setDifficulty] = useState<"" | Difficulty>("");
  const [maxCost, setMaxCost] = useState("");
  const match = useMutation({
    mutationFn: (page: number) =>
      recipeApi.match({
        ingredientIds: selected.map((s) => s.id),
        maxTotalTimeMins: maxTime,
        strict,
        difficulty: difficulty || null,
        maxCostBdt: maxCost ? Number(maxCost) : null,
        page,
        size: 12,
      }),
  });
  const result: MatchResponse | undefined = match.data;

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8">
      <header className="flex flex-col gap-2">
        <h1 className="flex items-center gap-2 text-2xl font-bold"><Refrigerator aria-hidden className="h-7 w-7 text-brand" /> Find a meal</h1>
        <p className="text-muted">Add the ingredients you have, in any order. We rank recipes by how much of each you can already make.</p>
      </header>

      <form
        className="grid gap-5 rounded-2xl border border-line bg-surface p-5 lg:grid-cols-[1.5fr_1fr]"
        onSubmit={(e) => {
          e.preventDefault();
          match.mutate(0);
        }}>
        <div className="flex flex-col gap-3">
          <p className="text-sm font-medium" id="pantry-label">Your ingredients</p>
          <IngredientSearch inputId="pantry-search" label="Search your ingredients" excludeIds={selected.map((s) => s.id)}
            onSelect={(i) => setSelected((cur) => (cur.some((c) => c.id === i.id) ? cur : [...cur, { id: i.id, name: i.name }]))} />
          <IngredientChips items={selected} onRemove={(id) => setSelected((cur) => cur.filter((c) => c.id !== id))} />
          {selected.length === 0 && <p className="text-sm text-muted">No ingredients selected yet.</p>}
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
          <Field label={`Max total time: ${maxTime} min`} htmlFor="max-time" hint="Default 20 minutes; up to 60.">
            <input id="max-time" type="range" min={5} max={60} step={5} value={maxTime} onChange={(e) => setMaxTime(Number(e.target.value))}
              className="accent-brand" aria-valuetext={`${maxTime} minutes`} />
          </Field>
          <Field label="Difficulty" htmlFor="m-difficulty">
            <Select id="m-difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value as "" | Difficulty)}>
              <option value="">Any</option>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </Select>
          </Field>
          <Field label="Max cost (৳)" htmlFor="m-cost">
            <Input id="m-cost" type="number" min={0} value={maxCost} onChange={(e) => setMaxCost(e.target.value)} />
          </Field>
          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-medium">Matching</legend>
            <Checkbox id="strict" checked={strict} onChange={(e) => setStrict(e.target.checked)}
              label="I have every required ingredient (strict)" />
            <p className="text-xs text-muted">Unchecked: show the closest matches and what is missing.</p>
          </fieldset>
        </div>
        <div className="lg:col-span-2">
          <Button type="submit" size="lg" disabled={selected.length === 0} loading={match.isPending} data-testid="run-match">
            Show recipes
          </Button>
        </div>
      </form>

      <Alert tone="info">
        <span className="inline-flex items-start gap-2"><Info aria-hidden className="mt-0.5 h-4 w-4 shrink-0" />
          Pantry match is a guide, not a guarantee: your brands, equipment and substitutions may change the result. Optional ingredients never count as missing.</span>
      </Alert>

      {match.isPending ? (
        <LoadingBlock label="Matching recipes…" />
      ) : match.isError ? (
        <ErrorState message={errorMessage(match.error)} onRetry={() => match.mutate(0)} />
      ) : result ? (
        result.results.items.length === 0 ? (
          <EmptyState title="No recipes match yet" description={strict ? "Try turning off strict matching or adding more ingredients." : "Try a longer time limit or more ingredients."} />
        ) : (
          <section aria-live="polite" className="flex flex-col gap-4">
            <p className="text-sm text-muted">{result.results.totalElements} recipe(s) within {result.maxTotalTimeMins} min · {result.strict ? "strict" : "closest matches"}</p>
            <ul className="grid gap-4 md:grid-cols-2">
              {result.results.items.map((m) => (
                <li key={m.recipe.id} className="flex gap-4 rounded-2xl border border-line bg-surface p-4" data-testid="match-result">
                  <RecipeImage url={m.recipe.imageUrl} title={m.recipe.title} className="h-24 w-24 shrink-0 rounded-xl" />
                  <div className="flex min-w-0 flex-1 flex-col gap-2">
                    <div className="flex items-start justify-between gap-2">
                      <Link href={`/recipes/${m.recipe.id}`} className="font-semibold hover:text-brand">{m.recipe.title}</Link>
                      <Badge tone={m.matchPercent === 100 ? "herb" : m.matchPercent >= 50 ? "saffron" : "neutral"}>{m.matchPercent}% match</Badge>
                    </div>
                    <p className="text-xs text-muted">
                      {m.recipe.totalTimeMins} min · {formatBdt(m.recipe.estimatedCostBdt)} · {DIFFICULTY_LABEL[m.recipe.difficulty]} ·{" "}
                      {m.recipe.ratingCount ? `★ ${Number(m.recipe.ratingAvg).toFixed(1)}` : "unrated"}
                    </p>
                    <p className="flex flex-wrap items-center gap-1 text-xs">
                      <CheckCircle2 aria-hidden className="h-3.5 w-3.5 text-herb" />
                      <span className="sr-only">You have:</span>
                      {m.matchedIngredients.map((i) => i.name).join(", ") || "—"}
                    </p>
                    {m.missingIngredients.length > 0 && (
                      <p className="flex flex-wrap items-center gap-1 text-xs text-danger">
                        <CircleDashed aria-hidden className="h-3.5 w-3.5" /> Missing: {m.missingIngredients.map((i) => i.name).join(", ")}
                      </p>
                    )}
                    {m.optionalIngredients.length > 0 && (
                      <p className="text-xs text-muted">Optional: {m.optionalIngredients.map((i) => i.name + (i.have ? " ✓" : "")).join(", ")}</p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination page={result.results.page} totalPages={result.results.totalPages} onChange={(p) => match.mutate(p)} />
          </section>
        )
      ) : null}
    </div>
  );
}
