package com.rostra.auction.repository;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionRepository
        extends JpaRepository<Auction, UUID>, AuctionQueryRepository {

    /** Row lock, so two simultaneous uploads cannot both slip under the per-auction image limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") UUID id);

    Page<Auction> findBySellerId(UUID sellerId, Pageable pageable);

    List<Auction> findByStatusAndStartsAtLessThanEqual(AuctionStatus status, Instant cutoff);

    List<Auction> findByStatusAndEndsAtLessThanEqual(AuctionStatus status, Instant cutoff);
}
