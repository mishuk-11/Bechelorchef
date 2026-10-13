"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useToast } from "@/components/ui/toast";
import { RecipeEditor } from "@/features/recipes/recipe-editor";
import { adminApi } from "@/lib/api/endpoints";
import type { RecipeRequest } from "@/types/api";

export default function NewOfficialRecipePage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { notify } = useToast();
  const create = useMutation({ mutationFn: (body: RecipeRequest) => adminApi.createOfficial(body) });
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">New official recipe</h1>
      <RecipeEditor mode="official" saving={create.isPending}
        onSave={async (body) => {
          const created = await create.mutateAsync(body);
          qc.invalidateQueries({ queryKey: ["admin"] });
          notify(body.submit ? "Official recipe published." : "Draft saved.");
          router.push(`/recipes/${created.recipe.id}`);
        }} />
    </div>
  );
}
