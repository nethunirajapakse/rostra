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
            @CookieValue(name = "access_token", required = false) String cookieToken,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PlaceBidRequestDTO request
    ) {
        // The same token is forwarded to the auction-service for the price update.
        String token = cookieToken != null
                ? cookieToken
                : (authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null);
        Bid bid = bidService.placeBid(bidderId, request, token);
        BidResponse body = BidResponse.from(bid);
        return ResponseEntity.created(URI.create("/bids/" + bid.getId())).body(body);
    }
}
