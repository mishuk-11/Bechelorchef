"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChefHat, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { RequireAuth } from "@/components/layout/guards";
import { Button, ButtonLink } from "@/components/ui/button";
import { Modal, Pagination, StatTile } from "@/components/ui/display";
import { Field, Input, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api/client";
import { personalApi } from "@/lib/api/endpoints";
import { formatDate } from "@/lib/format";
import type { HistoryEntry } from "@/types/api";

/** datetime-local value in the browser's zone for an ISO instant. */
function toLocalInput(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function EditEntry({ entry, onClose }: { entry: HistoryEntry; onClose: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [when, setWhen] = useState(toLocalInput(entry.cookedOn));
  const [notes, setNotes] = useState(entry.notes ?? "");
  const [error, setError] = useState<string>();
  const save = useMutation({
    mutationFn: () => personalApi.updateHistory(entry.id, { cookedOn: new Date(when).toISOString(), notes }),
    onSuccess: () => {
      notify("Entry updated.");
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: ["goals"] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  return (
    <Modal open onClose={onClose} title={`Edit: ${entry.recipe.title}`}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="flex flex-col gap-4">
        <Field label="Cooked on" htmlFor="cooked-on" error={error}>
          <Input id="cooked-on" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
        </Field>
        <Field label="Notes" htmlFor="history-notes">
          <Textarea id="history-notes" value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}

function History() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<HistoryEntry | null>(null);
  const qc = useQueryClient();
  const { notify } = useToast();
  const history = useQuery({ queryKey: ["history", page], queryFn: () => personalApi.history(page) });
  const remove = useMutation({
    mutationFn: (id: number) => personalApi.deleteHistory(id),
    onSuccess: () => {
      notify("Entry deleted.");
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  if (history.isPending) return <LoadingBlock />;
  if (history.isError) return <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />;
  const data = history.data;
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2">
        <StatTile label="Times cooked" value={data.totalCookEvents} />
        <StatTile label="Different recipes" value={data.distinctRecipes} />
      </div>
      {data.page.items.length === 0 ? (
        <EmptyState icon={<ChefHat className="h-8 w-8" />} title="Nothing logged yet"
          description="Press “I cooked this” on any recipe to start your Recipes Done list."
          action={<ButtonLink href="/match">Find something to cook</ButtonLink>} />
      ) : (
        <ul className="flex flex-col gap-3" data-testid="history-list">
          {data.page.items.map((h) => (
            <li key={h.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-line bg-surface p-4">
              <div className="min-w-0 flex-1">
                {h.recipe.available ? (
                  <Link href={`/recipes/${h.recipe.id}`} className="font-semibold hover:text-brand">{h.recipe.title}</Link>
                ) : (
                  <span className="font-semibold">{h.recipe.title} <span className="text-xs font-normal text-muted">(no longer public)</span></span>
                )}
                <p className="text-sm text-muted">{formatDate(h.cookedOn, true)} · {h.recipe.totalTimeMins} min</p>
                {h.notes && <p className="mt-1 text-sm">{h.notes}</p>}
              </div>
              <Button variant="ghost" size="sm" onClick={() => setEditing(h)} icon={<Pencil aria-hidden className="h-4 w-4" />}>Edit</Button>
              <Button variant="ghost" size="sm" loading={remove.isPending && remove.variables === h.id}
                onClick={() => confirm("Delete this entry? Goal progress will update.") && remove.mutate(h.id)}
                icon={<Trash2 aria-hidden className="h-4 w-4" />}>Delete</Button>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={data.page.page} totalPages={data.page.totalPages} onChange={setPage} />
      {editing && <EditEntry entry={editing} onClose={() => setEditing(null)} />}
    </>
  );
}

export default function CookingHistoryPage() {
  return (
    <RequireAuth>
      <div className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-8">
        <h1 className="text-2xl font-bold">Recipes Done</h1>
        <History />
      </div>
    </RequireAuth>
  );
}
