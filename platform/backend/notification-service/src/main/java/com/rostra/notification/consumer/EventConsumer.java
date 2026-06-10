package com.rostra.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.notification.entity.Notification;
import com.rostra.notification.entity.NotificationType;
import com.rostra.notification.event.AuctionEndedEvent;
import com.rostra.notification.event.BidPlacedEvent;
import com.rostra.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public EventConsumer(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.bid-placed}", groupId = "notification-service")
    @Transactional
    public void onBidPlaced(String payload) {
        try {
            BidPlacedEvent event = objectMapper.readValue(payload, BidPlacedEvent.class);
            log.info("Received bid.placed: bidId={}, auctionId={}, amount={}",
                    event.bidId(), event.auctionId(), event.amount());

            // For v1 we don't know the seller's userId without calling auction-service.
            // We'll persist a placeholder notification for the bidder confirming the bid was received.
            // (Notifying the seller is a follow-up issue — needs auction lookup or enriched event.)
            Notification notification = new Notification(
                    event.bidderId(),
                    NotificationType.BID_RECEIVED_ON_YOUR_AUCTION,  // generic for v1
                    String.format("Your bid of %s was placed", event.amount()),
                    event.auctionId()
            );
            notificationRepository.save(notification);

            log.info("Persisted bid-placed notification for user {}", event.bidderId());
        } catch (Exception e) {
            log.error("Failed to process bid.placed event: {}", e.getMessage(), e);
            // For at-least-once delivery, throwing causes the consumer to retry.
            // For now we log and swallow to avoid infinite retries on bad messages.
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.auction-ended}", groupId = "notification-service")
    @Transactional
    public void onAuctionEnded(String payload) {
        try {
            AuctionEndedEvent event = objectMapper.readValue(payload, AuctionEndedEvent.class);
            log.info("Received auction.ended: auctionId={}, sellerId={}",
                    event.auctionId(), event.sellerId());

            Notification sellerNotification = new Notification(
                    event.sellerId(),
                    NotificationType.AUCTION_ENDED_AS_SELLER,
                    "Your auction has ended",
                    event.auctionId()
            );
            notificationRepository.save(sellerNotification);

            log.info("Persisted auction-ended notification for seller {}", event.sellerId());
        } catch (Exception e) {
            log.error("Failed to process auction.ended event: {}", e.getMessage(), e);
        }
    }
}
