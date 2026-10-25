import { formatMoney, shortId, timeAgo } from "../lib/format";
import type { Bid } from "../lib/types";
import { useNow } from "../lib/useNow";
import { useSession } from "../store/session";

export function BidHistory({ bids, minNext, live }: { bids: Bid[]; minNext: number; live: boolean }) {
  const me = useSession((s) => s.user?.userId);
  const now = useNow();

  if (bids.length === 0) {
    return (
      <p className="rounded-ui border border-dashed border-rule bg-surface/60 px-5 py-8 text-center text-quiet">
        {live ? `No bids yet. The first bid must be at least ${formatMoney(minNext)}.` : "Nobody placed a bid."}
      </p>
    );
  }
  return (
    <ol className="overflow-hidden rounded-ui border border-rule bg-surface">
      {bids.map((b, i) => (
        <li key={b.id} className="flex items-baseline justify-between gap-4 border-b border-rule px-5 py-3 last:border-b-0">
          <div className="flex items-baseline gap-3">
            <span className={`price text-xl ${i === 0 ? "text-good" : ""}`}>{formatMoney(b.amount)}</span>
            <span className="text-sm text-quiet">{b.bidderId === me ? "You" : `Bidder ${shortId(b.bidderId)}`}</span>
          </div>
          <span className="text-sm text-quiet">{timeAgo(b.placedAt, now)}</span>
        </li>
      ))}
    </ol>
  );
}
