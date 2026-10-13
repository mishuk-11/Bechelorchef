"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Target, Trash2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { RequireAuth } from "@/components/layout/guards";
import { Button } from "@/components/ui/button";
import { Badge, Card } from "@/components/ui/display";
import { Field, Input, Select } from "@/components/ui/field";
import { Alert, EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError, errorMessage } from "@/lib/api/client";
import { catalogApi, personalApi } from "@/lib/api/endpoints";
import { currentMonth } from "@/lib/format";
import { goalSchema, type GoalForm } from "@/schemas";

function NewGoal() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const tags = useQuery({ queryKey: ["tags"], queryFn: catalogApi.tags, staleTime: 300_000 });
  const form = useForm<GoalForm>({
    resolver: zodResolver(goalSchema),
    defaultValues: { title: "", targetCount: 5, month: currentMonth(), countMode: "UNIQUE_RECIPES", tagId: null, maxTotalTimeMins: null },
  });
  const errors = form.formState.errors;
  const create = useMutation({
    mutationFn: (v: GoalForm) => personalApi.createGoal({ ...v, tagId: v.tagId || null, maxTotalTimeMins: v.maxTotalTimeMins || null }),
    onSuccess: () => {
      notify("Goal created.");
      form.reset({ ...form.getValues(), title: "" });
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
    onError: (e) => {
      if (e instanceof ApiError) Object.entries(e.fieldErrors).forEach(([k, m]) => form.setError(k as keyof GoalForm, { message: m }));
      notify(errorMessage(e), "error");
    },
  });
  // Empty or untouched optional numeric inputs must become null (Number(null) would be 0 and fail validation).
  const numOrNull = { setValueAs: (v: unknown) => (v === "" || v === null || v === undefined ? null : Number(v)) };
  return (
    <Card>
      <h2 className="mb-4 text-lg font-semibold">New monthly goal</h2>
      <form onSubmit={form.handleSubmit((v) => create.mutate(v))} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Field label="Goal" htmlFor="goal-title" required error={errors.title?.message} className="sm:col-span-2">
          <Input id="goal-title" placeholder="Cook 5 new dinner recipes this month" {...form.register("title")} />
        </Field>
        <Field label="Target (1–30)" htmlFor="goal-target" required error={errors.targetCount?.message}>
          <Input id="goal-target" type="number" min={1} max={30} {...form.register("targetCount", { valueAsNumber: true })} />
        </Field>
        <Field label="Month" htmlFor="goal-month" required error={errors.month?.message}>
          <Input id="goal-month" type="month" min={currentMonth()} {...form.register("month")} />
        </Field>
        <Field label="Count" htmlFor="goal-mode" hint="Distinct recipes counts each recipe once per month.">
          <Select id="goal-mode" {...form.register("countMode")}>
            <option value="UNIQUE_RECIPES">Distinct recipes cooked</option>
            <option value="COOK_EVENTS">Every cook</option>
          </Select>
        </Field>
        <Field label="Only recipes tagged (optional)" htmlFor="goal-tag">
          <Select id="goal-tag" {...form.register("tagId", numOrNull)}>
            <option value="">Any recipe</option>
            {tags.data?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </Select>
        </Field>
        <Field label="Only recipes up to (minutes, optional)" htmlFor="goal-time" error={errors.maxTotalTimeMins?.message}>
          <Input id="goal-time" type="number" min={1} max={60} {...form.register("maxTotalTimeMins", numOrNull)} />
        </Field>
        <div className="sm:col-span-2">
          <Button type="submit" loading={create.isPending} data-testid="create-goal">Create goal</Button>
        </div>
      </form>
    </Card>
  );
}

function GoalList() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const goals = useQuery({ queryKey: ["goals"], queryFn: personalApi.goals });
  const remove = useMutation({
    mutationFn: (id: number) => personalApi.deleteGoal(id),
    onSuccess: () => {
      notify("Goal deleted.");
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  if (goals.isPending) return <LoadingBlock />;
  if (goals.isError) return <ErrorState message={errorMessage(goals.error)} onRetry={() => goals.refetch()} />;
  if (goals.data.length === 0) {
    return <EmptyState icon={<Target className="h-8 w-8" />} title="No goals yet" description="Set a monthly goal and every recipe you log will count towards it." />;
  }
  return (
    <ul className="grid gap-4 md:grid-cols-2" data-testid="goal-list">
      {goals.data.map((g) => (
        <li key={g.id} className="flex flex-col gap-3 rounded-2xl border border-line bg-surface p-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="font-semibold">{g.title}</p>
              <p className="text-xs text-muted">
                {g.month} · {g.countMode === "UNIQUE_RECIPES" ? "distinct recipes" : "every cook"}
                {g.tag ? ` · #${g.tag.name}` : ""}{g.maxTotalTimeMins ? ` · ≤ ${g.maxTotalTimeMins} min` : ""}
              </p>
            </div>
            {g.completed ? <Badge tone="herb"><CheckCircle2 aria-hidden className="h-3 w-3" /> Complete</Badge> : <Badge>{g.percent}%</Badge>}
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-sunken" role="progressbar" aria-valuemin={0} aria-valuemax={g.targetCount}
            aria-valuenow={Math.min(g.progress, g.targetCount)} aria-label={`${g.title} progress`}>
            <div className="h-full rounded-full bg-herb" style={{ width: `${g.percent}%` }} />
          </div>
          <div className="flex items-center justify-between text-sm">
            <span data-testid="goal-progress">{g.progress} / {g.targetCount}</span>
            <Button variant="ghost" size="sm" onClick={() => confirm("Delete this goal?") && remove.mutate(g.id)} icon={<Trash2 aria-hidden className="h-4 w-4" />}>Delete</Button>
          </div>
        </li>
      ))}
    </ul>
  );
}

export default function GoalsPage() {
  return (
    <RequireAuth>
      <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-8">
        <h1 className="text-2xl font-bold">Cooking goals</h1>
        <Alert tone="info">Progress is calculated from your Recipes Done log for that month (Asia/Dhaka time), so edits and deletions update it automatically.</Alert>
        <NewGoal />
        <GoalList />
      </div>
    </RequireAuth>
  );
}
