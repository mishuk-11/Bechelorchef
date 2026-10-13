"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Controller, useFieldArray, useForm, useWatch, type FieldErrors } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/display";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { Alert } from "@/components/ui/states";
import { ImageField } from "@/features/media/image-field";
import { ApiError, errorMessage } from "@/lib/api/client";
import { catalogApi } from "@/lib/api/endpoints";
import { TIME_CATEGORY_LABEL, formatBdt, timeCategory } from "@/lib/format";
import { recipeSchema, type RecipeForm } from "@/schemas";
import type { RecipeDetail, RecipeRequest } from "@/types/api";
import { IngredientSearch } from "./ingredient-picker";

export function toForm(detail?: RecipeDetail): RecipeForm {
  if (!detail) {
    return {
      title: "", description: "", prepTimeMins: 5, cookTimeMins: 10, difficulty: "EASY", estimatedCostBdt: 0, baseServings: 1,
      imageUrl: "", imageAssetId: null, tags: [], ingredients: [], steps: [{ instruction: "", imageUrl: "", imageAssetId: null }],
    };
  }
  const r = detail.recipe;
  return {
    title: r.title, description: detail.description ?? "", prepTimeMins: r.prepTimeMins, cookTimeMins: r.cookTimeMins,
    difficulty: r.difficulty, estimatedCostBdt: Number(r.estimatedCostBdt), baseServings: r.baseServings,
    imageUrl: r.imageUrl ?? "", imageAssetId: detail.imageAssetId, tags: detail.tags.map((t) => t.name),
    ingredients: detail.ingredients.map((i) => ({ ingredientId: i.ingredientId, name: i.name, quantity: Number(i.quantity), unit: i.unit, optional: i.optional })),
    steps: detail.steps.map((s) => ({ instruction: s.instruction, imageUrl: s.imageUrl ?? "", imageAssetId: null })),
  };
}

function toRequest(v: RecipeForm, submit: boolean): RecipeRequest {
  return {
    title: v.title.trim(), description: v.description?.trim() || null, prepTimeMins: v.prepTimeMins, cookTimeMins: v.cookTimeMins,
    difficulty: v.difficulty, estimatedCostBdt: v.estimatedCostBdt, baseServings: v.baseServings,
    imageUrl: v.imageUrl?.trim() || null, imageAssetId: v.imageAssetId ?? null, tags: v.tags,
    ingredients: v.ingredients.map((i) => ({ ingredientId: i.ingredientId, quantity: i.quantity, unit: i.unit.trim(), optional: i.optional })),
    steps: v.steps.map((s) => ({ instruction: s.instruction.trim(), imageUrl: s.imageUrl?.trim() || null, imageAssetId: s.imageAssetId ?? null })),
    submit,
  };
}

/** Maps server field paths like "ingredients[0].quantity" onto the form. */
function applyServerErrors(form: ReturnType<typeof useForm<RecipeForm>>, error: unknown) {
  if (!(error instanceof ApiError)) return;
  Object.entries(error.fieldErrors).forEach(([path, message]) => {
    const name = path.replace(/\[(\d+)]/g, ".$1").replace(/\.ingredient$/, ".ingredientId").replace("totalTimeMins", "cookTimeMins");
    form.setError(name as never, { message });
  });
}

function firstError(errors: FieldErrors<RecipeForm>): string | undefined {
  const root = errors.ingredients?.root?.message ?? errors.ingredients?.message ?? errors.steps?.root?.message ?? errors.steps?.message;
  return root;
}

