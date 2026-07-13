package com.rostra.bidding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.bidding.client.AuctionServiceClient;
import com.rostra.bidding.client.AuctionView;
import com.rostra.bidding.dto.PlaceBidRequestDTO;
import com.rostra.bidding.entity.Bid;
import com.rostra.bidding.entity.BidStatus;
import com.rostra.bidding.entity.OutboxEvent;
import com.rostra.bidding.event.BidPlacedEvent;
import com.rostra.bidding.exception.AuctionNotBiddableException;
import com.rostra.bidding.exception.InvalidBidException;
import com.rostra.bidding.repository.BidRepository;
import com.rostra.bidding.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class BidServiceImpl implements BidService {

    private static final Logger log = LoggerFactory.getLogger(BidServiceImpl.class);

    private final BidRepository bidRepository;
    private final OutboxRepository outboxRepository;
    private final AuctionServiceClient auctionClient;
    private final ObjectMapper objectMapper;
    private final String bidPlacedTopic;

    public BidServiceImpl(
            BidRepository bidRepository,
            OutboxRepository outboxRepository,
            AuctionServiceClient auctionClient,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.bid-placed}") String bidPlacedTopic
    ) {
        this.bidRepository = bidRepository;
        this.outboxRepository = outboxRepository;
        this.auctionClient = auctionClient;
        this.objectMapper = objectMapper;
        this.bidPlacedTopic = bidPlacedTopic;
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public Bid placeBid(UUID bidderId, PlaceBidRequestDTO request, String bearerToken) {
        // 1. Fetch auction state
        AuctionView auction = auctionClient.fetchAuction(request.auctionId());

        // 2. State + amount validation (as before)
        validateAuctionState(auction);
        validateBidAmount(request.amount(), auction);

        // 3. Update auction's current_price — may throw ObjectOptimisticLockingFailureException
        auctionClient.updateCurrentPrice(
                auction.id(),
                request.amount(),
                auction.version(),     // <-- new field on AuctionView
                bearerToken
        );

        // 4. Persist Bid + OutboxEvent (as before)
        Bid bid = new Bid(auction.id(), bidderId, request.amount(), BidStatus.ACCEPTED);
        bid = bidRepository.save(bid);

        BidPlacedEvent event = new BidPlacedEvent(bid.getId(), bid.getAuctionId(),
                bid.getBidderId(), bid.getAmount(),
                bid.getPlacedAt() != null ? bid.getPlacedAt() : Instant.now());
        OutboxEvent outboxRow = new OutboxEvent(bid.getId(), bidPlacedTopic, serializeEvent(event));
        outboxRepository.save(outboxRow);

        log.info("Bid {} placed by user {} on auction {} for amount {}",
                bid.getId(), bidderId, auction.id(), request.amount());
        return bid;
    }

    @Recover
    public Bid recoverFromOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex,
            UUID bidderId,
            PlaceBidRequestDTO request,
            String bearerToken
    ) {
        log.warn("Bid placement failed after retries for auction {}: optimistic lock conflict",
                request.auctionId());
        throw new InvalidBidException(
                "Could not place bid — auction state changed too rapidly. Please try again."
        );
    }

    private void validateAuctionState(AuctionView auction) {
        if (!"ACTIVE".equals(auction.status())) {
            throw new AuctionNotBiddableException(
                    "Auction is not active. Current status: " + auction.status()
            );
        }
        Instant now = Instant.now();
        if (now.isBefore(auction.startsAt())) {
            throw new AuctionNotBiddableException("Auction has not started yet");
        }
        if (now.isAfter(auction.endsAt())) {
            throw new AuctionNotBiddableException("Auction has already ended");
        }
    }

    private void validateBidAmount(BigDecimal amount, AuctionView auction) {
        BigDecimal minRequired = auction.currentPrice().add(auction.minIncrement());
        if (amount.compareTo(minRequired) < 0) {
            throw new InvalidBidException(
                    String.format("Bid amount %s is below minimum required %s (current price %s + increment %s)",
                            amount, minRequired, auction.currentPrice(), auction.minIncrement())
            );
        }
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Should never happen for our simple event records, but signals a real bug if it does
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
