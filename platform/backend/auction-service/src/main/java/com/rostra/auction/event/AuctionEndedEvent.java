package com.rostra.auction.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionEndedEvent(
        UUID auctionId,
        UUID sellerId,
        UUID winnerId,
        BigDecimal finalPrice,
        Instant endedAt
) {}