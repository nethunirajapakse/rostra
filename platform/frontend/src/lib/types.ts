export type AuctionStatus = "DRAFT" | "SCHEDULED" | "ACTIVE" | "ENDED" | "CANCELLED";

export interface User {
  userId: string;
  name: string;
  email: string;
}

export interface Auction {
  id: string;
  sellerId: string;
  /** Set only once the auction has ended. */
  winnerId: string | null;
  title: string;
  description: string | null;
  startingPrice: number;
  currentPrice: number;
  minIncrement: number;
  finalPrice: number | null;
  startsAt: string;
  endsAt: string;
  status: AuctionStatus;
  createdAt: string;
  version: number;
}

export interface Bid {
  id: string;
  auctionId: string;
  bidderId: string;
  amount: number;
  status: string;
  placedAt: string;
}

export type NotificationType =
  | "BID_RECEIVED_ON_YOUR_AUCTION"
  | "BID_PLACED"
  | "OUTBID"
  | "AUCTION_WON"
  | "AUCTION_ENDED_AS_SELLER"
  | "AUCTION_ENDED_AS_BIDDER";

export interface AppNotification {
  id: string;
  type: NotificationType;
  message: string;
  auctionId: string | null;
  read: boolean;
  createdAt: string;
}

/** Spring Data page. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
