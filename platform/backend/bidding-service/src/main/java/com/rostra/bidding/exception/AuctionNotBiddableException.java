package com.rostra.bidding.exception;

public class AuctionNotBiddableException extends RuntimeException {
    public AuctionNotBiddableException(String message) {
        super(message);
    }
}
