import type { QueryClient } from "@tanstack/react-query";
import type { AppNotification, NotificationType } from "./types";
import { refreshSession } from "./api";
import { useLive } from "../store/live";
import { toast, type ToastTone } from "../store/toasts";

const TONE: Record<NotificationType, ToastTone> = {
  BID_RECEIVED_ON_YOUR_AUCTION: "info",
  BID_PLACED: "good",
  OUTBID: "warn",
  AUCTION_WON: "good",
  AUCTION_ENDED_AS_SELLER: "info",
  AUCTION_ENDED_AS_BIDDER: "info",
};

const socketUrl = () => `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/notifications`;

/**
 * Keeps one WebSocket open for the signed-in user and reacts to pushed notifications: refresh the lists,
 * refresh the auction the notification is about, and show a toast. Returns a function that stops it.
 */
export function startLiveNotifications(qc: QueryClient): () => void {
  let socket: WebSocket | null = null;
  let stopped = false;
  let attempts = 0;
  let timer: number | undefined;
  const { setSocket } = useLive.getState();

  const connect = () => {
    setSocket("connecting");
    socket = new WebSocket(socketUrl());

    socket.onopen = () => {
      attempts = 0;
      setSocket("live");
      // Anything pushed while the connection was down is picked up by a refetch.
      qc.invalidateQueries({ queryKey: ["notifications"] });
      qc.invalidateQueries({ queryKey: ["unread"] });
    };

    socket.onmessage = (event) => {
      let message: { event?: string; data?: AppNotification };
      try {
        message = JSON.parse(event.data);
      } catch {
        return;
      }
      if (message.event !== "notification" || !message.data) return;
      const n = message.data;
      qc.invalidateQueries({ queryKey: ["notifications"] });
      qc.invalidateQueries({ queryKey: ["unread"] });
      if (n.auctionId) {
        qc.invalidateQueries({ queryKey: ["auction", n.auctionId] });
        qc.invalidateQueries({ queryKey: ["bids", n.auctionId] });
      }
      qc.invalidateQueries({ queryKey: ["auctions"] });
      // Your own bid is already confirmed on screen by the bidding panel; the bell still lists it.
      if (n.type !== "BID_PLACED") toast[TONE[n.type] ?? "info"](n.message, n.auctionId ? `/lot/${n.auctionId}` : undefined);
    };

    socket.onclose = () => {
      setSocket("off");
      if (stopped) return;
      attempts += 1;
      // Back off up to 15 s. A closed handshake can mean the access cookie expired, so renew it first.
      const delay = Math.min(15_000, 1_000 * 2 ** Math.min(attempts, 4));
      timer = window.setTimeout(async () => {
        if (stopped) return;
        await refreshSession();
        if (!stopped) connect();
      }, delay);
    };
  };

  connect();

  return () => {
    stopped = true;
    window.clearTimeout(timer);
    socket?.close();
    setSocket("off");
  };
}
