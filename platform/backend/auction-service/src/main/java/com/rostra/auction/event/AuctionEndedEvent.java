package com.rostra.auction.event;

import java.time.Instant;
import java.util.UUID;

public record AuctionEndedEvent(
        UUID auctionId,
        UUID sellerId,
        Instant endedAt
) {}