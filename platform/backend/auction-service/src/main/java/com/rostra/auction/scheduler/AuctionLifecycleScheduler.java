package com.rostra.auction.scheduler;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;
import com.rostra.auction.event.AuctionEndedEvent;
import com.rostra.auction.event.KafkaEventPublisher;
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
    private final KafkaEventPublisher eventPublisher;

    public AuctionLifecycleScheduler(
            AuctionRepository auctionRepository,
            KafkaEventPublisher eventPublisher
    ) {
        this.auctionRepository = auctionRepository;
        this.eventPublisher = eventPublisher;
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
            log.info("Ended auction {} ({})", auction.getId(), auction.getTitle());

            AuctionEndedEvent event = new AuctionEndedEvent(
                    auction.getId(),
                    auction.getSellerId(),
                    now
            );
            eventPublisher.publishAuctionEnded(event);
        }
    }
}
