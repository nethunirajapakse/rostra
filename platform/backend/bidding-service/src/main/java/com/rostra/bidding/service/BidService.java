package com.rostra.bidding.service;

import com.rostra.bidding.dto.PlaceBidRequestDTO;
import com.rostra.bidding.entity.Bid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BidService {
    Bid placeBid(UUID bidderId, PlaceBidRequestDTO request);

    /** Bid history of one auction, newest first. */
    Page<Bid> listForAuction(UUID auctionId, Pageable pageable);
}