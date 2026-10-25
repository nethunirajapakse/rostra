import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "../api/notifications";
import { timeAgo } from "../lib/format";
import { useNow } from "../lib/useNow";
import { useLive } from "../store/live";
import type { AppNotification } from "../lib/types";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const now = useNow();
  const socket = useLive((s) => s.socket);
  const unread = useUnreadCount().data ?? 0;
  const list = useNotifications(0, 8);
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const openItem = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id);
    setOpen(false);
    if (n.auctionId) navigate(`/lot/${n.auctionId}`);
  };

  return (
    <div ref={root} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        aria-expanded={open}
        className="relative flex size-10 items-center justify-center rounded-ui border border-rule bg-surface text-ink hover:border-quiet"
      >
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
          <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" strokeLinejoin="round" />
          <path d="M10 19a2 2 0 0 0 4 0" strokeLinecap="round" />
        </svg>
        {unread > 0 ? (
          <span className="figures absolute -right-1.5 -top-1.5 flex min-w-5 items-center justify-center rounded-full bg-alert px-1 text-xs font-bold text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        ) : null}
        <span
          title={socket === "live" ? "Live updates on" : "Live updates off"}
          className={`absolute bottom-1 right-1 size-1.5 rounded-full ${socket === "live" ? "bg-good" : "bg-rule"}`}
          aria-hidden
        />
      </button>

      {open ? (
        <div className="absolute right-0 z-40 mt-2 w-[min(22rem,calc(100vw-2rem))] rounded-ui border border-rule bg-surface">
          <div className="flex items-center justify-between border-b border-rule px-4 py-3">
            <p className="font-semibold">Notifications</p>
            <button
              onClick={() => markAll.mutate()}
              disabled={unread === 0 || markAll.isPending}
              className="text-sm font-semibold text-link hover:underline disabled:text-quiet disabled:no-underline"
            >
              Mark all read
            </button>
          </div>
          {list.data && list.data.content.length > 0 ? (
            <ul className="max-h-96 overflow-y-auto">
              {list.data.content.map((n) => (
                <li key={n.id} className="border-b border-rule last:border-b-0">
                  <button onClick={() => openItem(n)} className="flex w-full gap-3 px-4 py-3 text-left hover:bg-canvas">
                    <span className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? "bg-transparent" : "bg-signal-deep"}`} aria-hidden />
                    <span className="flex-1">
                      <span className={`block text-sm ${n.read ? "text-quiet" : "font-medium"}`}>{n.message}</span>
                      <span className="mt-0.5 block text-xs text-quiet">{timeAgo(n.createdAt, now)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-4 py-8 text-center text-sm text-quiet">Nothing yet. Bids and results will show up here as they happen.</p>
          )}
          <Link to="/inbox" onClick={() => setOpen(false)} className="block border-t border-rule px-4 py-3 text-center text-sm font-semibold text-link hover:bg-canvas">
            See all notifications
          </Link>
        </div>
      ) : null}
    </div>
  );
}
