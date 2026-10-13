"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MessageSquare, Star } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Pagination, Stars } from "@/components/ui/display";
import { Field, Input, Textarea } from "@/components/ui/field";
import { Alert, EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/features/auth/auth-provider";
import { ApiError, errorMessage } from "@/lib/api/client";
import { profileApi, recipeApi, reviewApi } from "@/lib/api/endpoints";
import { formatDate, safeHttpsUrl } from "@/lib/format";
import { reviewSchema, type ReviewForm } from "@/schemas";
import type { RecipeDetail } from "@/types/api";
import { ReportButton } from "./report-button";

function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div role="radiogroup" aria-label="Rating" className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n} star${n > 1 ? "s" : ""}`}
          onClick={() => onChange(n)} className="rounded p-0.5" data-testid={`rating-${n}`}>
          <Star aria-hidden className={`h-7 w-7 ${n <= value ? "fill-saffron text-saffron" : "text-line"}`} />
        </button>
      ))}
    </div>
  );
}

function ReviewFormPanel({ detail, onDone }: { detail: RecipeDetail; onDone: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const myReviewId = detail.viewer.myReviewId;
  const mine = useQuery({
    queryKey: ["my-reviews"],
    queryFn: () => profileApi.myReviews(0),
    enabled: myReviewId !== null,
  });
  const existing = mine.data?.items.find((r) => r.id === myReviewId);
  const form = useForm<ReviewForm>({
    resolver: zodResolver(reviewSchema),
    values: { rating: existing?.rating ?? 0, comment: existing?.comment ?? "", photoUrl: existing?.photoUrl ?? "" },
  });
  const save = useMutation({
    mutationFn: (v: ReviewForm) => {
      const body = { rating: v.rating, comment: v.comment || undefined, photoUrl: v.photoUrl || null };
      return myReviewId ? reviewApi.update(myReviewId, body) : recipeApi.addReview(detail.recipe.id, body);
    },
    onSuccess: () => {
      notify("Thanks! Your review was submitted and will appear after moderation.");
      qc.invalidateQueries({ queryKey: ["recipe", detail.recipe.id] });
      qc.invalidateQueries({ queryKey: ["my-reviews"] });
      onDone();
    },
    onError: (e) => {
      if (e instanceof ApiError) Object.entries(e.fieldErrors).forEach(([k, m]) => form.setError(k as keyof ReviewForm, { message: m }));
      notify(errorMessage(e), "error");
    },
  });
  const remove = useMutation({
    mutationFn: () => reviewApi.remove(myReviewId!),
    onSuccess: () => {
      notify("Your review was deleted.");
      qc.invalidateQueries({ queryKey: ["recipe", detail.recipe.id] });
      qc.invalidateQueries({ queryKey: ["reviews", detail.recipe.id] });
      qc.invalidateQueries({ queryKey: ["my-reviews"] });
      onDone();
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });

  if (myReviewId && mine.isPending) return <LoadingBlock />;
  return (
    <form onSubmit={form.handleSubmit((v) => save.mutate(v))} className="flex flex-col gap-4 rounded-2xl border border-line bg-canvas p-4" noValidate>
      {existing && (
        <Alert tone={existing.status === "APPROVED" ? "success" : existing.status === "PENDING" ? "info" : "warning"}>
          Your review status: <strong>{existing.status.toLowerCase()}</strong>
          {existing.moderationReason ? ` — ${existing.moderationReason}` : ""}. Editing sends it back for moderation.
        </Alert>
      )}
      <Field label="Your rating" htmlFor="rating" required error={form.formState.errors.rating?.message}>
        <Controller control={form.control} name="rating" render={({ field }) => <StarInput value={field.value} onChange={field.onChange} />} />
      </Field>
      <Field label="Comment" htmlFor="comment" error={form.formState.errors.comment?.message}>
        <Textarea id="comment" {...form.register("comment")} maxLength={2000} />
      </Field>
      <Field label="Proof photo link (optional)" htmlFor="photoUrl" hint="An https:// link from an approved image host." error={form.formState.errors.photoUrl?.message}>
        <Input id="photoUrl" type="url" {...form.register("photoUrl")} />
      </Field>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" loading={save.isPending} data-testid="submit-review">{myReviewId ? "Update review" : "Submit review"}</Button>
        <Button variant="ghost" onClick={onDone}>Cancel</Button>
        {myReviewId && (
          <Button variant="danger" className="ml-auto" loading={remove.isPending} onClick={() => remove.mutate()}>Delete review</Button>
        )}
      </div>
    </form>
  );
}

export function ReviewSection({ detail }: { detail: RecipeDetail }) {
  const { status, user } = useAuth();
  const [writing, setWriting] = useState(false);
  const [page, setPage] = useState(0);
  const recipeId = detail.recipe.id;
  const reviews = useQuery({ queryKey: ["reviews", recipeId, page], queryFn: () => recipeApi.reviews(recipeId, page) });
  const canReview = detail.recipe.status === "APPROVED" && !detail.viewer.isOwner;

  return (
    <section aria-labelledby="reviews-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="reviews-heading" className="text-xl font-semibold">Reviews</h2>
        {canReview && !writing && (
          status === "authenticated" ? (
            <Button variant="secondary" onClick={() => setWriting(true)} icon={<MessageSquare aria-hidden className="h-4 w-4" />} data-testid="write-review">
              {detail.viewer.myReviewId ? "Edit my review" : "Write a review"}
            </Button>
          ) : (
            <Link href={`/login?next=/recipes/${recipeId}`} className="text-sm font-medium text-brand underline">Log in to write a review</Link>
          )
        )}
      </div>
      {writing && <ReviewFormPanel detail={detail} onDone={() => setWriting(false)} />}
      {reviews.isPending ? (
        <LoadingBlock label="Loading reviews…" />
      ) : reviews.isError ? (
        <ErrorState message={errorMessage(reviews.error)} onRetry={() => reviews.refetch()} />
      ) : reviews.data.items.length === 0 ? (
        <EmptyState title="No reviews yet" description="Approved reviews from people who cooked this will show here." />
      ) : (
        <ul className="flex flex-col gap-3">
          {reviews.data.items.map((r) => (
            <li key={r.id} className="rounded-2xl border border-line bg-surface p-4">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Stars value={r.rating} />
                <span className="font-medium">{r.author.displayName}</span>
                <span className="text-muted">{formatDate(r.createdAt)}</span>
                {status === "authenticated" && r.author.id !== user?.id && (
                  <span className="ml-auto"><ReportButton targetType="REVIEW" targetId={r.id} compact /></span>
                )}
              </div>
              {r.comment && <p className="mt-2 whitespace-pre-line text-sm">{r.comment}</p>}
              {safeHttpsUrl(r.photoUrl) && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={safeHttpsUrl(r.photoUrl)!} alt={`Photo by ${r.author.displayName}`} className="mt-3 max-h-48 rounded-lg object-cover" loading="lazy" referrerPolicy="no-referrer" />
              )}
            </li>
          ))}
        </ul>
      )}
      {reviews.data && <Pagination page={reviews.data.page} totalPages={reviews.data.totalPages} onChange={setPage} />}
    </section>
  );
}
