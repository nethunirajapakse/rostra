package com.rostra.auction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "watchlist",
        indexes = {
                @Index(name = "idx_watchlist_auction_id", columnList = "auction_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watchlist {

    @EmbeddedId
    private WatchlistId id;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public Watchlist(UUID userId, UUID auctionId) {
        this.id =
                new WatchlistId(userId, auctionId);
    }
}
