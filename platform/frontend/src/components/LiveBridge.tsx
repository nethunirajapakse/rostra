import { useEffect } from "react";
import { queryClient } from "../api/queryClient";
import { startLiveNotifications } from "../lib/live";
import { useSession } from "../store/session";

/** Renders nothing. Holds the WebSocket open for as long as someone is signed in. */
export function LiveBridge() {
  const userId = useSession((s) => s.user?.userId);
  useEffect(() => {
    if (!userId) return;
    return startLiveNotifications(queryClient);
  }, [userId]);
  return null;
}
