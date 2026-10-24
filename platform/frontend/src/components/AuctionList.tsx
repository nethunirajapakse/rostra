import { useState, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import { useAuctions } from "../api/auctions";
import { errorText } from "../lib/api";
import type { AuctionStatus } from "../lib/types";
import { Button, ErrorNotice, Spinner } from "./ui";
import { LotRow } from "./LotRow";

type Tab = "ACTIVE" | "SCHEDULED" | "ENDED" | "ALL";
const TABS: { id: Tab; label: string }[] = [
  { id: "ACTIVE", label: "Live" },
  { id: "SCHEDULED", label: "Upcoming" },
  { id: "ENDED", label: "Ended" },
  { id: "ALL", label: "All" },
];

const isTab = (v: string | null): v is Tab => TABS.some((t) => t.id === v);

/** Filters live in the URL, so a filtered view can be linked and survives a refresh. */
export function AuctionList({ sellerId, empty }: { sellerId?: string; empty: ReactNode }) {
  const [params, setParams] = useSearchParams();
  const raw = params.get("show");
  const tab: Tab = isTab(raw) ? raw : sellerId ? "ALL" : "ACTIVE";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const search = params.get("q") ?? "";
  const [draft, setDraft] = useState(search);

  const query = useAuctions({
    status: tab === "ALL" ? undefined : (tab as AuctionStatus),
    sellerId,
    search: search || undefined,
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
        <div role="tablist" aria-label="Show" className="flex gap-1 rounded-ui border border-rule bg-slab p-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => update({ show: t.id, page: null })}
              className={`flex-1 rounded-[4px] px-3.5 py-1.5 text-sm font-semibold sm:flex-none ${
                tab === t.id ? "bg-verdigris text-white" : "text-quiet hover:text-ink"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <form
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            update({ q: draft.trim() || null, page: null });
          }}
          className="flex gap-2"
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            type="search"
            aria-label="Search auctions"
            placeholder="Search by title"
            className="w-full rounded-ui border border-rule bg-slab px-3 py-2 text-sm placeholder:text-quiet/70 focus:border-verdigris sm:w-56"
          />
          <Button type="submit" variant="quiet" className="!py-2">
            Search
          </Button>
        </form>
      </div>

      {query.isPending ? (
        <Spinner label="Loading auctions" />
      ) : query.isError ? (
        <ErrorNotice>{errorText(query.error)}</ErrorNotice>
      ) : query.data.content.length === 0 ? (
        search ? (
          <p className="border border-dashed border-rule bg-slab/60 px-6 py-12 text-center text-quiet rounded-ui">
            Nothing matches “{search}”. Try a shorter search or another tab.
          </p>
        ) : (
          empty
        )
      ) : (
        <>
          <ul className={`overflow-hidden rounded-ui border border-rule bg-slab ${query.isPlaceholderData ? "opacity-60" : ""}`}>
            {query.data.content.map((a) => (
              <LotRow key={a.id} auction={a} />
            ))}
          </ul>
          {query.data.totalPages > 1 ? (
            <nav className="mt-5 flex items-center justify-between" aria-label="Pages">
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
