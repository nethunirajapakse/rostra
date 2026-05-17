package com.rostra.auction.exception;

import java.util.UUID;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(UUID id) {
        super("Auction not found: " + id);
    }
}