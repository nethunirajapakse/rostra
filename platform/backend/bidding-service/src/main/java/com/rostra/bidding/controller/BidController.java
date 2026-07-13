package com.rostra.bidding.controller;

import com.rostra.bidding.dto.BidResponse;
import com.rostra.bidding.dto.PlaceBidRequestDTO;
import com.rostra.bidding.entity.Bid;
import com.rostra.bidding.service.BidService;
import jakarta.validation.Valid;
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

    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @AuthenticationPrincipal UUID bidderId,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PlaceBidRequestDTO request
    ) {
        String token = authHeader.substring(7);
        Bid bid = bidService.placeBid(bidderId, request, token);
        BidResponse body = BidResponse.from(bid);
        return ResponseEntity.created(URI.create("/bids/" + bid.getId())).body(body);
    }
}
