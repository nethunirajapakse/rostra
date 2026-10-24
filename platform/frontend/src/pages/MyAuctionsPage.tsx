import { Link } from "react-router";
import { AuctionList } from "../components/AuctionList";
import { buttonClass } from "../lib/ui";
import { useTitle } from "../lib/useTitle";
import { useSession } from "../store/session";

export function MyAuctionsPage() {
  useTitle("My auctions");
  const me = useSession((s) => s.user?.userId);
  return (
    <>
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <h1 className="font-display text-5xl leading-[1.05]">My auctions</h1>
        <Link to="/sell" className={buttonClass("primary")}>
          Start an auction
        </Link>
      </div>
      <AuctionList
        sellerId={me}
        empty={
          <div className="border border-dashed border-rule bg-slab/60 px-6 py-12 text-center rounded-ui">
            <p className="font-display text-2xl">You have not listed anything</p>
            <p className="mx-auto mt-2 max-w-md text-quiet">Set a starting price and a closing time, and bidding opens by itself.</p>
          </div>
        }
      />
    </>
  );
}
