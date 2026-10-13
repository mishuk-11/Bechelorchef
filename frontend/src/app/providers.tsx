"use client";

import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, type ReactNode } from "react";
import { ToastProvider } from "@/components/ui/toast";
import { AuthProvider } from "@/features/auth/auth-provider";
import { EasterEggs } from "@/features/easter-eggs";
import { ApiError, onSessionChange } from "@/lib/api/client";

let browserQueryClient: QueryClient | undefined;

function makeClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Do not retry client errors such as 400/401/403/404.
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
    },
  });
}

function getQueryClient() {
  if (typeof window === "undefined") return makeClient();
  browserQueryClient ??= makeClient();
  return browserQueryClient;
}

/** Drops all cached API data when the signed-in user changes, so one person never sees another's data. */
function ClearCacheOnUserChange() {
  const client = useQueryClient();
  const lastUserId = useRef<number | null | undefined>(undefined);
  useEffect(
    () =>
      onSessionChange((session) => {
        const userId = session?.user.id ?? null;
        if (lastUserId.current !== undefined && lastUserId.current !== userId) {
          client.clear();
        }
        lastUserId.current = userId;
      }),
    [client],
  );
  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={getQueryClient()}>
      <ClearCacheOnUserChange />
      <ToastProvider>
        <AuthProvider>{children}</AuthProvider>
        <EasterEggs />
      </ToastProvider>
    </QueryClientProvider>
  );
}
