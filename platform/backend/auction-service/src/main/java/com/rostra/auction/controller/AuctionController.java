package com.rostra.auction.controller;

import com.rostra.auction.dto.AuctionResponseDTO;
import com.rostra.auction.dto.CreateAuctionRequestDTO;
import com.rostra.auction.dto.UpdateAuctionRequestDTO;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.repository.AuctionSearchCriteria;
import com.rostra.auction.service.AuctionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    public ResponseEntity<AuctionResponseDTO> create(
            @AuthenticationPrincipal UUID sellerId,
            @Valid @RequestBody CreateAuctionRequestDTO req
    ) {
        Auction created = auctionService.create(sellerId, req);
        AuctionResponseDTO body = AuctionResponseDTO.from(created);
        return ResponseEntity
                .created(URI.create("/auctions/" + created.getId()))
                .body(body);
    }

    @GetMapping("/{id}")
    public AuctionResponseDTO getById(@PathVariable UUID id) {
        Auction auction = auctionService.findById(id);
        return AuctionResponseDTO.from(auction);
    }

    @GetMapping
    public Page<AuctionResponseDTO> list(
            AuctionSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return auctionService.search(criteria, pageable).map(AuctionResponseDTO::from);
    }

    @PatchMapping("/{id}")
    public AuctionResponseDTO update(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateAuctionRequestDTO req
    ) {
        Auction updated = auctionService.update(userId, id, req);
        return AuctionResponseDTO.from(updated);
    }

    @DeleteMapping("/{id}")
    public AuctionResponseDTO cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId
    ) {
        Auction cancelled = auctionService.cancel(userId, id);
        return AuctionResponseDTO.from(cancelled);
    }
}
