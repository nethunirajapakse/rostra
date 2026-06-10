package com.rostra.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuctionEndedEvent(
        UUID auctionId,
        UUID sellerId,
        Instant endedAt
) {}
