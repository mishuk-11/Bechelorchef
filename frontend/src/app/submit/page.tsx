"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { RequireAuth } from "@/components/layout/guards";
import { useToast } from "@/components/ui/toast";
import { RecipeEditor } from "@/features/recipes/recipe-editor";
import { recipeApi } from "@/lib/api/endpoints";
import type { RecipeRequest } from "@/types/api";

function SubmitInner() {
  const router = useRouter();
  const qc = useQueryClient();
  const { notify } = useToast();
  const create = useMutation({ mutationFn: (body: RecipeRequest) => recipeApi.create(body) });
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-8">
      <header>
        <h1 className="text-2xl font-bold">Share a recipe</h1>
        <p className="text-muted">Quick meals only: total time (prep + cook) up to 60 minutes.</p>
      </header>
      <RecipeEditor mode="community" saving={create.isPending}
        onSave={async (body) => {
          const created = await create.mutateAsync(body);
          qc.invalidateQueries({ queryKey: ["my-recipes"] });
          notify(body.submit ? "Submitted! A moderator will review your recipe." : "Draft saved.");
          router.push(`/recipes/${created.recipe.id}`);
        }} />
    </div>
  );
}

export default function SubmitPage() {
  return (
    <RequireAuth>
      <SubmitInner />
    </RequireAuth>
  );
}
