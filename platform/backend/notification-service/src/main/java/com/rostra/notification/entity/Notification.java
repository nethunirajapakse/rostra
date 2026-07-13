package com.rostra.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_notifications_user_id_read", columnList = "user_id, read_flag")
        }
)
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "auction_id")
    private UUID auctionId;

    @Column(name = "read_flag", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(UUID userId, NotificationType type, String message, UUID auctionId) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.auctionId = auctionId;
        this.read = false;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public UUID getAuctionId() { return auctionId; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead() { this.read = true; }
}
