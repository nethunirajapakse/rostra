package com.rostra.bidding.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        UUID bidderId,
        BigDecimal amount,
        Instant placedAt
) {}
