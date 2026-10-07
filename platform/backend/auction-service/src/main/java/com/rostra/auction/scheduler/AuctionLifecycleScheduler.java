package com.rostra.auction.scheduler;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;
import com.rostra.auction.event.AuctionEndedEvent;
import com.rostra.auction.entity.OutboxEvent;
import com.rostra.auction.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import com.rostra.auction.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class AuctionLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionLifecycleScheduler.class);

    private final AuctionRepository auctionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String auctionEndedTopic;

    public AuctionLifecycleScheduler(
            AuctionRepository auctionRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.auction-ended}") String auctionEndedTopic
    ) {
        this.auctionRepository = auctionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.auctionEndedTopic = auctionEndedTopic;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    @Transactional
    public void activateScheduledAuctions() {
        Instant now = Instant.now();
        List<Auction> toActivate = auctionRepository
                .findByStatusAndStartsAtLessThanEqual(AuctionStatus.SCHEDULED, now);

        if (toActivate.isEmpty()) return;

        for (Auction auction : toActivate) {
            auction.setStatus(AuctionStatus.ACTIVE);
            log.info("Activated auction {} ({})", auction.getId(), auction.getTitle());
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    @Transactional
    public void endActiveAuctions() {
        Instant now = Instant.now();
        List<Auction> toEnd = auctionRepository
                .findByStatusAndEndsAtLessThanEqual(AuctionStatus.ACTIVE, now);

        if (toEnd.isEmpty()) return;

        for (Auction auction : toEnd) {
            auction.setStatus(AuctionStatus.ENDED);
            if (auction.getWinnerId() != null) {
                auction.setFinalPrice(auction.getCurrentPrice());
            }
            log.info("Ended auction {} ({})", auction.getId(), auction.getTitle());

            AuctionEndedEvent event = new AuctionEndedEvent(
                    auction.getId(),
                    auction.getSellerId(),
                    auction.getWinnerId(),
                    auction.getFinalPrice(),
                    now
            );
            // Same transaction as the ENDED status: either both are saved or neither is. The OutboxPoller
            // publishes it to Kafka afterwards, retrying while Kafka is down.
            outboxRepository.save(new OutboxEvent(auction.getId(), auctionEndedTopic, serialize(event)));
        }
    }

    private String serialize(AuctionEndedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AuctionEndedEvent", e);
        }
    }
}
