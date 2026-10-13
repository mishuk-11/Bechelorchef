"use client";

import { ShieldAlert } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/features/auth/auth-provider";
import { ButtonLink } from "@/components/ui/button";
import { EmptyState, LoadingBlock } from "@/components/ui/states";

/** Redirects anonymous visitors to /login?next=… . The API enforces access regardless. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);
  if (status !== "authenticated") return <LoadingBlock label="Checking your session…" />;
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin, status } = useAuth();
  return (
    <RequireAuth>
      {status === "authenticated" && !isAdmin ? (
        <div className="mx-auto max-w-xl px-4 py-16">
          <EmptyState icon={<ShieldAlert className="h-8 w-8" />} title="Administrators only"
            description="This area is part of Bachelor Chef Admin. Your account does not have access."
            action={<ButtonLink href="/" variant="secondary">Back to home</ButtonLink>} />
        </div>
      ) : (
        children
      )}
    </RequireAuth>
  );
}
