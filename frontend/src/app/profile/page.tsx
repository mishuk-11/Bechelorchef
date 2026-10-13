"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { RequireAuth } from "@/components/layout/guards";
import { Button, ButtonLink } from "@/components/ui/button";
import { Badge, Card, Pagination } from "@/components/ui/display";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/features/auth/auth-provider";
import { ImageField } from "@/features/media/image-field";
import { RecipeCard, RecipeGrid } from "@/features/recipes/recipe-card";
import { ApiError, errorMessage } from "@/lib/api/client";
import { profileApi } from "@/lib/api/endpoints";
import { STATUS_LABEL, formatDate } from "@/lib/format";
import type { RecipeStatus, UserProfile } from "@/types/api";

function ProfileForm({ user }: { user: UserProfile }) {
  const { updateUser } = useAuth();
  const { notify } = useToast();
  const [fullName, setFullName] = useState(user.fullName);
  const [phone, setPhone] = useState(user.phoneNumber ?? "");
  const [bio, setBio] = useState(user.bio ?? "");
  const [avatar, setAvatar] = useState(user.avatarUrl ?? "");
  const [avatarAssetId, setAvatarAssetId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const save = useMutation({
    mutationFn: () => profileApi.update({ fullName, phoneNumber: phone, bio, avatarUrl: avatar, avatarAssetId }),
    onSuccess: (u) => {
      setErrors({});
      updateUser(u);
      notify("Profile saved.");
    },
    onError: (e) => {
      setErrors(e instanceof ApiError ? e.fieldErrors : {});
      notify(errorMessage(e), "error");
    },
  });
  return (
    <Card>
      <form className="grid gap-4 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(); }} noValidate>
        <Field label="Full name" htmlFor="p-name" required error={errors.fullName}>
          <Input id="p-name" value={fullName} maxLength={100} onChange={(e) => setFullName(e.target.value)} />
        </Field>
        <Field label="Email" htmlFor="p-email" hint="Email changes are not supported yet.">
          <Input id="p-email" value={user.email} disabled />
        </Field>
        <Field label="Phone (optional)" htmlFor="p-phone" error={errors.phoneNumber}>
          <Input id="p-phone" value={phone} maxLength={20} onChange={(e) => setPhone(e.target.value)} placeholder="+8801XXXXXXXXX" />
        </Field>
        <Field label="Bio (optional)" htmlFor="p-bio" error={errors.bio}>
          <Textarea id="p-bio" value={bio} maxLength={500} onChange={(e) => setBio(e.target.value)} className="min-h-10" />
        </Field>
        <div className="sm:col-span-2">
          <ImageField label="Avatar" purpose="AVATAR" url={avatar} error={errors.avatarUrl} onChange={(u, a) => { setAvatar(u); setAvatarAssetId(a); }} />
        </div>
        <div><Button type="submit" loading={save.isPending}>Save profile</Button></div>
      </form>
    </Card>
  );
}

function PasswordAndAccount() {
  const { logout } = useAuth();
  const router = useRouter();
  const { notify } = useToast();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [deletePassword, setDeletePassword] = useState("");
  const change = useMutation({
    mutationFn: () => profileApi.changePassword(current, next),
    onSuccess: () => { setCurrent(""); setNext(""); notify("Password changed. Other sessions were signed out."); },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  const remove = useMutation({
    mutationFn: () => profileApi.deleteAccount(deletePassword),
    onSuccess: async () => { await logout().catch(() => undefined); notify("Your account was deleted."); router.push("/"); },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <h2 className="mb-3 font-semibold">Change password</h2>
        <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); change.mutate(); }}>
          <Field label="Current password" htmlFor="pw-current"><Input id="pw-current" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
          <Field label="New password" htmlFor="pw-new" hint="8–72 characters"><Input id="pw-new" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></Field>
          <div><Button type="submit" variant="secondary" loading={change.isPending} disabled={!current || next.length < 8}>Update password</Button></div>
        </form>
      </Card>
      <Card>
        <h2 className="mb-3 font-semibold">Delete account</h2>
        <p className="mb-3 text-sm text-muted">Your personal details are erased. Published recipes and reviews stay, shown as “Deleted user”.</p>
        <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); if (confirm("Permanently delete your account?")) remove.mutate(); }}>
          <Field label="Confirm with your password" htmlFor="pw-delete"><Input id="pw-delete" type="password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} /></Field>
          <div><Button type="submit" variant="danger" loading={remove.isPending} disabled={!deletePassword}>Delete my account</Button></div>
        </form>
      </Card>
    </div>
  );
}

function MySubmissions() {
  const [status, setStatus] = useState<RecipeStatus | "">("");
  const [page, setPage] = useState(0);
  const recipes = useQuery({ queryKey: ["my-recipes", status, page], queryFn: () => profileApi.myRecipes(status || undefined, page) });
  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-xl font-semibold">My recipes</h2>
        <div className="flex items-center gap-2">
          <label htmlFor="my-status" className="text-sm text-muted">Status</label>
          <Select id="my-status" value={status} onChange={(e) => { setStatus(e.target.value as RecipeStatus | ""); setPage(0); }} className="w-44">
            <option value="">All</option>
            {(Object.keys(STATUS_LABEL) as RecipeStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </Select>
        </div>
      </div>
      {recipes.isPending ? <LoadingBlock /> : recipes.isError ? <ErrorState message={errorMessage(recipes.error)} onRetry={() => recipes.refetch()} />
        : recipes.data.items.length === 0 ? (
          <EmptyState title="No submissions yet" action={<ButtonLink href="/submit">Share a recipe</ButtonLink>} />
        ) : (
          <>
            <RecipeGrid>{recipes.data.items.map((r) => <RecipeCard key={r.id} recipe={r} showStatus />)}</RecipeGrid>
            <Pagination page={recipes.data.page} totalPages={recipes.data.totalPages} onChange={setPage} />
          </>
        )}
    </section>
  );
}

function MyReviews() {
  const reviews = useQuery({ queryKey: ["my-reviews"], queryFn: () => profileApi.myReviews(0) });
  if (reviews.isPending) return <LoadingBlock />;
  if (reviews.isError) return <ErrorState message={errorMessage(reviews.error)} />;
  if (reviews.data.items.length === 0) return null;
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-xl font-semibold">My reviews</h2>
      <ul className="flex flex-col gap-2">
        {reviews.data.items.map((r) => (
          <li key={r.id} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-surface p-3 text-sm">
            <span className="font-medium">{r.recipeTitle}</span> <span>{"★".repeat(r.rating)}</span>
            <Badge tone={r.status === "APPROVED" ? "herb" : r.status === "PENDING" ? "info" : "danger"}>{r.status.toLowerCase()}</Badge>
            {r.moderationReason && <span className="text-muted">— {r.moderationReason}</span>}
            <span className="ml-auto text-xs text-muted">{formatDate(r.updatedAt)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ProfileInner() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-8">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold">Profile</h1>
        {user.role === "ADMIN" && <Badge tone="brand">Administrator</Badge>}
        <span className="text-sm text-muted">Member since {formatDate(user.createdAt)}</span>
      </header>
      <ProfileForm key={user.id} user={user} />
      <MySubmissions />
      <MyReviews />
      <PasswordAndAccount />
    </div>
  );
}

export default function ProfilePage() {
  return (
    <RequireAuth>
      <ProfileInner />
    </RequireAuth>
  );
}
