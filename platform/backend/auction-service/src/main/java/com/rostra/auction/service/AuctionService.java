package com.rostra.auction.service;

import com.rostra.auction.dto.CreateAuctionRequestDTO;
import com.rostra.auction.dto.UpdateAuctionRequestDTO;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.repository.AuctionSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuctionService {

    /**
     * Creates a new auction for a given seller.
     *
     * @param sellerId the unique identifier of the seller
     * @param req      the data transfer object containing auction details
     * @return the saved Auction entity
     * @throws IllegalArgumentException if the end time is not after the start time
     */
    Auction create(UUID sellerId, CreateAuctionRequestDTO req);

    Auction findById(UUID id);

    Page<Auction> search(AuctionSearchCriteria criteria, Pageable pageable);

    Auction update(UUID userId, UUID auctionId, UpdateAuctionRequestDTO req);

    Auction cancel(UUID userId, UUID auctionId);
}