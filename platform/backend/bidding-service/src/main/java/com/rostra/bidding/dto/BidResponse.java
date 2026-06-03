package com.rostra.bidding.dto;

import com.rostra.bidding.entity.Bid;
import com.rostra.bidding.entity.BidStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidResponse(
        UUID id,
        UUID auctionId,
        UUID bidderId,
        BigDecimal amount,
        BidStatus status,
        Instant placedAt
) {
    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getAuctionId(),
                bid.getBidderId(),
                bid.getAmount(),
                bid.getStatus(),
                bid.getPlacedAt()
        );
    }
}
