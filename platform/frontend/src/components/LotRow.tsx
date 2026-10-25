import { Link } from "react-router";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import type { Auction } from "../lib/types";
import { useSession } from "../store/session";
import { AuctionPhoto } from "./AuctionPhoto";
import { Countdown } from "./Countdown";
import { StatusBadge } from "./StatusBadge";
import { WatchButton } from "./WatchButton";

function priceFor(a: Auction): { label: string; amount: string } {
  if (a.status === "ENDED") {
    return a.winnerId && a.finalPrice != null
      ? { label: "Sold for", amount: formatMoney(a.finalPrice) }
      : { label: "No bids", amount: formatMoney(a.startingPrice) };
  }
  if (a.status === "ACTIVE" && a.currentPrice > a.startingPrice) return { label: "Highest bid", amount: formatMoney(a.currentPrice) };
  return { label: "Starting price", amount: formatMoney(a.startingPrice) };
}

export function LotRow({ auction: a }: { auction: Auction }) {
  const me = useSession((s) => s.user?.userId);
  const price = priceFor(a);
  return (
    <li className="flex items-center border-b border-rule last:border-b-0 hover:bg-marble">
      <Link to={`/lot/${a.id}`} className="flex min-w-0 flex-1 items-start gap-4 py-4 pl-5 pr-3 sm:items-center">
        <AuctionPhoto src={a.coverUrl} alt="" className="size-16 shrink-0 rounded-ui border border-rule sm:size-[4.5rem]" />
        <div className="grid min-w-0 flex-1 gap-x-8 gap-y-3 sm:grid-cols-[1fr_auto_8.5rem] sm:items-center">
          <div className="min-w-0">
            <p className="truncate text-lg font-semibold">{a.title}</p>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-quiet">
              <StatusBadge status={a.status} />
              <span>{a.sellerId === me ? "Your auction" : `Seller ${shortId(a.sellerId)}`}</span>
            </div>
          </div>
          <div className="sm:text-right">
            <p className="figures text-2xl font-semibold leading-none">{price.amount}</p>
            <p className="mt-1.5 text-sm text-quiet">{price.label}</p>
          </div>
          <div className="sm:text-right">
            {a.status === "ACTIVE" ? (
              <>
                <Countdown to={a.endsAt} className="text-base font-semibold" />
                <p className="mt-0.5 text-sm text-quiet">left</p>
              </>
            ) : a.status === "SCHEDULED" ? (
              <>
                <Countdown to={a.startsAt} urgent={false} className="text-base font-semibold" />
                <p className="mt-0.5 text-sm text-quiet">until it opens</p>
              </>
            ) : (
              <p className="text-sm text-quiet">{a.status === "CANCELLED" ? "Cancelled" : `Closed ${formatDateTime(a.endsAt)}`}</p>
            )}
          </div>
        </div>
      </Link>
      {/* Fixed width, so rows stay aligned on auctions that have no button (your own). */}
      <div className="mr-5 flex w-[38px] shrink-0 justify-end">
        <WatchButton auction={a} compact />
      </div>
    </li>
  );
}
