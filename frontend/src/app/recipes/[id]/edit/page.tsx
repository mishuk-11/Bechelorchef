"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { RequireAuth } from "@/components/layout/guards";
import { ButtonLink } from "@/components/ui/button";
import { Alert, EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { RecipeEditor } from "@/features/recipes/recipe-editor";
import { ApiError, errorMessage } from "@/lib/api/client";
import { recipeApi } from "@/lib/api/endpoints";
import type { RecipeRequest } from "@/types/api";

function EditInner() {
  const { id } = useParams<{ id: string }>();
  const recipeId = Number(id);
  const router = useRouter();
  const qc = useQueryClient();
  const { notify } = useToast();
  const detail = useQuery({ queryKey: ["recipe", recipeId, "edit"], queryFn: () => recipeApi.detail(recipeId), enabled: recipeId > 0 });
  const update = useMutation({ mutationFn: (body: RecipeRequest) => recipeApi.update(recipeId, body) });

  if (!(recipeId > 0) || (detail.error instanceof ApiError && detail.error.status === 404)) {
    return <EmptyState title="Recipe not found" action={<ButtonLink href="/profile" variant="secondary">My recipes</ButtonLink>} />;
  }
  if (detail.isPending) return <LoadingBlock />;
  if (detail.isError) return <ErrorState message={errorMessage(detail.error)} onRetry={() => detail.refetch()} />;
  if (!detail.data.viewer.canEdit) {
    return <EmptyState title="You cannot edit this recipe" description="Only the author or an administrator can edit it, and archived recipes are read-only." />;
  }
  const r = detail.data.recipe;
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">Edit “{r.title}”</h1>
      {r.status === "APPROVED" && !r.official && detail.data.viewer.isOwner && (
        <Alert tone="warning">This recipe is published. Changing its content sends it back to moderation until approved again.</Alert>
      )}
      <RecipeEditor initial={detail.data} mode={r.official ? "official" : "community"} saving={update.isPending}
        onSave={async (body) => {
          const saved = await update.mutateAsync(body);
          qc.invalidateQueries({ queryKey: ["recipe", recipeId] });
          notify(saved.recipe.status === "PENDING" ? "Saved and sent for review." : "Recipe saved.");
          router.push(`/recipes/${recipeId}`);
        }} />
    </div>
  );
}

export default function EditRecipePage() {
  return (
    <RequireAuth>
      <div className="mx-auto max-w-4xl px-4 py-8">
        <EditInner />
      </div>
    </RequireAuth>
  );
}
