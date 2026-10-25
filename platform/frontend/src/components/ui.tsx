import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { buttonClass, inputClass, type ButtonVariant } from "../lib/ui";

export function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return <button {...props} className={buttonClass(variant, className)} />;
}

interface FieldShell {
  label: string;
  hint?: ReactNode;
  error?: string | null;
}

export function TextField({ label, hint, error, ...props }: FieldShell & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <input id={id} {...props} aria-invalid={error ? true : undefined} className={`${inputClass} ${props.className ?? ""}`} />
      {error ? <p className="mt-1.5 text-sm text-alert">{error}</p> : hint ? <p className="mt-1.5 text-sm text-quiet">{hint}</p> : null}
    </div>
  );
}

export function TextArea({ label, hint, error, ...props }: FieldShell & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <textarea id={id} {...props} aria-invalid={error ? true : undefined} className={`${inputClass} min-h-28 ${props.className ?? ""}`} />
      {error ? <p className="mt-1.5 text-sm text-alert">{error}</p> : hint ? <p className="mt-1.5 text-sm text-quiet">{hint}</p> : null}
    </div>
  );
}

export function ErrorNotice({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="rounded-ui border border-alert/40 bg-alert-tint px-3.5 py-3 text-sm text-alert">
      {children}
    </div>
  );
}

export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2.5 py-10 text-quiet">
      <span className="size-4 animate-spin rounded-full border-2 border-rule border-t-signal-deep" aria-hidden />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="border border-dashed border-rule bg-surface/60 px-6 py-12 text-center rounded-ui">
      <p className="font-display text-2xl">{title}</p>
      {children ? <div className="mx-auto mt-2 max-w-md text-quiet">{children}</div> : null}
    </div>
  );
}
