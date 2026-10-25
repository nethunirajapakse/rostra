import { formatRemaining } from "../lib/format";
import { useNow } from "../lib/useNow";

/** Time left until `to`. Turns red when less than `hotUnder` ms remain (default five minutes); `urgent={false}` never does. */
export function Countdown({
  to,
  urgent = true,
  hotUnder = 5 * 60_000,
  className = "",
}: {
  to: string;
  urgent?: boolean;
  hotUnder?: number;
  className?: string;
}) {
  const now = useNow();
  const ms = new Date(to).getTime() - now;
  const hot = urgent && ms > 0 && ms < hotUnder;
  return (
    <span className={`figures ${hot ? "text-alert" : ""} ${className}`}>{ms <= 0 ? "Closing" : formatRemaining(ms)}</span>
  );
}