export function RecipeEditor({ initial, mode, onSave, saving }: {
  initial?: RecipeDetail;
  mode: "community" | "official";
  onSave: (request: RecipeRequest) => Promise<unknown>;
  saving: boolean;
}) {
  const form = useForm<RecipeForm>({ resolver: zodResolver(recipeSchema), defaultValues: toForm(initial), mode: "onBlur" });
  const { register, control, handleSubmit, formState: { errors } } = form;
  const ingredients = useFieldArray({ control, name: "ingredients" });
  const steps = useFieldArray({ control, name: "steps" });
  const tags = useQuery({ queryKey: ["tags"], queryFn: catalogApi.tags, staleTime: 300_000 });
  const [serverError, setServerError] = useState<string>();
  const [submitIntent, setSubmitIntent] = useState(true);

  const [prepRaw, cookRaw, costRaw, selectedTags] = useWatch({ control, name: ["prepTimeMins", "cookTimeMins", "estimatedCostBdt", "tags"] });
  const prep = Number(prepRaw) || 0;
  const cook = Number(cookRaw) || 0;
  const total = prep + cook;
  const category = timeCategory(total);
  const num = { valueAsNumber: true } as const;

  async function onValid(values: RecipeForm) {
    setServerError(undefined);
    try {
      await onSave(toRequest(values, submitIntent));
    } catch (e) {
      applyServerErrors(form, e);
      setServerError(errorMessage(e));
    }
  }

  return (
    <form onSubmit={handleSubmit(onValid)} className="flex flex-col gap-6" noValidate data-testid="recipe-editor">
      {serverError && <Alert tone="error">{serverError}</Alert>}
      <Card className="grid gap-4 md:grid-cols-2">
        <Field label="Title" htmlFor="title" required error={errors.title?.message} className="md:col-span-2">
          <Input id="title" maxLength={150} invalid={!!errors.title} {...register("title")} />
        </Field>
        <Field label="Description" htmlFor="description" error={errors.description?.message} className="md:col-span-2">
          <Textarea id="description" maxLength={5000} {...register("description")} />
        </Field>
        <Field label="Prep time (min)" htmlFor="prep" required hint="0–60" error={errors.prepTimeMins?.message}>
          <Input id="prep" type="number" min={0} max={60} invalid={!!errors.prepTimeMins} {...register("prepTimeMins", num)} />
        </Field>
        <Field label="Cook time (min)" htmlFor="cook" required hint="1–60; prep + cook ≤ 60" error={errors.cookTimeMins?.message}>
          <Input id="cook" type="number" min={1} max={60} invalid={!!errors.cookTimeMins} {...register("cookTimeMins", num)} />
        </Field>
        <p className="text-sm md:col-span-2" aria-live="polite">
          Total time: <strong>{total} min</strong>{" "}
          {category ? <span className="text-muted">({TIME_CATEGORY_LABEL[category]})</span> : <span className="text-danger">(must be 1–60 min)</span>}
        </p>
        <Field label="Difficulty" htmlFor="difficulty" required>
          <Select id="difficulty" {...register("difficulty")}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </Select>
        </Field>
        <Field label="Base servings" htmlFor="servings" required hint="1–20" error={errors.baseServings?.message}>
          <Input id="servings" type="number" min={1} max={20} invalid={!!errors.baseServings} {...register("baseServings", num)} />
        </Field>
        <Field label="Estimated total cost (৳ BDT)" htmlFor="cost" required hint={`For the base servings. Now: ${formatBdt(Number(costRaw) || 0)}`} error={errors.estimatedCostBdt?.message}>
          <Input id="cost" type="number" min={0} step="0.01" invalid={!!errors.estimatedCostBdt} {...register("estimatedCostBdt", num)} />
        </Field>
        <div className="md:col-span-2">
          <Controller control={control} name="imageUrl" render={({ field }) => (
            <ImageField label="Main image" purpose="RECIPE" url={field.value ?? ""} error={errors.imageUrl?.message}
              onChange={(url, assetId) => {
                field.onChange(url);
                form.setValue("imageAssetId", assetId);
              }} />
          )} />
        </div>
        <fieldset className="md:col-span-2">
          <legend className="mb-2 text-sm font-medium">Tags</legend>
          <div className="flex flex-wrap gap-2">
            {tags.data?.map((t) => {
              const on = selectedTags.includes(t.name);
              return (
                <button key={t.id} type="button" aria-pressed={on}
                  onClick={() => form.setValue("tags", on ? selectedTags.filter((x) => x !== t.name) : [...selectedTags, t.name], { shouldDirty: true })}
                  className={`rounded-full px-3 py-1 text-sm ring-1 ${on ? "bg-brand text-white ring-brand" : "bg-surface ring-line hover:bg-sunken"}`}>
                  {t.name}
                </button>
              );
            })}
          </div>
          {errors.tags && <p role="alert" className="mt-1 text-xs text-danger">{errors.tags.message}</p>}
        </fieldset>
      </Card>

      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Ingredients</h2>
        <IngredientSearch inputId="add-ingredient" label="Add an ingredient" excludeIds={ingredients.fields.map((f) => f.ingredientId)}
          onSelect={(i) => ingredients.append({ ingredientId: i.id, name: i.name, quantity: 1, unit: i.defaultUnit ?? "pcs", optional: false })} />
        {ingredients.fields.length === 0 && <p className="text-sm text-muted">Search above to add at least one ingredient.</p>}
        <ul className="flex flex-col gap-3">
          {ingredients.fields.map((f, i) => (
            <li key={f.id} className="grid grid-cols-2 items-end gap-2 rounded-xl bg-canvas p-3 sm:grid-cols-[1.5fr_0.7fr_0.7fr_auto_auto]" data-testid="ingredient-row">
              <p className="col-span-2 self-center font-medium sm:col-span-1">{f.name ?? `Ingredient #${f.ingredientId}`}
                {errors.ingredients?.[i]?.ingredientId && <span role="alert" className="block text-xs text-danger">{errors.ingredients[i]?.ingredientId?.message}</span>}
              </p>
              <Field label="Qty" htmlFor={`qty-${i}`} error={errors.ingredients?.[i]?.quantity?.message}>
                <Input id={`qty-${i}`} type="number" step="0.001" min={0} {...register(`ingredients.${i}.quantity`, num)} />
              </Field>
              <Field label="Unit" htmlFor={`unit-${i}`} error={errors.ingredients?.[i]?.unit?.message}>
                <Input id={`unit-${i}`} maxLength={20} {...register(`ingredients.${i}.unit`)} />
              </Field>
              <Checkbox id={`opt-${i}`} label="Optional" {...register(`ingredients.${i}.optional`)} />
              <Button variant="ghost" aria-label={`Remove ${f.name ?? "ingredient"}`} onClick={() => ingredients.remove(i)}>
                <Trash2 aria-hidden className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
        {firstError(errors) && errors.ingredients && <p role="alert" className="text-sm text-danger">{errors.ingredients.root?.message ?? errors.ingredients.message}</p>}
      </Card>

      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Steps</h2>
        <ol className="flex flex-col gap-3">
          {steps.fields.map((f, i) => (
            <li key={f.id} className="flex flex-col gap-2 rounded-xl bg-canvas p-3" data-testid="step-row">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold">Step {i + 1}</span>
                <div className="flex gap-1">
                  <Button variant="ghost" size="sm" aria-label={`Move step ${i + 1} up`} disabled={i === 0} onClick={() => steps.move(i, i - 1)}><ArrowUp aria-hidden className="h-4 w-4" /></Button>
                  <Button variant="ghost" size="sm" aria-label={`Move step ${i + 1} down`} disabled={i === steps.fields.length - 1} onClick={() => steps.move(i, i + 1)}><ArrowDown aria-hidden className="h-4 w-4" /></Button>
                  <Button variant="ghost" size="sm" aria-label={`Remove step ${i + 1}`} disabled={steps.fields.length === 1} onClick={() => steps.remove(i)}><Trash2 aria-hidden className="h-4 w-4" /></Button>
                </div>
              </div>
              <Field label="Instruction" htmlFor={`step-${i}`} error={errors.steps?.[i]?.instruction?.message}>
                <Textarea id={`step-${i}`} className="min-h-16" maxLength={2000} {...register(`steps.${i}.instruction`)} />
              </Field>
              <Controller control={control} name={`steps.${i}.imageUrl`} render={({ field }) => (
                <ImageField label="Step photo (optional)" purpose="STEP" url={field.value ?? ""} error={errors.steps?.[i]?.imageUrl?.message}
                  onChange={(url, assetId) => {
                    field.onChange(url);
                    form.setValue(`steps.${i}.imageAssetId`, assetId);
                  }} />
              )} />
            </li>
          ))}
        </ol>
        <Button variant="secondary" onClick={() => steps.append({ instruction: "", imageUrl: "", imageAssetId: null })} icon={<Plus aria-hidden className="h-4 w-4" />}
          disabled={steps.fields.length >= 30}>Add step</Button>
        {(errors.steps?.root?.message ?? errors.steps?.message) && <p role="alert" className="text-sm text-danger">{errors.steps?.root?.message ?? errors.steps?.message}</p>}
      </Card>

      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="lg" loading={saving && submitIntent} onClick={() => setSubmitIntent(true)} data-testid="save-recipe-submit">
          {mode === "official" ? "Publish" : "Submit for review"}
        </Button>
        {(!initial || initial.recipe.status !== "APPROVED") && (
          <Button type="submit" size="lg" variant="secondary" loading={saving && !submitIntent} onClick={() => setSubmitIntent(false)}>
            Save as draft
          </Button>
        )}
      </div>
      {mode === "community" && (
        <p className="text-xs text-muted">New and materially edited community recipes are reviewed by a moderator before they become public.</p>
      )}
    </form>
  );
}
