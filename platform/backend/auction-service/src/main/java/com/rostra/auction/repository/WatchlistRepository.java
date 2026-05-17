package com.rostra.auction.repository;

import com.rostra.auction.entity.Watchlist;
import com.rostra.auction.entity.WatchlistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, WatchlistId> {

    Page<Watchlist> findByIdUserId(UUID userId, Pageable pageable);

    long countByIdAuctionId(UUID auctionId);

    void deleteByIdUserIdAndIdAuctionId(UUID userId, UUID auctionId);
}
