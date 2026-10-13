"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, Bookmark, BookmarkCheck, ChefHat, Clock, Minus, Pencil, Plus, Trash2, Users } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Badge, Card, Modal, Stars } from "@/components/ui/display";
import { Field, Textarea } from "@/components/ui/field";
import { Alert, EmptyState, ErrorState, LoadingBlock, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/features/auth/auth-provider";
import { RecipeImage } from "@/features/recipes/recipe-card";
import { ReportButton } from "@/features/recipes/report-button";
import { ReviewSection } from "@/features/recipes/review-section";
import { ApiError, errorMessage } from "@/lib/api/client";
import { personalApi, recipeApi } from "@/lib/api/endpoints";
import { DIFFICULTY_LABEL, STATUS_LABEL, formatBdt, formatQuantity, safeHttpsUrl } from "@/lib/format";
import type { RecipeDetail } from "@/types/api";

function PortionPanel({ detail }: { detail: RecipeDetail }) {
  const base = detail.recipe.baseServings;
  const [servings, setServings] = useState(base);
  const scaled = useQuery({
    queryKey: ["portions", detail.recipe.id, servings],
    queryFn: () => recipeApi.portions(detail.recipe.id, servings),
    enabled: servings !== base,
    placeholderData: (prev) => prev,
  });
  const atBase = servings === base;
  const lines = atBase
    ? detail.ingredients.map((i) => ({ ingredientId: i.ingredientId, name: i.name, quantity: i.quantity, unit: i.unit, optional: i.optional }))
    : scaled.data?.ingredients ?? [];
  const total = atBase ? detail.recipe.estimatedCostBdt : scaled.data?.estimatedTotalCostBdt;
  const perServing = atBase ? detail.costPerServingBdt : scaled.data?.costPerServingBdt;

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Ingredients</h2>
        <div className="flex items-center gap-2" role="group" aria-label="Servings">
          <Button variant="secondary" size="sm" aria-label="Fewer servings" disabled={servings <= 1} onClick={() => setServings((s) => s - 1)}>
            <Minus aria-hidden className="h-4 w-4" />
          </Button>
          <span className="min-w-24 text-center text-sm" aria-live="polite" data-testid="servings">{servings} serving{servings > 1 ? "s" : ""}</span>
          <Button variant="secondary" size="sm" aria-label="More servings" disabled={servings >= 20} onClick={() => setServings((s) => s + 1)}>
            <Plus aria-hidden className="h-4 w-4" />
          </Button>
        </div>
      </div>
      {scaled.isError ? (
        <ErrorState message={errorMessage(scaled.error)} onRetry={() => scaled.refetch()} />
      ) : !atBase && !scaled.data ? (
        <Skeleton className="h-40" />
      ) : (
        <ul className="divide-y divide-line" aria-busy={scaled.isFetching}>
          {lines.map((l) => (
            <li key={l.ingredientId} className="flex justify-between gap-3 py-2 text-sm">
              <span>
                {l.name}
                {l.optional && <span className="ml-2 text-xs text-muted">(optional)</span>}
              </span>
              <span className="tabular-nums text-muted">{formatQuantity(l.quantity)} {l.unit}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="rounded-xl bg-canvas p-3 text-sm">
        <p>
          Estimated total: <strong data-testid="total-cost">{formatBdt(total)}</strong> · per serving <strong>{formatBdt(perServing)}</strong>
        </p>
        <p className="mt-1 text-xs text-muted">Estimate in BDT scaled linearly from {base} serving{base > 1 ? "s" : ""}; not a live market price.</p>
      </div>
    </Card>
  );
}

function Actions({ detail }: { detail: RecipeDetail }) {
  const { status } = useAuth();
  const router = useRouter();
  const qc = useQueryClient();
  const { notify } = useToast();
  const [cookOpen, setCookOpen] = useState(false);
  const [notes, setNotes] = useState("");
  const id = detail.recipe.id;
  const requireLogin = () => router.push(`/login?next=/recipes/${id}`);

  const toggleSave = useMutation({
    mutationFn: () => (detail.viewer.saved ? personalApi.unsave(id) : personalApi.save(id)),
    onSuccess: () => {
      notify(detail.viewer.saved ? "Removed from your cookbook." : "Saved to your cookbook.");
      qc.invalidateQueries({ queryKey: ["recipe", id] });
      qc.invalidateQueries({ queryKey: ["saved"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  const cook = useMutation({
    mutationFn: () => recipeApi.cook(id, notes.trim() || undefined),
    onSuccess: () => {
      notify("Logged! Nice cooking.");
      setCookOpen(false);
      setNotes("");
      qc.invalidateQueries({ queryKey: ["history"] });
      qc.invalidateQueries({ queryKey: ["goals"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  const withdraw = useMutation({
    mutationFn: () => recipeApi.withdraw(id),
    onSuccess: () => {
      notify("Recipe withdrawn.");
      qc.invalidateQueries({ queryKey: ["recipe", id] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  const authed = status === "authenticated";
  const archived = detail.recipe.status === "ARCHIVED";

  return (
    <div className="flex flex-wrap gap-2">
      {!archived && (
        <Button variant="secondary" loading={toggleSave.isPending} onClick={() => (authed ? toggleSave.mutate() : requireLogin())} data-testid="save-recipe"
          icon={detail.viewer.saved ? <BookmarkCheck aria-hidden className="h-4 w-4" /> : <Bookmark aria-hidden className="h-4 w-4" />}>
          {detail.viewer.saved ? "Saved" : "Save to cookbook"}
        </Button>
      )}
      {!archived && (
        <Button onClick={() => (authed ? setCookOpen(true) : requireLogin())} icon={<ChefHat aria-hidden className="h-4 w-4" />} data-testid="cooked-this">
          I cooked this
        </Button>
      )}
      {detail.viewer.canEdit && (
        <ButtonLink href={`/recipes/${id}/edit`} variant="secondary"><Pencil aria-hidden className="h-4 w-4" /> Edit</ButtonLink>
      )}
      {detail.viewer.isOwner && !archived && (
        <Button variant="ghost" loading={withdraw.isPending} onClick={() => confirm("Withdraw this recipe? It will no longer be public.") && withdraw.mutate()}
          icon={<Trash2 aria-hidden className="h-4 w-4" />}>Withdraw</Button>
      )}
      {authed && !detail.viewer.isOwner && detail.recipe.status === "APPROVED" && <ReportButton targetType="RECIPE" targetId={id} />}
      <Modal open={cookOpen} onClose={() => setCookOpen(false)} title="Log a cook"
        footer={
          <>
            <Button variant="ghost" onClick={() => setCookOpen(false)}>Cancel</Button>
            <Button loading={cook.isPending} onClick={() => cook.mutate()} data-testid="confirm-cook">Save to Recipes Done</Button>
          </>
        }>
        <Field label="Notes (optional)" htmlFor="cook-notes" hint="e.g. added extra chili">
          <Textarea id="cook-notes" value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </Modal>
    </div>
  );
}

export default function RecipeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const recipeId = Number(id);
  const valid = Number.isInteger(recipeId) && recipeId > 0;
  const { status } = useAuth();
  const query = useQuery({
    queryKey: ["recipe", recipeId, status],
    queryFn: () => recipeApi.detail(recipeId),
    enabled: valid && status !== "loading",
  });

  if (!valid || (query.error instanceof ApiError && query.error.status === 404)) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <EmptyState title="Recipe not found" description="It may have been removed or is not published yet."
          action={<ButtonLink href="/recipes" variant="secondary">Browse recipes</ButtonLink>} />
      </div>
    );
  }
  if (query.isPending) return <LoadingBlock label="Loading recipe…" />;
  if (query.isError) return <div className="mx-auto max-w-xl px-4 py-16"><ErrorState message={errorMessage(query.error)} onRetry={() => query.refetch()} /></div>;

  const d = query.data;
  const r = d.recipe;
  return (
    <article className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-8">
      {r.status !== "APPROVED" && (
        <Alert tone={r.status === "REJECTED" ? "error" : "warning"}>
          Status: <strong>{STATUS_LABEL[r.status]}</strong>. Only you{d.viewer.isOwner ? "" : " (admin)"} can see this recipe.
          {d.moderationNote && <> Moderator note: {d.moderationNote}</>}
        </Alert>
      )}
      <div className="grid gap-6 md:grid-cols-2">
        <RecipeImage url={r.imageUrl} title={r.title} className="aspect-[4/3] w-full rounded-2xl" />
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <Badge tone="herb"><Clock aria-hidden className="h-3 w-3" /> {r.timeCategoryLabel}</Badge>
            {r.official ? <Badge tone="brand"><BadgeCheck aria-hidden className="h-3 w-3" /> Official recipe</Badge> : <Badge>Community recipe</Badge>}
            {d.tags.map((t) => (
              <Link key={t.id} href={`/recipes?tagId=${t.id}&maxTotalTimeMins=any`}><Badge tone="neutral">#{t.name}</Badge></Link>
            ))}
          </div>
          <h1 className="text-3xl font-bold" data-testid="recipe-title">{r.title}</h1>
          <p className="text-sm text-muted">{r.official ? "By Bachelor Chef" : `By ${r.author?.displayName ?? "a community cook"}`}</p>
          {r.ratingCount > 0 ? (
            <p className="flex items-center gap-2 text-sm"><Stars value={Number(r.ratingAvg)} /> {Number(r.ratingAvg).toFixed(1)} from {r.ratingCount} review{r.ratingCount > 1 ? "s" : ""}</p>
          ) : (
            <p className="text-sm text-muted">No ratings yet</p>
          )}
          {d.description && <p className="whitespace-pre-line">{d.description}</p>}
          <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            {[
              ["Prep", `${r.prepTimeMins} min`],
              ["Cook", `${r.cookTimeMins} min`],
              ["Total", `${r.totalTimeMins} min`],
              ["Difficulty", DIFFICULTY_LABEL[r.difficulty]],
            ].map(([k, v]) => (
              <div key={k} className="rounded-xl bg-surface p-3 text-center ring-1 ring-line">
                <dt className="text-xs text-muted">{k}</dt>
                <dd className="font-semibold">{v}</dd>
              </div>
            ))}
          </dl>
          <p className="flex items-center gap-2 text-sm"><Users aria-hidden className="h-4 w-4" /> Serves {r.baseServings} · {formatBdt(r.estimatedCostBdt)} estimated</p>
          <Actions detail={d} />
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_1.4fr]">
        <PortionPanel detail={d} />
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Steps</h2>
          <ol className="flex flex-col gap-4">
            {d.steps.map((s) => (
              <li key={s.stepNumber} className="flex gap-3">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-sm font-semibold text-white">{s.stepNumber}</span>
                <div className="flex flex-col gap-2">
                  <p className="whitespace-pre-line text-sm leading-relaxed">{s.instruction}</p>
                  {safeHttpsUrl(s.imageUrl) && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={safeHttpsUrl(s.imageUrl)!} alt={`Step ${s.stepNumber}`} className="max-h-56 rounded-lg object-cover" loading="lazy" referrerPolicy="no-referrer" />
                  )}
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </div>
      <ReviewSection detail={d} />
    </article>
  );
}
