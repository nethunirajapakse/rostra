package com.rostra.auction.service;

import com.rostra.auction.entity.Auction;
import com.rostra.auction.exception.AuctionNotFoundException;
import com.rostra.auction.repository.AuctionRepository;
import com.rostra.auction.repository.WatchlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final AuctionRepository auctionRepository;

    public WatchlistServiceImpl(WatchlistRepository watchlistRepository, AuctionRepository auctionRepository) {
        this.watchlistRepository = watchlistRepository;
        this.auctionRepository = auctionRepository;
    }

    @Override
    @Transactional
    public void watch(UUID userId, UUID auctionId) {
        requireAuction(auctionId);
        watchlistRepository.addIfAbsent(userId, auctionId);
    }

    @Override
    @Transactional
    public void unwatch(UUID userId, UUID auctionId) {
        requireAuction(auctionId);
        watchlistRepository.remove(userId, auctionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Auction> listWatched(UUID userId, Pageable pageable) {
        // The order (newest added first) is part of the contract, so a client-supplied ?sort= is ignored.
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return watchlistRepository.findWatchedAuctions(userId, newestFirst);
    }

    private void requireAuction(UUID auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }
    }
}
