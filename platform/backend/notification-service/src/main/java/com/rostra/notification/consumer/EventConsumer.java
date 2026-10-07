package com.rostra.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.notification.dto.NotificationResponse;
import com.rostra.notification.entity.Notification;
import com.rostra.notification.entity.NotificationType;
import com.rostra.notification.event.AuctionEndedEvent;
import com.rostra.notification.event.BidPlacedEvent;
import com.rostra.notification.repository.NotificationRepository;
import com.rostra.notification.ws.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

            // Kafka delivers at least once; a redelivered bid must not notify anyone twice.
            if (notificationRepository.existsBySourceEventIdAndType(event.bidId(), NotificationType.BID_PLACED)) {
                log.info("bid.placed {} already processed; skipping", event.bidId());
                return;
            }

            // Seller: someone bid on your auction.
            saveAndPush(new Notification(
                    event.sellerId(),
                    NotificationType.BID_RECEIVED_ON_YOUR_AUCTION,
                    String.format("New bid of %s on your auction", event.amount()),
                    event.auctionId(), event.bidId()));

            // Bidder: confirmation of their own bid.
            saveAndPush(new Notification(
                    event.bidderId(),
                    NotificationType.BID_PLACED,
                    String.format("Your bid of %s was placed", event.amount()),
                    event.auctionId(), event.bidId()));

            // Previous leader: you have been outbid.
            if (event.previousBidderId() != null) {
                saveAndPush(new Notification(
                        event.previousBidderId(),
                        NotificationType.OUTBID,
                        String.format("You were outbid. The new highest bid is %s", event.amount()),
                        event.auctionId(), event.bidId()));
            }

            log.info("Persisted bid-placed notifications (seller {}, bidder {}, outbid {})",
                    event.sellerId(), event.bidderId(), event.previousBidderId());
        } catch (JsonProcessingException e) {
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
            log.info("Received auction.ended: auctionId={}, sellerId={}, winnerId={}, finalPrice={}",
                    event.auctionId(), event.sellerId(), event.winnerId(), event.finalPrice());

            if (notificationRepository.existsBySourceEventIdAndType(
                    event.auctionId(), NotificationType.AUCTION_ENDED_AS_SELLER)) {
                log.info("auction.ended {} already processed; skipping", event.auctionId());
                return;
            }

            boolean sold = event.winnerId() != null;

            saveAndPush(new Notification(
                    event.sellerId(),
                    NotificationType.AUCTION_ENDED_AS_SELLER,
                    sold ? String.format("Your auction ended and sold for %s", event.finalPrice())
                         : "Your auction ended with no bids",
                    event.auctionId(), event.auctionId()));

            if (sold) {
                saveAndPush(new Notification(
                        event.winnerId(),
                        NotificationType.AUCTION_WON,
                        String.format("You won the auction with a bid of %s", event.finalPrice()),
                        event.auctionId(), event.auctionId()));

                // Everyone else who bid learns they did not win. Bidders are known from the bid
                // confirmations this service already stored, so no call back to bidding-service is needed.
                List<UUID> bidders = notificationRepository
                        .findUserIdsByAuctionIdAndType(event.auctionId(), NotificationType.BID_PLACED);
                for (UUID bidder : bidders) {
                    if (bidder.equals(event.winnerId())) continue;
                    saveAndPush(new Notification(
                            bidder,
                            NotificationType.AUCTION_ENDED_AS_BIDDER,
                            String.format("The auction ended and you did not win. Winning bid: %s", event.finalPrice()),
                            event.auctionId(), event.auctionId()));
                }
            }

            log.info("Persisted auction-ended notifications for auction {}", event.auctionId());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize auction.ended event payload; skipping message: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to process auction.ended event: {}", e.getMessage(), e);
            throw e;
        }
    }
}
