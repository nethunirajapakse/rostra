import { useState } from "react";
import { Link } from "react-router";
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "../api/notifications";
import { Button, Empty, ErrorNotice, Spinner } from "../components/ui";
import { errorText } from "../lib/api";
import { timeAgo } from "../lib/format";
import { useNow } from "../lib/useNow";
import { useTitle } from "../lib/useTitle";

export function InboxPage() {
  useTitle("Notifications");
  const [page, setPage] = useState(0);
  const list = useNotifications(page, 20);
  const unread = useUnreadCount().data ?? 0;
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const now = useNow();

  return (
    <div className="max-w-2xl">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <h1 className="font-display text-4xl leading-none">Notifications</h1>
        <Button variant="quiet" disabled={unread === 0 || markAll.isPending} onClick={() => markAll.mutate()}>
          Mark all read
        </Button>
      </div>

      {list.isPending ? (
        <Spinner label="Loading notifications" />
      ) : list.isError ? (
        <ErrorNotice>{errorText(list.error)}</ErrorNotice>
      ) : list.data.content.length === 0 ? (
        <Empty title="Nothing yet">Bids on your auctions, outbid alerts and results will appear here as they happen.</Empty>
      ) : (
        <>
          <ul className="overflow-hidden rounded-ui border border-rule bg-surface">
            {list.data.content.map((n) => (
              <li key={n.id} className="flex items-start gap-3 border-b border-rule px-5 py-4 last:border-b-0">
                <span className={`mt-2 size-2 shrink-0 rounded-full ${n.read ? "bg-transparent" : "bg-signal-deep"}`} aria-hidden />
                <div className="flex-1">
                  <p className={n.read ? "text-quiet" : "font-medium"}>{n.message}</p>
                  <p className="mt-1 flex flex-wrap gap-x-4 text-sm text-quiet">
                    <span>{timeAgo(n.createdAt, now)}</span>
                    {n.auctionId ? (
                      <Link to={`/lot/${n.auctionId}`} className="font-semibold text-link hover:underline">
                        View auction
                      </Link>
                    ) : null}
                    {!n.read ? (
                      <button onClick={() => markRead.mutate(n.id)} className="font-semibold text-link hover:underline">
                        Mark read
                      </button>
                    ) : null}
                  </p>
                </div>
              </li>
            ))}
          </ul>
          {list.data.totalPages > 1 ? (
            <nav className="mt-5 flex items-center justify-between" aria-label="Pages">
              <Button variant="quiet" disabled={list.data.first} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <p className="figures text-sm text-quiet">
                Page {list.data.number + 1} of {list.data.totalPages}
              </p>
              <Button variant="quiet" disabled={list.data.last} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </div>
  );
}
