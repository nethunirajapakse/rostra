import { useNavigate } from "react-router";
import { useToggleWatch } from "../api/auctions";
import type { Auction } from "../lib/types";
import { useSession } from "../store/session";
import { EyeIcon } from "./icons";

/**
 * Add or remove an auction from the signed-in user's watchlist. Signed-out visitors are sent to sign in,
 * and sellers do not see it on their own auctions.
 */
export function WatchButton({ auction, compact = false, overlay = false }: { auction: Auction; compact?: boolean; overlay?: boolean }) {
  const me = useSession((s) => s.user?.userId);
  const navigate = useNavigate();
  const toggle = useToggleWatch();

  if (me === auction.sellerId) return null;
  const watching = auction.watching;

  return (
    <button
      type="button"
      aria-pressed={watching}
      aria-label={`${watching ? "Stop watching" : "Watch"} ${auction.title}`}
      title={watching ? "Remove from your watchlist" : "Add to your watchlist"}
      onClick={() => {
        if (!me) {
          navigate(`/signin?next=${encodeURIComponent(`/lot/${auction.id}`)}`);
          return;
        }
        toggle.mutate({ id: auction.id, watch: !watching });
      }}
      className={`inline-flex items-center gap-1.5 border text-sm font-bold transition-colors ${
        overlay ? "size-9 justify-center rounded-full" : compact ? "rounded-ui px-2.5 py-2" : "rounded-ui px-3.5 py-2.5"
      } ${
        watching
          ? "border-ink bg-signal text-ink"
          : overlay
            ? "border-transparent bg-surface/95 text-ink hover:bg-surface"
            : "border-rule bg-surface text-ink hover:border-ink"
      }`}
    >
      <EyeIcon filled={watching} />
      <span className={compact || overlay ? "sr-only" : ""}>{watching ? "Watching" : "Watch"}</span>
    </button>
  );
}
