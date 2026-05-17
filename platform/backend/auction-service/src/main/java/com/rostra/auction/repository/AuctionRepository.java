package com.rostra.auction.repository;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuctionRepository
        extends JpaRepository<Auction, UUID>, AuctionQueryRepository {

    Page<Auction> findBySellerId(UUID sellerId, Pageable pageable);

    List<Auction> findByStatusAndStartsAtLessThanEqual(AuctionStatus status, Instant cutoff);

    List<Auction> findByStatusAndEndsAtLessThanEqual(AuctionStatus status, Instant cutoff);
}
