package com.rostra.auction.repository;

import com.rostra.auction.entity.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionSearchCriteria(
        AuctionStatus status,
        UUID sellerId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Instant endingBefore,
        String search
) {}