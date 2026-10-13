"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen } from "lucide-react";
import { useState } from "react";
import { RequireAuth } from "@/components/layout/guards";
import { Button, ButtonLink } from "@/components/ui/button";
import { Pagination } from "@/components/ui/display";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { RecipeCard, RecipeGrid } from "@/features/recipes/recipe-card";
import { errorMessage } from "@/lib/api/client";
import { personalApi } from "@/lib/api/endpoints";
import { formatDate } from "@/lib/format";

function Cookbook() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const { notify } = useToast();
  const saved = useQuery({ queryKey: ["saved", page], queryFn: () => personalApi.saved(page) });
  const unsave = useMutation({
    mutationFn: (id: number) => personalApi.unsave(id),
    onSuccess: () => {
      notify("Removed from your cookbook.");
      qc.invalidateQueries({ queryKey: ["saved"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-bold">My cookbook</h1>
      {saved.isPending ? (
        <RecipeGrid>{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-72" />)}</RecipeGrid>
      ) : saved.isError ? (
        <ErrorState message={errorMessage(saved.error)} onRetry={() => saved.refetch()} />
      ) : saved.data.items.length === 0 ? (
        <EmptyState icon={<BookOpen className="h-8 w-8" />} title="Your cookbook is empty"
          description="Save recipes you want to cook later and they will appear here."
          action={<ButtonLink href="/recipes">Browse recipes</ButtonLink>} />
      ) : (
        <>
          <RecipeGrid>
            {saved.data.items.map((s) => (
              <RecipeCard key={s.recipe.id} recipe={s.recipe} showStatus
                footer={
                  <div className="flex items-center justify-between text-xs text-muted">
                    <span>Saved {formatDate(s.savedAt)}</span>
                    <Button variant="ghost" size="sm" loading={unsave.isPending && unsave.variables === s.recipe.id} onClick={() => unsave.mutate(s.recipe.id)}>Remove</Button>
                  </div>
                } />
            ))}
          </RecipeGrid>
          <Pagination page={saved.data.page} totalPages={saved.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export default function CookbookPage() {
  return (
    <RequireAuth>
      <Cookbook />
    </RequireAuth>
  );
}
