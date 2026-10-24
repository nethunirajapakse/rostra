import { useState, type FormEvent } from "react";
import { Link, useLocation } from "react-router";
import { useCancelAuction, usePlaceBid } from "../api/auctions";
import { errorText } from "../lib/api";
import { formatDateTime, formatMoney, shortId } from "../lib/format";
import type { Auction, Bid } from "../lib/types";
import { buttonClass } from "../lib/ui";
import { useChangeNonce } from "../lib/useChangeNonce";
import { toast } from "../store/toasts";
import { useSession } from "../store/session";
import { Countdown } from "./Countdown";
import { LivePrice } from "./LivePrice";
import { Button, ErrorNotice } from "./ui";

const cents = (n: number) => Math.round(n * 100);

export function BidPanel({ auction: a, bids }: { auction: Auction; bids: Bid[] }) {
  const me = useSession((s) => s.user?.userId);
  const nonce = useChangeNonce(a.currentPrice);
  const isSeller = me === a.sellerId;
  const leaderId = bids[0]?.bidderId;
  const iBid = me ? bids.some((b) => b.bidderId === me) : false;

  return (
    <section aria-label="Bidding" className="relative overflow-hidden rounded-ui border border-rule bg-slab lg:sticky lg:top-6">
      {/* Replays on every price change: the panel flashes without touching what is being typed. It sits
          behind the content, which is positioned so it paints above it. */}
      {nonce > 0 ? <span key={nonce} className="panel-flash pointer-events-none absolute inset-0" aria-hidden /> : null}

      <div className="relative p-6">
      {a.status === "ACTIVE" ? (
        <>
          <p className="text-sm text-quiet">{bids.length > 0 ? "Highest bid" : "Starting price"}</p>
          <LivePrice value={a.currentPrice} className="text-5xl font-semibold leading-tight" />
          <p className="mt-2 text-sm text-quiet">
            Closes in <Countdown to={a.endsAt} className="font-semibold text-ink" />
          </p>
          {me && !isSeller ? (
            <>
              {leaderId === me ? (
                <p className="mt-4 rounded-ui bg-verdigris-tint px-3.5 py-2.5 text-sm font-semibold text-verdigris-deep">You have the highest bid.</p>
              ) : iBid ? (
                <p className="mt-4 rounded-ui bg-bronze-tint px-3.5 py-2.5 text-sm font-semibold text-bronze">You were outbid. Bid again to take the lead.</p>
              ) : null}
              <BidForm auction={a} />
            </>
          ) : isSeller ? (
            <SellerControls auction={a} />
          ) : (
            <SignInPrompt />
          )}
        </>
      ) : a.status === "SCHEDULED" ? (
        <>
          <p className="text-sm text-quiet">Starting price</p>
          <p className="figures text-5xl font-semibold leading-tight">{formatMoney(a.startingPrice)}</p>
          <p className="mt-2 text-sm text-quiet">
            Opens in <Countdown to={a.startsAt} urgent={false} className="font-semibold text-ink" />
          </p>
          <p className="mt-1 text-sm text-quiet">{formatDateTime(a.startsAt)}</p>
          {isSeller ? <SellerControls auction={a} /> : null}
        </>
      ) : a.status === "ENDED" ? (
        <Result auction={a} />
      ) : (
        <>
          <p className="font-display text-3xl">Cancelled</p>
          <p className="mt-2 text-quiet">The seller closed this auction. Bids on it no longer count.</p>
        </>
      )}
      </div>
    </section>
  );
}

function Result({ auction: a }: { auction: Auction }) {
  const me = useSession((s) => s.user?.userId);
  if (!a.winnerId || a.finalPrice == null) {
    return (
      <>
        <p className="font-display text-3xl">Ended without bids</p>
        <p className="mt-2 text-quiet">Nobody bid on this auction before it closed on {formatDateTime(a.endsAt)}.</p>
      </>
    );
  }
  return (
    <>
      <p className="text-sm text-quiet">Sold for</p>
      <p className="figures text-5xl font-semibold leading-tight">{formatMoney(a.finalPrice)}</p>
      <p className={`mt-4 rounded-ui px-3.5 py-2.5 text-sm font-semibold ${me === a.winnerId ? "bg-verdigris-tint text-verdigris-deep" : "bg-marble text-ink"}`}>
        {me === a.winnerId ? "You won this auction." : me === a.sellerId ? `Won by bidder ${shortId(a.winnerId)}.` : `Won by bidder ${shortId(a.winnerId)}. Better luck next time.`}
      </p>
      <p className="mt-3 text-sm text-quiet">Closed {formatDateTime(a.endsAt)}</p>
    </>
  );
}

