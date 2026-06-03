package com.rostra.bidding.client;

import com.rostra.bidding.exception.AuctionNotFoundException;
import com.rostra.bidding.exception.AuctionServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AuctionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuctionServiceClient.class);

    private final RestClient restClient;

    public AuctionServiceClient(@Value("${app.services.auction.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "auctionService", fallbackMethod = "fetchAuctionFallback")
    public AuctionView fetchAuction(UUID auctionId) {
        try {
            return restClient.get()
                    .uri("/auctions/{id}", auctionId)
                    .retrieve()
                    .body(AuctionView.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new AuctionNotFoundException(auctionId);
            }
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private AuctionView fetchAuctionFallback(UUID auctionId, Throwable ex) {
        // Don't fall back on a 404 — that's a real "not found", not a service outage.
        if (ex instanceof AuctionNotFoundException) {
            throw (AuctionNotFoundException) ex;
        }
        log.error("Circuit breaker fallback triggered for auction {}: {}", auctionId, ex.getMessage());
        throw new AuctionServiceUnavailableException(
                "Auction validation service temporarily unavailable"
        );
    }
}
