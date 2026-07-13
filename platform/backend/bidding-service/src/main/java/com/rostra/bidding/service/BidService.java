package com.rostra.bidding.service;

import com.rostra.bidding.dto.PlaceBidRequestDTO;
import com.rostra.bidding.entity.Bid;

import java.util.UUID;

public interface BidService {
    Bid placeBid(UUID bidderId, PlaceBidRequestDTO request, String bearerToken);
}