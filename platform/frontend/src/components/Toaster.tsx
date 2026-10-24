import { Link, useLocation } from "react-router";
import { useToasts, type ToastTone } from "../store/toasts";

const BAR: Record<ToastTone, string> = {
  info: "bg-quiet",
  good: "bg-verdigris",
  warn: "bg-bronze",
  bad: "bg-alert",
};

export function Toaster() {
  const toasts = useToasts((s) => s.toasts);
  const dismiss = useToasts((s) => s.dismiss);
  const here = useLocation().pathname;
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className="toast-in pointer-events-auto flex w-full max-w-sm overflow-hidden rounded-ui border border-rule bg-slab"
        >
          <span className={`w-1.5 shrink-0 ${BAR[t.tone]}`} aria-hidden />
          <div className="flex-1 px-3.5 py-3 text-sm">
            <p>{t.text}</p>
            {t.to && t.to !== here ? (
              <Link to={t.to} onClick={() => dismiss(t.id)} className="mt-1 inline-block font-semibold text-verdigris hover:underline">
                View auction
              </Link>
            ) : null}
          </div>
          <button onClick={() => dismiss(t.id)} aria-label="Dismiss" className="px-3 text-quiet hover:text-ink">
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
