import type { ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import { useAuctions } from "../api/auctions";
import { errorText } from "../lib/api";
import type { AuctionStatus } from "../lib/types";
import { Button, ErrorNotice, Spinner } from "./ui";
import { LotGrid } from "./LotCard";

type Tab = "ACTIVE" | "SCHEDULED" | "ENDED" | "ALL";
const TABS: { id: Tab; label: string }[] = [
  { id: "ACTIVE", label: "Live" },
  { id: "SCHEDULED", label: "Upcoming" },
  { id: "ENDED", label: "Ended" },
  { id: "ALL", label: "All" },
];
const isTab = (v: string | null): v is Tab => TABS.some((t) => t.id === v);

const SORTS = [
  { id: "ending", label: "Ending soonest", value: "endsAt,asc" },
  { id: "newest", label: "Newest", value: "createdAt,desc" },
  { id: "low", label: "Price: low to high", value: "currentPrice,asc" },
  { id: "high", label: "Price: high to low", value: "currentPrice,desc" },
];

/** Filters live in the URL, so a filtered view can be linked and survives a refresh. The search box is in the header. */
export function AuctionList({ sellerId, empty }: { sellerId?: string; empty: ReactNode }) {
  const [params, setParams] = useSearchParams();
  const raw = params.get("show");
  const tab: Tab = isTab(raw) ? raw : sellerId ? "ALL" : "ACTIVE";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const search = params.get("q") ?? "";
  const sort = SORTS.find((s) => s.id === params.get("sort"));

  const query = useAuctions({
    status: tab === "ALL" ? undefined : (tab as AuctionStatus),
    sellerId,
    search: search || undefined,
    sort: sort?.value,
    page,
  });

  const update = (next: Record<string, string | null>) => {
    const p = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v) p.set(k, v);
      else p.delete(k);
    }
    setParams(p, { replace: true });
  };

  return (
    <div>
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div role="tablist" aria-label="Show" className="flex flex-wrap gap-2">
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => update({ show: t.id, page: null })}
              className={`rounded-full border px-4 py-1.5 text-sm font-bold ${
                tab === t.id ? "border-ink bg-ink text-white" : "border-rule bg-surface text-ink hover:border-ink"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-quiet">
          Sort by
          <select
            value={sort?.id ?? "default"}
            onChange={(e) => update({ sort: e.target.value === "default" ? null : e.target.value, page: null })}
            className="rounded-ui border border-rule bg-surface px-2.5 py-2 text-sm font-semibold text-ink"
          >
            <option value="default">Recommended</option>
            {SORTS.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {search ? (
        <p className="mb-4 text-sm text-quiet">
          {query.data ? `${query.data.totalElements} ${query.data.totalElements === 1 ? "result" : "results"}` : "Searching"} for{" "}
          <strong className="text-ink">“{search}”</strong>.{" "}
          <Link to="/" className="font-semibold text-link hover:underline">
            Clear search
          </Link>
        </p>
      ) : null}

      {query.isPending ? (
        <Spinner label="Loading auctions" />
      ) : query.isError ? (
        <ErrorNotice>{errorText(query.error)}</ErrorNotice>
      ) : query.data.content.length === 0 ? (
        search ? (
          <p className="rounded-ui border border-dashed border-rule bg-surface px-6 py-12 text-center text-quiet">
            Nothing matches “{search}” here. Try a shorter search or another tab.
          </p>
        ) : (
          empty
        )
      ) : (
        <>
          <LotGrid auctions={query.data.content} dim={query.isPlaceholderData} />
          {query.data.totalPages > 1 ? (
            <nav className="mt-8 flex items-center justify-between" aria-label="Pages">
              <Button variant="quiet" disabled={query.data.first} onClick={() => update({ page: String(page - 1) })}>
                Previous
              </Button>
              <p className="figures text-sm text-quiet">
                Page {query.data.number + 1} of {query.data.totalPages}
              </p>
              <Button variant="quiet" disabled={query.data.last} onClick={() => update({ page: String(page + 1) })}>
                Next
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </div>
  );
}
