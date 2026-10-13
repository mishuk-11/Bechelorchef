"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/display";
import { Field, Textarea } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { RecipeSummary } from "@/types/api";

type Action = "approve" | "reject" | "archive" | "restore";

/** Moderation buttons valid for the recipe's current status; reject requires a recorded reason. */
export function RecipeActions({ recipe }: { recipe: RecipeSummary }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [pending, setPending] = useState<Action | null>(null);
  const [reason, setReason] = useState("");
  const run = useMutation({
    mutationFn: ({ action, why }: { action: Action; why?: string }) => {
      switch (action) {
        case "approve": return adminApi.approve(recipe.id, why);
        case "reject": return adminApi.reject(recipe.id, why ?? "");
        case "archive": return adminApi.archive(recipe.id, why);
        case "restore": return adminApi.restore(recipe.id);
      }
    },
    onSuccess: (_, { action }) => {
      notify(`Recipe ${action === "approve" ? "approved" : action + "d"}.`);
      setPending(null);
      setReason("");
      qc.invalidateQueries({ queryKey: ["admin"] });
      qc.invalidateQueries({ queryKey: ["recipes"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  const actions: Action[] = recipe.status === "ARCHIVED" ? ["restore"]
    : recipe.status === "APPROVED" ? ["archive"]
    : recipe.status === "PENDING" ? ["approve", "reject", "archive"]
    : ["approve", "archive"];

  return (
    <div className="flex flex-wrap gap-1">
      {actions.map((a) => (
        <Button key={a} size="sm" variant={a === "approve" ? "success" : a === "reject" ? "danger" : "secondary"}
          data-testid={`recipe-${a}-${recipe.id}`}
          onClick={() => (a === "restore" ? run.mutate({ action: a }) : setPending(a))}>
          {a[0].toUpperCase() + a.slice(1)}
        </Button>
      ))}
      <Modal open={pending !== null} onClose={() => setPending(null)} title={`${pending ? pending[0].toUpperCase() + pending.slice(1) : ""}: ${recipe.title}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setPending(null)}>Cancel</Button>
            <Button loading={run.isPending} disabled={pending === "reject" && !reason.trim()}
              variant={pending === "reject" ? "danger" : "primary"} onClick={() => pending && run.mutate({ action: pending, why: reason.trim() || undefined })}>
              Confirm
            </Button>
          </>
        }>
        <Field label={pending === "reject" ? "Reason (shown to the author)" : "Note (optional)"} htmlFor={`reason-${recipe.id}`} required={pending === "reject"}>
          <Textarea id={`reason-${recipe.id}`} value={reason} maxLength={1000} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </Modal>
    </div>
  );
}
