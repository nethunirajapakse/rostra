import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { api, errorText } from "../lib/api";
import type { Auction, AuctionImage, AuctionStatus, Bid, Page } from "../lib/types";
import { toast } from "../store/toasts";

export interface AuctionFilters {
  status?: AuctionStatus;
  sellerId?: string;
  search?: string;
  page: number;
}

const PAGE_SIZE = 12;

export function useAuctions(filters: AuctionFilters) {
  const params = new URLSearchParams({ page: String(filters.page), size: String(PAGE_SIZE) });
  if (filters.status) params.set("status", filters.status);
  if (filters.sellerId) params.set("sellerId", filters.sellerId);
  if (filters.search) params.set("search", filters.search);
  // Open lots: the one closing first comes first. Everything else: newest first.
  const closingSoonFirst = filters.status === "ACTIVE" || filters.status === "SCHEDULED";
  params.set("sort", closingSoonFirst ? "endsAt,asc" : "createdAt,desc");

  return useQuery({
    queryKey: ["auctions", filters],
    queryFn: () => api.get<Page<Auction>>(`/auctions?${params}`),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  });
}

const liveStatuses = new Set<string>(["ACTIVE", "SCHEDULED"]);

export function useAuction(id: string) {
  return useQuery({
    queryKey: ["auction", id],
    queryFn: () => api.get<Auction>(`/auctions/${id}`),
    // The price can change at any moment while a lot is open; poll as a safety net next to the live pushes.
    refetchInterval: (q) => (q.state.data && liveStatuses.has(q.state.data.status) ? 4_000 : false),
  });
}

export interface NewAuction {
  title: string;
  description: string;
  startingPrice: number;
  minIncrement: number;
  startsAt: string;
  endsAt: string;
}

export function useCreateAuction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: NewAuction) => api.post<Auction>("/auctions", input),
    onSuccess: (auction) => {
      qc.setQueryData(["auction", auction.id], auction); // the next page renders instantly
      qc.invalidateQueries({ queryKey: ["auctions"] });
    },
  });
}

export function useCancelAuction(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.del<Auction>(`/auctions/${id}`),
    onSuccess: (auction) => {
      qc.setQueryData(["auction", id], auction);
      qc.invalidateQueries({ queryKey: ["auctions"] });
    },
  });
}

export function useBids(auctionId: string, live: boolean) {
  return useQuery({
    queryKey: ["bids", auctionId],
    queryFn: () => api.get<Page<Bid>>(`/bids?auctionId=${auctionId}&size=50&sort=placedAt,desc`),
    refetchInterval: live ? 4_000 : false,
  });
}

export function usePlaceBid(auctionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (amount: number) => api.post<Bid>("/bids", { auctionId, amount }),
    onSettled: () => {
      // Success or failure (someone else may have just outbid us): show the truth.
      qc.invalidateQueries({ queryKey: ["auction", auctionId] });
      qc.invalidateQueries({ queryKey: ["bids", auctionId] });
    },
  });
}

// ---- watchlist -------------------------------------------------------------------------------------------

/** The signed-in user's watched auctions, most recently added first. */
export function useWatchlist(page: number) {
  return useQuery({
    queryKey: ["watchlist", page],
    queryFn: () => api.get<Page<Auction>>(`/me/watchlist?page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  });
}

/** Flip the "watching" flag in every cached copy of the auction, so the button reacts before the server answers. */
function setWatching(qc: QueryClient, id: string, watching: boolean) {
  qc.setQueryData<Auction>(["auction", id], (old) => (old ? { ...old, watching } : old));
  for (const key of [["auctions"], ["watchlist"]]) {
    qc.setQueriesData<Page<Auction>>({ queryKey: key }, (old) =>
      old ? { ...old, content: old.content.map((a) => (a.id === id ? { ...a, watching } : a)) } : old,
    );
  }
}

export function useToggleWatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, watch }: { id: string; watch: boolean }) =>
      watch ? api.post<unknown>(`/auctions/${id}/watch`) : api.del<unknown>(`/auctions/${id}/watch`),
    onMutate: ({ id, watch }) => setWatching(qc, id, watch),
    onError: (e, { id, watch }) => {
      setWatching(qc, id, !watch);
      toast.bad(errorText(e));
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["watchlist"] }),
  });
}

// ---- photos ----------------------------------------------------------------------------------------------

function refreshAuction(qc: QueryClient, auctionId: string) {
  qc.invalidateQueries({ queryKey: ["auction", auctionId] });
  qc.invalidateQueries({ queryKey: ["auctions"] });
  qc.invalidateQueries({ queryKey: ["watchlist"] });
}

/** Uploads one file. Callers send several photos one after another, which keeps each request small. */
export function useUploadImage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ auctionId, file }: { auctionId: string; file: File }) => {
      const form = new FormData();
      form.append("file", file);
      return api.upload<AuctionImage>(`/auctions/${auctionId}/images`, form);
    },
    onSettled: (_data, _err, { auctionId }) => refreshAuction(qc, auctionId),
  });
}

export function useDeleteImage(auctionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: string) => api.del<void>(`/auctions/${auctionId}/images/${imageId}`),
    onSettled: () => refreshAuction(qc, auctionId),
  });
}

/** Send every image id in the new order; the first becomes the cover. */
export function useReorderImages(auctionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageIds: string[]) => api.put<AuctionImage[]>(`/auctions/${auctionId}/images/order`, { imageIds }),
    onSettled: () => refreshAuction(qc, auctionId),
  });
}
