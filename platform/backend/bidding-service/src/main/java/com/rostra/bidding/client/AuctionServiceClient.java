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

import java.math.BigDecimal;
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

    private AuctionView fetchAuctionFallback(UUID auctionId, Throwable ex) {
        if (ex instanceof AuctionNotFoundException) {
            throw (AuctionNotFoundException) ex;
        }
        log.error("Circuit breaker fallback triggered for auction {}: {}", auctionId, ex.getMessage());
        throw new AuctionServiceUnavailableException(
                "Auction validation service temporarily unavailable"
        );
    }

    public AuctionView updateCurrentPrice(UUID auctionId, BigDecimal newPrice, Long expectedVersion, String bearerToken) {
        String body = String.format(
                "{\"newPrice\":%s,\"expectedVersion\":%d}",
                newPrice.toPlainString(), expectedVersion
        );
        try {
            return restClient.patch()
                    .uri("/auctions/{id}/current-price", auctionId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(AuctionView.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                // Version mismatch — concurrent bid won the race
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        "Auction current_price update conflicted with concurrent bid", e
                );
            }
            if (e.getStatusCode().value() == 404) {
                throw new AuctionNotFoundException(auctionId);
            }
            throw e;
        }
    }
}
