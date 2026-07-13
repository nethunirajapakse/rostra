package com.rostra.auction.service;

import com.rostra.auction.dto.CreateAuctionRequestDTO;
import com.rostra.auction.dto.UpdateAuctionRequestDTO;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.repository.AuctionSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.math.BigDecimal;

public interface AuctionService {

    Auction create(UUID sellerId, CreateAuctionRequestDTO req);

    Auction findById(UUID id);

    Page<Auction> search(AuctionSearchCriteria criteria, Pageable pageable);

    Auction update(UUID userId, UUID auctionId, UpdateAuctionRequestDTO req);

    Auction cancel(UUID userId, UUID auctionId);

    Auction updateCurrentPrice(UUID auctionId, BigDecimal newPrice, Long expectedVersion);
}