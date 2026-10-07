package com.rostra.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.notification.entity.Notification;
import com.rostra.notification.entity.NotificationType;
import com.rostra.notification.event.AuctionEndedEvent;
import com.rostra.notification.event.BidPlacedEvent;
import com.rostra.notification.repository.NotificationRepository;
import com.rostra.notification.dto.NotificationResponse;
import com.rostra.notification.ws.NotificationCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public EventConsumer(NotificationRepository notificationRepository, ObjectMapper objectMapper,
                         ApplicationEventPublisher events) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /** Store the notification; it is pushed over WebSocket only after this transaction commits. */
    private void saveAndPush(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        events.publishEvent(new NotificationCreatedEvent(saved.getUserId(), NotificationResponse.from(saved)));
    }

    @KafkaListener(topics = "${app.kafka.topics.bid-placed}", groupId = "notification-service")
    @Transactional
    public void onBidPlaced(String payload) {
        try {
            BidPlacedEvent event = objectMapper.readValue(payload, BidPlacedEvent.class);
            log.info("Received bid.placed: bidId={}, auctionId={}, amount={}",
                    event.bidId(), event.auctionId(), event.amount());

            // Seller: someone bid on your auction.
            saveAndPush(new Notification(
                    event.sellerId(),
                    NotificationType.BID_RECEIVED_ON_YOUR_AUCTION,
                    String.format("New bid of %s on your auction", event.amount()),
                    event.auctionId()));

            // Bidder: confirmation of their own bid.
            saveAndPush(new Notification(
                    event.bidderId(),
                    NotificationType.BID_PLACED,
                    String.format("Your bid of %s was placed", event.amount()),
                    event.auctionId()));

            // Previous leader: you have been outbid.
            if (event.previousBidderId() != null) {
                saveAndPush(new Notification(
                        event.previousBidderId(),
                        NotificationType.OUTBID,
                        String.format("You were outbid. The new highest bid is %s", event.amount()),
                        event.auctionId()));
            }

            log.info("Persisted bid-placed notifications (seller {}, bidder {}, outbid {})",
                    event.sellerId(), event.bidderId(), event.previousBidderId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to deserialize bid.placed event payload; skipping message: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to process bid.placed event: {}", e.getMessage(), e);
            throw e;
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
            saveAndPush(sellerNotification);

            log.info("Persisted auction-ended notification for seller {}", event.sellerId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to deserialize auction.ended event payload; skipping message: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to process auction.ended event: {}", e.getMessage(), e);
            throw e;
        }
    }
}
