"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Modal, Pagination, TableWrap } from "@/components/ui/display";
import { Field, Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError, errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import type { Ingredient } from "@/types/api";

function IngredientModal({ ingredient, categories, onClose }: { ingredient: Ingredient | "new"; categories: string[]; onClose: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const existing = ingredient === "new" ? null : ingredient;
  const [name, setName] = useState(existing?.name ?? "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [unit, setUnit] = useState(existing?.defaultUnit ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const save = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), category: category.trim() || undefined, defaultUnit: unit.trim() || undefined };
      return existing ? adminApi.updateIngredient(existing.id, body) : adminApi.createIngredient(body);
    },
    onSuccess: () => {
      notify(existing ? "Ingredient updated. Existing recipes keep their link." : "Ingredient added.");
      qc.invalidateQueries({ queryKey: ["admin", "ingredients"] });
      onClose();
    },
    onError: (e) => setErrors(e instanceof ApiError && Object.keys(e.fieldErrors).length ? e.fieldErrors : { name: errorMessage(e) }),
  });
  return (
    <Modal open onClose={onClose} title={existing ? `Edit ${existing.name}` : "Add ingredient"}
      footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={save.isPending} disabled={!name.trim()} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="flex flex-col gap-3">
        <Field label="Name" htmlFor="ing-name" required error={errors.name} hint="Names are unique ignoring case and spacing.">
          <Input id="ing-name" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Category" htmlFor="ing-category">
          <Input id="ing-category" list="ing-categories" value={category} maxLength={40} onChange={(e) => setCategory(e.target.value)} />
          <datalist id="ing-categories">{categories.map((c) => <option key={c} value={c} />)}</datalist>
        </Field>
        <Field label="Default unit" htmlFor="ing-unit">
          <Input id="ing-unit" value={unit} maxLength={20} onChange={(e) => setUnit(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}

export default function AdminIngredientsPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Ingredient | "new" | null>(null);
  const categories = useQuery({ queryKey: ["admin", "ingredient-categories"], queryFn: adminApi.categories });
  const list = useQuery({
    queryKey: ["admin", "ingredients", query, category, page],
    queryFn: () => adminApi.ingredients({ query: query || undefined, category: category || undefined, page }),
  });
  const toggle = useMutation({
    mutationFn: (i: Ingredient) => adminApi.setIngredientActive(i.id, !i.active),
    onSuccess: (i) => {
      notify(i.active ? `${i.name} reactivated.` : `${i.name} deactivated. Existing recipes keep it.`);
      qc.invalidateQueries({ queryKey: ["admin", "ingredients"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  function search(e: FormEvent) {
    e.preventDefault();
    setQuery(text.trim());
    setPage(0);
  }
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Ingredient catalogue</h1>
        <Button onClick={() => setEditing("new")} icon={<Plus aria-hidden className="h-4 w-4" />}>Add ingredient</Button>
      </div>
      <div className="flex flex-wrap gap-3">
        <form onSubmit={search} role="search" className="flex gap-2">
          <label htmlFor="ing-search" className="sr-only">Search ingredients</label>
          <Input id="ing-search" value={text} onChange={(e) => setText(e.target.value)} placeholder="Search" className="w-56" />
          <Button type="submit" variant="secondary">Search</Button>
        </form>
        <label htmlFor="ing-filter" className="sr-only">Category</label>
        <Select id="ing-filter" value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }} className="w-48">
          <option value="">All categories</option>
          {categories.data?.map((c) => <option key={c} value={c}>{c}</option>)}
        </Select>
      </div>
      {list.isPending ? <LoadingBlock /> : list.isError ? <ErrorState message={errorMessage(list.error)} onRetry={() => list.refetch()} />
        : list.data.items.length === 0 ? <EmptyState title="No ingredients found" /> : (
          <>
            <TableWrap label="Ingredients">
              <thead><tr><th>Name</th><th>Category</th><th>Default unit</th><th>Status</th><th>Actions</th></tr></thead>
              <tbody>
                {list.data.items.map((i) => (
                  <tr key={i.id}>
                    <td className="font-medium">{i.name}</td>
                    <td>{i.category ?? "—"}</td>
                    <td>{i.defaultUnit ?? "—"}</td>
                    <td>{i.active ? <Badge tone="herb">Active</Badge> : <Badge tone="neutral">Inactive</Badge>}</td>
                    <td className="flex gap-1">
                      <Button size="sm" variant="secondary" onClick={() => setEditing(i)}>Edit</Button>
                      <Button size="sm" variant="ghost" loading={toggle.isPending && toggle.variables?.id === i.id} onClick={() => toggle.mutate(i)}>
                        {i.active ? "Deactivate" : "Activate"}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </TableWrap>
            <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        )}
      {editing && <IngredientModal ingredient={editing} categories={categories.data ?? []} onClose={() => setEditing(null)} />}
    </div>
  );
}
