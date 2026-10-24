package com.rostra.auction.controller;

import com.rostra.auction.dto.AuctionResponseDTO;
import com.rostra.auction.dto.WatchResponseDTO;
import com.rostra.auction.service.WatchlistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-user watchlist. Every endpoint needs a signed-in user (SecurityConfig: anyRequest().authenticated()),
 * and the user always comes from the token, never from the request, so nobody can read or change another
 * user's list.
 */
@RestController
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @PostMapping("/auctions/{id}/watch")
    public WatchResponseDTO watch(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        watchlistService.watch(userId, id);
        return new WatchResponseDTO(id, true);
    }

    @DeleteMapping("/auctions/{id}/watch")
    public WatchResponseDTO unwatch(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        watchlistService.unwatch(userId, id);
        return new WatchResponseDTO(id, false);
    }

    /** Auctions the caller watches, newest added first. */
    @GetMapping("/me/watchlist")
    public Page<AuctionResponseDTO> list(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return watchlistService.listWatched(userId, pageable).map(AuctionResponseDTO::from);
    }
}
