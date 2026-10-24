import { formatRemaining } from "../lib/format";
import { useNow } from "../lib/useNow";

/** Time left until `to`. Turns red in the last five minutes when `urgent` is allowed. */
export function Countdown({ to, urgent = true, className = "" }: { to: string; urgent?: boolean; className?: string }) {
  const now = useNow();
  const ms = new Date(to).getTime() - now;
  const hot = urgent && ms > 0 && ms < 5 * 60_000;
  return (
    <span className={`figures ${hot ? "text-alert" : ""} ${className}`}>{ms <= 0 ? "Closing" : formatRemaining(ms)}</span>
  );
}
