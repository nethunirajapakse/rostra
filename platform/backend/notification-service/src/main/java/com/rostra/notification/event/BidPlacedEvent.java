package com.rostra.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        UUID bidderId,
        BigDecimal amount,
        Instant placedAt
) {}
