import { Link } from "react-router";
import { AuctionList } from "../components/AuctionList";
import { buttonClass } from "../lib/ui";
import { useTitle } from "../lib/useTitle";
import { useSession } from "../store/session";

export function AuctionsPage() {
  useTitle("Auctions");
  const signedIn = useSession((s) => s.status === "authed");
  return (
    <>
      <div className="mb-8 max-w-xl">
        <h1 className="font-display text-5xl leading-[1.05]">Auctions</h1>
        <p className="mt-3 text-lg text-quiet">Place a bid and watch the price move as other people bid.</p>
      </div>
      <AuctionList
        empty={
          <div className="border border-dashed border-rule bg-slab/60 px-6 py-12 text-center rounded-ui">
            <p className="font-display text-2xl">No auctions here yet</p>
            <p className="mx-auto mt-2 max-w-md text-quiet">Try another tab, or list the first item yourself.</p>
            <Link to={signedIn ? "/sell" : "/signup"} className={buttonClass("primary", "mt-5")}>
              {signedIn ? "Start an auction" : "Create an account to sell"}
            </Link>
          </div>
        }
      />
    </>
  );
}
