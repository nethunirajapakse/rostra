package com.rostra.auction.repository;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.Watchlist;
import com.rostra.auction.entity.WatchlistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, WatchlistId> {

    Page<Watchlist> findByIdUserId(UUID userId, Pageable pageable);

    long countByIdAuctionId(UUID auctionId);

    void deleteByIdUserIdAndIdAuctionId(UUID userId, UUID auctionId);

    /**
     * Adds the auction to the user's watchlist unless it is already there. A single atomic statement, so two
     * concurrent "watch" requests cannot collide on the primary key; the second one simply does nothing.
     *
     * @return 1 if a row was inserted, 0 if the user was already watching
     */
    @Modifying
    @Query(value = """
            INSERT INTO watchlist (user_id, auction_id, added_at)
            VALUES (:userId, :auctionId, now())
            ON CONFLICT (user_id, auction_id) DO NOTHING
            """, nativeQuery = true)
    int addIfAbsent(@Param("userId") UUID userId, @Param("auctionId") UUID auctionId);

    /** @return 1 if a row was removed, 0 if the user was not watching */
    @Modifying
    @Query("delete from Watchlist w where w.id.userId = :userId and w.id.auctionId = :auctionId")
    int remove(@Param("userId") UUID userId, @Param("auctionId") UUID auctionId);

    /** The auctions one user watches, most recently added first. Never returns another user's rows. */
    @Query(value = """
            select a from Auction a
            join Watchlist w on w.id.auctionId = a.id
            where w.id.userId = :userId
            order by w.addedAt desc, a.id desc
            """,
            countQuery = "select count(w) from Watchlist w where w.id.userId = :userId")
    Page<Auction> findWatchedAuctions(@Param("userId") UUID userId, Pageable pageable);
}
