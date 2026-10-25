export type ButtonVariant = "primary" | "quiet" | "danger";

const base =
  "inline-flex items-center justify-center gap-2 rounded-ui px-4 py-2.5 text-sm font-bold transition-colors disabled:opacity-50";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-signal text-ink hover:bg-signal-deep",
  quiet: "border border-rule bg-surface text-ink hover:border-quiet",
  danger: "border border-alert bg-surface text-alert hover:bg-alert-tint",
};

export const buttonClass = (variant: ButtonVariant = "primary", extra = "") => `${base} ${variants[variant]} ${extra}`;

export const inputClass =
  "w-full rounded-ui border border-rule bg-surface px-3 py-2.5 text-base text-ink placeholder:text-quiet/70 focus:border-ink";
