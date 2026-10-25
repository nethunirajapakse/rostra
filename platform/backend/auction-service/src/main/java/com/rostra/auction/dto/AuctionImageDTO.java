package com.rostra.auction.dto;

import java.util.UUID;

/** {@code url} is relative to the web app's origin: /auctions/{auctionId}/images/{imageId}/content */
public record AuctionImageDTO(UUID id, String url, int position) {
}
