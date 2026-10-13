"use client";

import clsx from "clsx";
import { CheckCircle2, Info, X, XCircle } from "lucide-react";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

type ToastTone = "success" | "error" | "info";
interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

const ToastContext = createContext<{ notify: (message: string, tone?: ToastTone) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const notify = useCallback(
    (message: string, tone: ToastTone = "success") => {
      const id = Date.now() + Math.random();
      setToasts((t) => [...t.slice(-3), { id, tone, message }]);
      setTimeout(() => dismiss(id), tone === "error" ? 7000 : 4000);
    },
    [dismiss],
  );
  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4">
        {toasts.map((t) => (
          <div key={t.id} data-testid={`toast-${t.tone}`}
            className={clsx("pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-lg",
              t.tone === "success" && "border-green-200 bg-herb-soft",
              t.tone === "error" && "border-red-200 bg-danger-soft",
              t.tone === "info" && "border-blue-200 bg-info-soft")}>
            {t.tone === "success" ? <CheckCircle2 aria-hidden className="h-5 w-5 text-herb" />
              : t.tone === "error" ? <XCircle aria-hidden className="h-5 w-5 text-danger" />
              : <Info aria-hidden className="h-5 w-5 text-blue-800" />}
            <p className="flex-1">{t.message}</p>
            <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss notification"><X aria-hidden className="h-4 w-4" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}
