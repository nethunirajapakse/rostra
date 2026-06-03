package com.rostra.bidding.exception;

public class AuctionServiceUnavailableException extends RuntimeException {
    public AuctionServiceUnavailableException(String message) {
        super(message);
    }
}
