import clsx from "clsx";
import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

const control =
  "w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-muted/70 focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30 disabled:bg-sunken aria-[invalid=true]:border-danger";

export function Field({ label, htmlFor, error, hint, required, children, className }: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={clsx("flex flex-col gap-1.5", className)}>
      <label htmlFor={htmlFor} className="text-sm font-medium text-ink">
        {label}
        {required && <span className="text-danger" aria-hidden> *</span>}
      </label>
      {children}
      {hint && !error && <p id={`${htmlFor}-hint`} className="text-xs text-muted">{hint}</p>}
      {error && <p id={`${htmlFor}-error`} role="alert" className="text-xs font-medium text-danger">{error}</p>}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ className, invalid, ...rest }, ref) {
    return <input ref={ref} aria-invalid={invalid || undefined} className={clsx(control, "h-10", className)} {...rest} />;
  },
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }>(
  function Textarea({ className, invalid, ...rest }, ref) {
    return <textarea ref={ref} aria-invalid={invalid || undefined} className={clsx(control, "min-h-24", className)} {...rest} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }>(
  function Select({ className, invalid, ...rest }, ref) {
    return <select ref={ref} aria-invalid={invalid || undefined} className={clsx(control, "h-10", className)} {...rest} />;
  },
);

export function Checkbox({ id, label, ...rest }: InputHTMLAttributes<HTMLInputElement> & { id: string; label: ReactNode }) {
  return (
    <label htmlFor={id} className="inline-flex cursor-pointer items-center gap-2 text-sm">
      <input id={id} type="checkbox" className="h-4 w-4 rounded border-line accent-brand" {...rest} />
      {label}
    </label>
  );
}
