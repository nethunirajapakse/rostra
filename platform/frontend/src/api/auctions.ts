import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { Auction, AuctionStatus, Bid, Page } from "../lib/types";

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
