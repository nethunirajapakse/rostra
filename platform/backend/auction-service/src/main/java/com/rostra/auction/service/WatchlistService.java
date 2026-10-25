package com.rostra.auction.service;

import com.rostra.auction.entity.Auction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WatchlistService {

    /** Idempotent: watching an auction that is already watched changes nothing. */
    void watch(UUID userId, UUID auctionId);

    /** Idempotent: unwatching an auction that is not watched changes nothing. */
    void unwatch(UUID userId, UUID auctionId);

    /** The auctions {@code userId} watches, most recently added first. */
    Page<Auction> listWatched(UUID userId, Pageable pageable);
}
