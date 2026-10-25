package com.rostra.auction.dto;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        Instant createdAt,
        Long version,
        String coverUrl,
        List<AuctionImageDTO> images,
        /** Whether the signed-in caller watches this auction. Always false for anonymous callers. */
        boolean watching
) {
    /** {@code images} must already be in display order; the first one is the cover. */
    public static AuctionResponseDTO from(Auction a, List<AuctionImageDTO> images, boolean watching) {
        return new AuctionResponseDTO(
                a.getId(),
                a.getSellerId(),
                a.getStatus() == AuctionStatus.ENDED ? a.getWinnerId() : null,
                a.getTitle(),
                a.getDescription(),
                a.getStartingPrice(),
                a.getCurrentPrice(),
                a.getMinIncrement(),
                a.getFinalPrice(),
                a.getStartsAt(),
                a.getEndsAt(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getVersion(),
                images.isEmpty() ? null : images.get(0).url(),
                images,
                watching
        );
    }
}
