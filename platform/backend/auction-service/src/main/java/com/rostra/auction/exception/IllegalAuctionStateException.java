package com.rostra.auction.exception;

public class IllegalAuctionStateException extends RuntimeException {
    public IllegalAuctionStateException(String message) {
        super(message);
    }
}
