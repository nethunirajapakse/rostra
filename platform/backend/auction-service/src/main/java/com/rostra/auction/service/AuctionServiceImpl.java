package com.rostra.auction.service;

import com.rostra.auction.dto.CreateAuctionRequestDTO;
import com.rostra.auction.dto.UpdateAuctionRequestDTO;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionStatus;
import com.rostra.auction.exception.AuctionNotFoundException;
import com.rostra.auction.exception.ForbiddenException;
import com.rostra.auction.exception.IllegalAuctionStateException;
import com.rostra.auction.repository.AuctionRepository;
import com.rostra.auction.repository.AuctionSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class AuctionServiceImpl implements AuctionService{

    private final AuctionRepository auctionRepository;

    public AuctionServiceImpl(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    private static final Set<AuctionStatus> EDITABLE_STATUSES =
            Set.of(AuctionStatus.DRAFT, AuctionStatus.SCHEDULED);

    private static final Set<AuctionStatus> CANCELLABLE_STATUSES =
            Set.of(AuctionStatus.DRAFT, AuctionStatus.SCHEDULED);

    @Transactional
    public Auction create(UUID sellerId, CreateAuctionRequestDTO req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }

        Auction auction = new Auction(
                sellerId,
                req.title(),
                req.description(),
                req.startingPrice(),
                req.minIncrement(),
                req.startsAt(),
                req.endsAt()
        );

        if (!req.startsAt().isAfter(Instant.now())) {
            auction.setStatus(AuctionStatus.ACTIVE);
        }

        return auctionRepository.save(auction);
    }

    @Override
    @Transactional(readOnly = true)
    public Auction findById(UUID id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Auction> search(AuctionSearchCriteria criteria, Pageable pageable) {
        return auctionRepository.search(criteria, pageable);
    }

    @Override
    @Transactional
    public Auction update(UUID userId, UUID auctionId, UpdateAuctionRequestDTO req) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (!auction.getSellerId().equals(userId)) {
            throw new ForbiddenException("Only the seller can update this auction");
        }

        if (!EDITABLE_STATUSES.contains(auction.getStatus())) {
            throw new IllegalAuctionStateException(
                    "Cannot update auction in status: " + auction.getStatus()
            );
        }

        if (req.title() != null)          auction.setTitle(req.title());
        if (req.description() != null)    auction.setDescription(req.description());
        if (req.startingPrice() != null)  auction.setStartingPrice(req.startingPrice());
        if (req.minIncrement() != null)   auction.setMinIncrement(req.minIncrement());
        if (req.startsAt() != null)       auction.setStartsAt(req.startsAt());
        if (req.endsAt() != null)         auction.setEndsAt(req.endsAt());

        // Cross-field check after applying updates
        if (!auction.getEndsAt().isAfter(auction.getStartsAt())) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }

        // If price changed and no bids exist yet, sync current_price
        if (req.startingPrice() != null && auction.getStatus() == AuctionStatus.SCHEDULED) {
            auction.setCurrentPrice(req.startingPrice());
        }

        return auction;  // dirty checking + @Transactional handles the UPDATE
    }

    @Override
    @Transactional
    public Auction cancel(UUID userId, UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (!auction.getSellerId().equals(userId)) {
            throw new ForbiddenException("Only the seller can cancel this auction");
        }

        if (!CANCELLABLE_STATUSES.contains(auction.getStatus())) {
            throw new IllegalAuctionStateException(
                    "Cannot cancel auction in status: " + auction.getStatus()
            );
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        return auction;
    }

    @Override
    @Transactional
    public Auction updateCurrentPrice(UUID auctionId, BigDecimal newPrice, Long expectedVersion) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        // Manual version check — gives us a controllable response
        if (!auction.getVersion().equals(expectedVersion)) {
            throw new IllegalAuctionStateException(
                    String.format("Auction version mismatch (expected %d, found %d). " +
                                    "Another bid may have been placed concurrently.",
                            expectedVersion, auction.getVersion())
            );
        }

        if (!AuctionStatus.ACTIVE.equals(auction.getStatus())) {
            throw new IllegalAuctionStateException(
                    "Cannot update price of auction in status: " + auction.getStatus()
            );
        }

        auction.setCurrentPrice(newPrice);
        // dirty checking handles the UPDATE; @Version annotation increments version automatically
        return auction;
    }
}
