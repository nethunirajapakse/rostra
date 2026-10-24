// The backend stores plain decimal amounts without a currency; change it here to relabel the whole UI.
const CURRENCY = "USD";

const money = new Intl.NumberFormat("en-US", { style: "currency", currency: CURRENCY });
export const formatMoney = (n: number) => money.format(n);

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso));

/** "2 days", "3 h 05 min", "4 min 09 s": the two most useful units for the time that is left. */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (d >= 1) return `${d} ${d === 1 ? "day" : "days"} ${h} h`;
  if (h >= 1) return `${h} h ${String(m).padStart(2, "0")} min`;
  if (m >= 1) return `${m} min ${String(s).padStart(2, "0")} s`;
  return `${s} s`;
}

export function timeAgo(iso: string, now = Date.now()): string {
  const diff = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000));
  if (diff < 45) return "just now";
  if (diff < 3600) return `${Math.max(1, Math.round(diff / 60))} min ago`;
  if (diff < 86400) return `${Math.round(diff / 3600)} h ago`;
  return `${Math.round(diff / 86400)} d ago`;
}

/** Short stable label for someone we only know by id. */
export const shortId = (id: string) => id.replace(/-/g, "").slice(0, 6);

/** datetime-local inputs use local time without a zone: "2026-10-07T19:30". */
export function toLocalInput(date: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}
