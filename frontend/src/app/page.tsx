"use client";

import { useQuery } from "@tanstack/react-query";
import { Clock, PiggyBank, Refrigerator, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ButtonLink } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { RecipeCard, RecipeGrid } from "@/features/recipes/recipe-card";
import { errorMessage } from "@/lib/api/client";
import { recipeApi } from "@/lib/api/endpoints";

function RecipeRow({ title, sort, maxTotalTimeMins, testId }: { title: string; sort: string; maxTotalTimeMins?: number; testId: string }) {
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ["recipes", "home", sort, maxTotalTimeMins],
    queryFn: () => recipeApi.search({ sort, maxTotalTimeMins, size: 6 }),
  });
  return (
    <section className="flex flex-col gap-4" data-testid={testId}>
      <h2 className="text-xl font-semibold">{title}</h2>
      {isPending ? (
        <RecipeGrid>{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-72" />)}</RecipeGrid>
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.items.length === 0 ? (
        <EmptyState title="No recipes yet" description="Recipes appear here once they are published." />
      ) : (
        <RecipeGrid>{data.items.map((r) => <RecipeCard key={r.id} recipe={r} />)}</RecipeGrid>
      )}
    </section>
  );
}

export default function HomePage() {
  const router = useRouter();
  const [q, setQ] = useState("");
  function search(e: FormEvent) {
    e.preventDefault();
    router.push(`/recipes${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`);
  }
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-12 px-4 py-10">
      <section className="grid items-center gap-8 rounded-3xl border border-line bg-surface p-6 sm:p-10 md:grid-cols-[1.4fr_1fr]">
        <div className="flex flex-col gap-5">
          <h1 className="text-3xl font-bold leading-tight sm:text-4xl">
            Cook with what you already have — <span className="text-brand">in 20 minutes or less.</span>
          </h1>
          <p className="text-muted">
            Bachelor Chef helps students and busy people in Bangladesh find quick, low-cost meals from the ingredients in their kitchen.
          </p>
          <div className="flex flex-wrap gap-3">
            <ButtonLink href="/match" size="lg" data-testid="find-a-meal">
              <Refrigerator aria-hidden className="h-5 w-5" /> Find a meal
            </ButtonLink>
            <ButtonLink href="/recipes" size="lg" variant="secondary">Browse recipes</ButtonLink>
          </div>
          <form onSubmit={search} role="search" className="flex max-w-md gap-2">
            <label htmlFor="home-search" className="sr-only">Search recipes</label>
            <input id="home-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search e.g. fried rice"
              className="h-11 flex-1 rounded-lg border border-line bg-canvas px-3 text-sm focus:border-brand focus:outline-none" />
            <button type="submit" className="inline-flex h-11 items-center gap-1 rounded-lg border border-line bg-surface px-4 text-sm font-medium hover:bg-sunken">
              <Search aria-hidden className="h-4 w-4" /> Search
            </button>
          </form>
        </div>
        <ul className="grid gap-3 text-sm">
          {[
            { icon: Refrigerator, title: "Use what's in your kitchen", text: "Pick ingredients you have; we rank recipes by how much you can already make." },
            { icon: Clock, title: "Quick by default", text: "Express (≤15 min) and Bachelor Quick (≤20 min) meals first." },
            { icon: PiggyBank, title: "Know the cost", text: "Every recipe shows an estimated cost in ৳ and scales with servings." },
          ].map(({ icon: Icon, title, text }) => (
            <li key={title} className="flex gap-3 rounded-2xl bg-canvas p-4">
              <Icon aria-hidden className="h-6 w-6 shrink-0 text-brand" />
              <div>
                <p className="font-semibold">{title}</p>
                <p className="text-muted">{text}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>
      <RecipeRow title="Quick meals (20 minutes or less)" sort="time" maxTotalTimeMins={20} testId="quick-recipes" />
      <RecipeRow title="Popular with the community" sort="popular" testId="popular-recipes" />
      <RecipeRow title="Newest recipes" sort="newest" testId="newest-recipes" />
    </div>
  );
}
