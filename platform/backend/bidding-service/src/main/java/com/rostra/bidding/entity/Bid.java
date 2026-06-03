package com.rostra.bidding.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "bids",
        indexes = {
                @Index(name = "idx_bids_auction_id_placed_at", columnList = "auction_id, placed_at"),
                @Index(name = "idx_bids_bidder_id", columnList = "bidder_id")
        }
)
public class Bid {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BidStatus status;

    @CreationTimestamp
    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    protected Bid() {}

    public Bid(UUID auctionId, UUID bidderId, BigDecimal amount, BidStatus status) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.status = status;
    }

    // getters
    public UUID getId() { return id; }
    public UUID getAuctionId() { return auctionId; }
    public UUID getBidderId() { return bidderId; }
    public BigDecimal getAmount() { return amount; }
    public BidStatus getStatus() { return status; }
    public Instant getPlacedAt() { return placedAt; }

    // setters that make sense (status changes; other fields immutable post-creation)
    public void setStatus(BidStatus status) { this.status = status; }
}