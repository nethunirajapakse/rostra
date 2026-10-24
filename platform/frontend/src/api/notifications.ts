import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { AppNotification, Page } from "../lib/types";
import { useSession } from "../store/session";

export function useNotifications(page = 0, size = 8) {
  const signedIn = useSession((s) => s.status === "authed");
  return useQuery({
    queryKey: ["notifications", page, size],
    queryFn: () => api.get<Page<AppNotification>>(`/me/notifications?page=${page}&size=${size}`),
    enabled: signedIn,
    placeholderData: keepPreviousData,
  });
}

export function useUnreadCount() {
  const signedIn = useSession((s) => s.status === "authed");
  return useQuery({
    queryKey: ["unread"],
    queryFn: () => api.get<{ unreadCount: number }>("/me/notifications/unread-count"),
    enabled: signedIn,
    select: (d) => d.unreadCount,
    refetchInterval: 60_000, // the WebSocket is the fast path; this only catches a missed push
  });
}

function useRefreshNotifications() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["notifications"] });
    qc.invalidateQueries({ queryKey: ["unread"] });
  };
}

export function useMarkRead() {
  const refresh = useRefreshNotifications();
  return useMutation({
    mutationFn: (id: string) => api.patch<AppNotification>(`/me/notifications/${id}/read`),
    onSuccess: refresh,
  });
}

export function useMarkAllRead() {
  const refresh = useRefreshNotifications();
  return useMutation({
    mutationFn: () => api.post<{ updated: number }>("/me/notifications/read-all"),
    onSuccess: refresh,
  });
}
