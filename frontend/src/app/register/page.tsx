"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/display";
import { Field, Input } from "@/components/ui/field";
import { Alert, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/features/auth/auth-provider";
import { ApiError, errorMessage } from "@/lib/api/client";
import { safeNext } from "@/lib/format";
import { registerSchema, type RegisterForm } from "@/schemas";

function RegisterInner() {
  const { register: signUp } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"), "/match");
  const { notify } = useToast();
  const [error, setError] = useState<string>();
  const form = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
  });
  const errors = form.formState.errors;

  async function onSubmit(v: RegisterForm) {
    setError(undefined);
    try {
      await signUp(v.fullName, v.email, v.password);
      notify("Welcome to Bachelor Chef!");
      router.replace(next);
    } catch (e) {
      if (e instanceof ApiError) {
        Object.entries(e.fieldErrors).forEach(([k, m]) => form.setError(k as keyof RegisterForm, { message: m }));
      }
      setError(errorMessage(e));
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-12">
      <Card className="flex flex-col gap-5">
        <h1 className="text-2xl font-bold">Create your account</h1>
        {error && <Alert tone="error">{error}</Alert>}
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <Field label="Full name" htmlFor="fullName" required error={errors.fullName?.message}>
            <Input id="fullName" autoComplete="name" invalid={!!errors.fullName} {...form.register("fullName")} />
          </Field>
          <Field label="Email" htmlFor="email" required error={errors.email?.message}>
            <Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...form.register("email")} />
          </Field>
          <Field label="Password" htmlFor="password" required hint="8–72 characters." error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="new-password" invalid={!!errors.password} {...form.register("password")} />
          </Field>
          <Field label="Confirm password" htmlFor="confirmPassword" required error={errors.confirmPassword?.message}>
            <Input id="confirmPassword" type="password" autoComplete="new-password" invalid={!!errors.confirmPassword} {...form.register("confirmPassword")} />
          </Field>
          <Button type="submit" loading={form.formState.isSubmitting} data-testid="register-submit">Sign up</Button>
        </form>
        <p className="text-sm text-muted">
          Already have an account? <Link href="/login" className="font-medium text-brand underline">Log in</Link>
        </p>
      </Card>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RegisterInner />
    </Suspense>
  );
}
