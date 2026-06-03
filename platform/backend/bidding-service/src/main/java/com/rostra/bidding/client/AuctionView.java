package com.rostra.bidding.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuctionView(
        UUID id,
        UUID sellerId,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        Instant startsAt,
        Instant endsAt,
        String status,
        Long version
) {}
