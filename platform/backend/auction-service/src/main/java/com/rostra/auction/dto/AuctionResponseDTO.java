package com.rostra.auction.dto;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionResponseDTO(
        UUID id,
        UUID sellerId,
        UUID winnerId,
        String title,
        String description,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        BigDecimal finalPrice,
        Instant startsAt,
        Instant endsAt,
        AuctionStatus status,
        Instant createdAt
) {
    public static AuctionResponseDTO from(Auction a) {
        return new AuctionResponseDTO(
                a.getId(),
                a.getSellerId(),
                a.getWinnerId(),
                a.getTitle(),
                a.getDescription(),
                a.getStartingPrice(),
                a.getCurrentPrice(),
                a.getMinIncrement(),
                a.getFinalPrice(),
                a.getStartsAt(),
                a.getEndsAt(),
                a.getStatus(),
                a.getCreatedAt()
        );
    }
}
