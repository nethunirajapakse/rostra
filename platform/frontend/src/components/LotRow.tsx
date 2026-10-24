import { Link } from "react-router";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import type { Auction } from "../lib/types";
import { useSession } from "../store/session";
import { Countdown } from "./Countdown";
import { StatusBadge } from "./StatusBadge";

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
    <li className="border-b border-rule last:border-b-0">
      <Link
        to={`/lot/${a.id}`}
        className="grid gap-x-8 gap-y-3 px-5 py-4 hover:bg-marble sm:grid-cols-[1fr_auto_8.5rem] sm:items-center"
      >
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
      </Link>
    </li>
  );
}
