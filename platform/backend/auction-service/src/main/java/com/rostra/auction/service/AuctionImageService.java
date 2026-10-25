package com.rostra.auction.service;

import com.rostra.auction.entity.AuctionImage;

import java.util.List;
import java.util.UUID;

public interface AuctionImageService {

    record ImageContent(byte[] data, String contentType) {
    }

    /** The stored file, for anyone (auctions and their photos are public). */
    ImageContent read(UUID auctionId, UUID imageId);

    /** Validates and stores one image at the end of the auction's gallery. Seller only. */
    AuctionImage add(UUID userId, UUID auctionId, byte[] data);

    void delete(UUID userId, UUID auctionId, UUID imageId);

    /** {@code orderedIds} must name every image of the auction exactly once. Returns the new order. */
    List<AuctionImage> reorder(UUID userId, UUID auctionId, List<UUID> orderedIds);
}
