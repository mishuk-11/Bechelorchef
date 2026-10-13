"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/display";
import { Field, Input } from "@/components/ui/field";
import { Alert, LoadingBlock } from "@/components/ui/states";
import { useAuth } from "@/features/auth/auth-provider";
import { errorMessage } from "@/lib/api/client";
import { safeNext } from "@/lib/format";
import { loginSchema, type LoginForm } from "@/schemas";

function LoginInner() {
  const { login, status, isAdmin } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [error, setError] = useState<string>();
  const form = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });

  useEffect(() => {
    if (status === "authenticated") router.replace(next === "/" && isAdmin ? "/admin" : next);
  }, [status, isAdmin, next, router]);

  async function onSubmit(values: LoginForm) {
    setError(undefined);
    try {
      await login(values.email, values.password);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-12">
      <Card className="flex flex-col gap-5">
        <h1 className="text-2xl font-bold">Log in</h1>
        {error && <Alert tone="error">{error}</Alert>}
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <Field label="Email" htmlFor="email" required error={form.formState.errors.email?.message}>
            <Input id="email" type="email" autoComplete="email" invalid={!!form.formState.errors.email} {...form.register("email")} />
          </Field>
          <Field label="Password" htmlFor="password" required error={form.formState.errors.password?.message}>
            <Input id="password" type="password" autoComplete="current-password" invalid={!!form.formState.errors.password} {...form.register("password")} />
          </Field>
          <Button type="submit" loading={form.formState.isSubmitting} data-testid="login-submit">Log in</Button>
        </form>
        <p className="text-sm text-muted">
          New here? <Link href={`/register${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`} className="font-medium text-brand underline">Create an account</Link>
        </p>
        <p className="text-xs text-muted">Password reset is not available yet; contact an administrator if you are locked out.</p>
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <LoginInner />
    </Suspense>
  );
}
