import { Link, useSearchParams } from "react-router";
import { useWatchlist } from "../api/auctions";
import { LotGrid } from "../components/LotCard";
import { Button, ErrorNotice, Spinner } from "../components/ui";
import { errorText } from "../lib/api";
import { buttonClass } from "../lib/ui";
import { useTitle } from "../lib/useTitle";

export function WatchlistPage() {
  useTitle("Watchlist");
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const query = useWatchlist(page);
  const goTo = (n: number) => setParams(n > 0 ? { page: String(n) } : {}, { replace: true });

  return (
    <>
      <h1 className="mb-1 text-4xl leading-none">Watchlist</h1>
      <p className="mb-5 text-quiet">Auctions you are watching, most recently added first.</p>

      {query.isPending ? (
        <Spinner label="Loading your watchlist" />
      ) : query.isError ? (
        <ErrorNotice>{errorText(query.error)}</ErrorNotice>
      ) : query.data.content.length === 0 ? (
        <div className="rounded-ui border border-dashed border-rule bg-surface px-6 py-12 text-center">
          <p className="font-display text-3xl">Nothing on your watchlist</p>
          <p className="mx-auto mt-2 max-w-md text-quiet">Press Watch on any auction and it will be kept here.</p>
          <Link to="/" className={buttonClass("primary", "mt-5")}>
            Browse auctions
          </Link>
        </div>
      ) : (
        <>
          <LotGrid auctions={query.data.content} dim={query.isPlaceholderData} />
          {query.data.totalPages > 1 ? (
            <nav className="mt-5 flex items-center justify-between" aria-label="Pages">
              <Button variant="quiet" disabled={query.data.first} onClick={() => goTo(page - 1)}>
                Previous
              </Button>
              <p className="figures text-sm text-quiet">
                Page {query.data.number + 1} of {query.data.totalPages}
              </p>
              <Button variant="quiet" disabled={query.data.last} onClick={() => goTo(page + 1)}>
                Next
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </>
  );
}
