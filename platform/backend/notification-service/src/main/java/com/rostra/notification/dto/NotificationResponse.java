package com.rostra.notification.dto;

import com.rostra.notification.entity.Notification;
import com.rostra.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        UUID auctionId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getMessage(),
                n.getAuctionId(), n.isRead(), n.getCreatedAt()
        );
    }
}
