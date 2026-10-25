import { Link, useParams } from "react-router";
import { useAuction, useBids } from "../api/auctions";
import { BidHistory } from "../components/BidHistory";
import { BidPanel } from "../components/BidPanel";
import { PhotoGallery } from "../components/PhotoGallery";
import { PhotoManager } from "../components/PhotoManager";
import { WatchButton } from "../components/WatchButton";
import { StatusBadge } from "../components/StatusBadge";
import { ErrorNotice, Spinner } from "../components/ui";
import { ApiError, errorText } from "../lib/api";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import { useTitle } from "../lib/useTitle";
import { useSession } from "../store/session";

export function AuctionDetailPage() {
  const { id = "" } = useParams();
  const auction = useAuction(id);
  const live = auction.data?.status === "ACTIVE";
  const bids = useBids(id, live);
  const me = useSession((s) => s.user?.userId);
  useTitle(auction.data?.title ?? "Auction");

  if (auction.isPending) return <Spinner label="Loading auction" />;
  if (auction.isError) {
    const missing = auction.error instanceof ApiError && (auction.error.status === 404 || auction.error.status === 400);
    return missing ? (
      <div className="py-12">
        <h1 className="font-display text-4xl">That auction does not exist</h1>
        <p className="mt-3 text-quiet">The link may be wrong, or the auction was removed.</p>
        <Link to="/" className="mt-4 inline-block font-semibold text-verdigris hover:underline">
          Back to all auctions
        </Link>
      </div>
    ) : (
      <ErrorNotice>{errorText(auction.error)}</ErrorNotice>
    );
  }

  const a = auction.data;
  const bidList = bids.data?.content ?? [];
  const minNext = (Math.round(a.currentPrice * 100) + Math.round(a.minIncrement * 100)) / 100;

  return (
    <>
      <Link to="/" className="text-sm font-semibold text-verdigris hover:underline">
        All auctions
      </Link>
      {/* DOM order is the mobile order: title, then the bidding panel, then the details. On wide screens the
          panel moves to its own column beside both. */}
      <div className="mt-4 grid gap-x-10 gap-y-8 lg:grid-cols-[1fr_22rem]">
        <div className="min-w-0 lg:col-start-1">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <StatusBadge status={a.status} />
            <WatchButton auction={a} />
          </div>
          <h1 className="mt-3 font-display text-5xl leading-[1.05] [overflow-wrap:anywhere]">{a.title}</h1>
        </div>

        <aside className="lg:col-start-2 lg:row-span-2 lg:row-start-1">
          <BidPanel auction={a} bids={bidList} />
        </aside>

        <div className="min-w-0 lg:col-start-1">
          {a.images.length > 0 ? (
            <div className="mb-8">
              <PhotoGallery images={a.images} title={a.title} />
            </div>
          ) : null}
          {a.description ? <p className="max-w-prose whitespace-pre-line text-lg leading-relaxed">{a.description}</p> : null}

          <dl className="figures mt-8 grid max-w-prose grid-cols-2 gap-x-8 gap-y-5 border-t border-rule pt-6 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-quiet">Seller</dt>
              <dd className="mt-0.5 font-medium">{a.sellerId === me ? "You" : shortId(a.sellerId)}</dd>
            </div>
            <div>
              <dt className="text-quiet">Starting price</dt>
              <dd className="mt-0.5 font-medium">{formatMoney(a.startingPrice)}</dd>
            </div>
            <div>
              <dt className="text-quiet">Each bid raises by at least</dt>
              <dd className="mt-0.5 font-medium">{formatMoney(a.minIncrement)}</dd>
            </div>
            <div>
              <dt className="text-quiet">Opens</dt>
              <dd className="mt-0.5 font-medium">{formatDateTime(a.startsAt)}</dd>
            </div>
            <div>
              <dt className="text-quiet">Closes</dt>
              <dd className="mt-0.5 font-medium">{formatDateTime(a.endsAt)}</dd>
            </div>
          </dl>

          {a.sellerId === me && (a.status === "SCHEDULED" || a.status === "ACTIVE" || a.status === "DRAFT") ? (
            <PhotoManager auction={a} />
          ) : null}

          <h2 className="mb-3 mt-10 text-xl font-semibold">Bids</h2>
          {bids.isPending ? (
            <Spinner label="Loading bids" />
          ) : bids.isError ? (
            <ErrorNotice>{errorText(bids.error)}</ErrorNotice>
          ) : (
            <BidHistory bids={bidList} minNext={minNext} live={a.status === "ACTIVE" || a.status === "SCHEDULED"} />
          )}
        </div>
      </div>
    </>
  );
}
