package com.rostra.bidding.controller;

import com.rostra.bidding.dto.BidResponse;
import com.rostra.bidding.dto.PlaceBidRequestDTO;
import com.rostra.bidding.entity.Bid;
import com.rostra.bidding.service.BidService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/bids")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    /** Public bid history for one auction, newest first. */
    @GetMapping
    public Page<BidResponse> history(
            @RequestParam UUID auctionId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return bidService.listForAuction(auctionId, pageable).map(BidResponse::from);
    }

    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @AuthenticationPrincipal UUID bidderId,
            @Valid @RequestBody PlaceBidRequestDTO request
    ) {
        Bid bid = bidService.placeBid(bidderId, request);
        BidResponse body = BidResponse.from(bid);
        return ResponseEntity.created(URI.create("/bids/" + bid.getId())).body(body);
    }
}
