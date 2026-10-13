"use client";

import { ErrorState } from "@/components/ui/states";

export default function GlobalError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <div className="mx-auto max-w-xl px-4 py-16">
      <ErrorState message="Something went wrong while showing this page." onRetry={() => retry()} />
    </div>
  );
}
