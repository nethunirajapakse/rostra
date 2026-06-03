package com.rostra.bidding.repository;

import com.rostra.bidding.entity.Bid;
import com.rostra.bidding.entity.BidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BidRepository extends JpaRepository<Bid, UUID> {

    Page<Bid> findByAuctionIdOrderByPlacedAtDesc(UUID auctionId, Pageable pageable);

    Page<Bid> findByBidderIdOrderByPlacedAtDesc(UUID bidderId, Pageable pageable);

    Optional<Bid> findTopByAuctionIdAndStatusOrderByAmountDesc(UUID auctionId, BidStatus status);
}
