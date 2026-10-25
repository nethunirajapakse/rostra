import { Link } from "react-router";
import { formatMoney, shortId } from "../lib/format";
import type { Auction } from "../lib/types";
import { useSession } from "../store/session";
import { AuctionPhoto } from "./AuctionPhoto";
import { Countdown } from "./Countdown";
import { WatchButton } from "./WatchButton";

function priceFor(a: Auction): { label: string; amount: number } {
  if (a.status === "ENDED") {
    return a.winnerId && a.finalPrice != null
      ? { label: "Sold for", amount: a.finalPrice }
      : { label: "No bids", amount: a.startingPrice };
  }
  if (a.status === "ACTIVE" && a.currentPrice > a.startingPrice) return { label: "Current bid", amount: a.currentPrice };
  return { label: "Starting price", amount: a.startingPrice };
}

const HOUR = 60 * 60_000;

/** What the corner of the photo says about time: how long is left, when it opens, or that it is over. */
function TimeChip({ a }: { a: Auction }) {
  const base = "absolute left-2 top-2 rounded-[3px] px-2 py-1 text-xs font-bold";
  if (a.status === "ACTIVE") {
    return (
      <span className={`${base} bg-ink text-white`}>
        <Countdown to={a.endsAt} hotUnder={HOUR} className="[&.text-alert]:text-[#ff8a7d]" /> left
      </span>
    );
  }
  if (a.status === "SCHEDULED") {
    return (
      <span className={`${base} bg-surface text-ink`}>
        Opens in <Countdown to={a.startsAt} urgent={false} />
      </span>
    );
  }
  return <span className={`${base} ${a.status === "CANCELLED" ? "bg-alert-tint text-alert" : "bg-surface text-quiet"}`}>{a.status === "CANCELLED" ? "Cancelled" : "Ended"}</span>;
}

export function LotCard({ auction: a }: { auction: Auction }) {
  const me = useSession((s) => s.user?.userId);
  const price = priceFor(a);
  return (
    <li className="group relative">
      <Link to={`/lot/${a.id}`} className="block">
        <div className="relative overflow-hidden rounded-ui border border-rule bg-surface">
          <AuctionPhoto src={a.coverUrl} alt="" className="aspect-[4/3] w-full transition-transform duration-300 group-hover:scale-[1.03]" />
          <TimeChip a={a} />
          {/* The lot tag: the number people compare first, in the same place on every card. */}
          <div className="absolute bottom-0 left-0 bg-ink px-3 pb-1.5 pt-1">
            <p className="text-xs font-medium leading-tight text-white/75">{price.label}</p>
            <p className="price text-2xl leading-none text-signal">{formatMoney(price.amount)}</p>
          </div>
        </div>
        <h3 className="mt-2.5 line-clamp-2 text-base font-semibold leading-snug group-hover:underline">{a.title}</h3>
        <p className="mt-0.5 text-sm text-quiet">{a.sellerId === me ? "Your auction" : `Seller ${shortId(a.sellerId)}`}</p>
      </Link>
      <div className="absolute right-2 top-2">
        <WatchButton auction={a} overlay />
      </div>
    </li>
  );
}

export function LotGrid({ auctions, dim = false }: { auctions: Auction[]; dim?: boolean }) {
  return (
    <ul className={`grid grid-cols-2 gap-x-4 gap-y-7 md:grid-cols-3 lg:grid-cols-4 ${dim ? "opacity-60" : ""}`}>
      {auctions.map((a) => (
        <LotCard key={a.id} auction={a} />
      ))}
    </ul>
  );
}