function SignInPrompt() {
  const location = useLocation();
  return (
    <div className="mt-5">
      <p className="text-sm text-quiet">Sign in to place a bid.</p>
      <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClass("primary", "mt-3 w-full")}>
        Sign in
      </Link>
    </div>
  );
}

function SellerControls({ auction: a }: { auction: Auction }) {
  const cancel = useCancelAuction(a.id);
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="mt-5 border-t border-rule pt-4">
      <p className="text-sm text-quiet">
        {a.status === "ACTIVE" ? "This is your auction. Everyone else can bid on it." : "This is your auction. Bidding opens at the start time."}
      </p>
      {cancel.isError ? <div className="mt-3"><ErrorNotice>{errorText(cancel.error)}</ErrorNotice></div> : null}
      {confirming ? (
        <div className="mt-3 flex gap-2">
          <Button
            variant="danger"
            disabled={cancel.isPending}
            onClick={() =>
              cancel.mutate(undefined, {
                onSuccess: () => toast.info("Auction cancelled."),
                onSettled: () => setConfirming(false),
              })
            }
          >
            Cancel it
          </Button>
          <Button variant="quiet" onClick={() => setConfirming(false)}>
            Keep it
          </Button>
        </div>
      ) : (
        <Button variant="quiet" className="mt-3" onClick={() => setConfirming(true)}>
          Cancel auction
        </Button>
      )}
    </div>
  );
}

function BidForm({ auction: a }: { auction: Auction }) {
  const place = usePlaceBid(a.id);
  const [raw, setRaw] = useState("");
  const minNext = (cents(a.currentPrice) + cents(a.minIncrement)) / 100;
  const amount = raw === "" ? minNext : Number(raw);
  const valid = Number.isFinite(amount) && cents(amount) >= cents(minNext);
  const quick = [minNext, minNext + a.minIncrement, minNext + a.minIncrement * 4].map((n) => cents(n) / 100);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    place.mutate(amount, {
      onSuccess: () => setRaw(""), // the panel itself confirms: "You have the highest bid"
    });
  };

  return (
    <form onSubmit={submit} className="mt-5" noValidate>
      <label htmlFor="bid-amount" className="mb-1.5 block text-sm font-medium">
        Your bid
      </label>
      <div className="flex items-stretch rounded-ui border border-rule bg-slab focus-within:border-verdigris">
        <span className="flex items-center pl-3 text-quiet" aria-hidden>
          $
        </span>
        <input
          id="bid-amount"
          inputMode="decimal"
          autoComplete="off"
          value={raw}
          onChange={(e) => setRaw(e.target.value.replace(/[^0-9.]/g, ""))}
          placeholder={minNext.toFixed(2)}
          aria-describedby="bid-hint"
          className="figures w-full min-w-0 bg-transparent px-2 py-3 text-xl font-semibold focus:outline-none"
        />
      </div>
      <p id="bid-hint" className={`mt-1.5 text-sm ${valid ? "text-quiet" : "text-alert"}`}>
        {valid ? `Minimum ${formatMoney(minNext)}, in steps of ${formatMoney(a.minIncrement)}.` : `Bid at least ${formatMoney(minNext)}.`}
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        {quick.map((q) => (
          <button
            type="button"
            key={q}
            onClick={() => setRaw(q.toFixed(2))}
            className="figures rounded-full border border-rule px-3 py-1 text-sm font-medium hover:border-verdigris hover:text-verdigris-deep"
          >
            {formatMoney(q)}
          </button>
        ))}
      </div>
      {place.isError ? (
        <div className="mt-4">
          <ErrorNotice>{errorText(place.error)}</ErrorNotice>
        </div>
      ) : null}
      <Button type="submit" disabled={!valid || place.isPending} className="mt-4 w-full !py-3 text-base">
        {place.isPending ? "Placing bid" : `Place bid of ${formatMoney(amount)}`}
      </Button>
    </form>
  );
}
