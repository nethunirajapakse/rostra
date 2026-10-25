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
      <h1 className="mb-4 text-4xl leading-none">Auctions</h1>
      <AuctionList
        empty={
          <div className="rounded-ui border border-dashed border-rule bg-surface px-6 py-12 text-center">
            <p className="font-display text-3xl">No auctions here yet</p>
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
