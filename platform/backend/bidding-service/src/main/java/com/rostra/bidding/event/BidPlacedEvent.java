package com.rostra.bidding.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        UUID sellerId,
        UUID bidderId,
        UUID previousBidderId,
        BigDecimal amount,
        Instant placedAt
) {}
