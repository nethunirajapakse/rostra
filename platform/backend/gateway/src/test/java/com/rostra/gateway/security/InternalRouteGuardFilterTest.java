package com.rostra.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalRouteGuardFilterTest {

    private final InternalRouteGuardFilter filter = new InternalRouteGuardFilter();

    private MockServerWebExchange call(MockServerHttpRequest.BaseBuilder<?> builder, AtomicBoolean reached) {
        MockServerWebExchange exchange = MockServerWebExchange.from(builder);
        GatewayFilterChain chain = ex -> {
            reached.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void priceUpdate_isHiddenFromTheOutside() {
        AtomicBoolean reached = new AtomicBoolean(false);
        MockServerWebExchange ex = call(MockServerHttpRequest.patch("/auctions/123/current-price"), reached);
        assertFalse(reached.get());
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
    }

    @Test
    void encodedVariant_isAlsoHidden() {
        AtomicBoolean reached = new AtomicBoolean(false);
        MockServerWebExchange ex = call(MockServerHttpRequest.method(HttpMethod.PATCH, URI.create("/auctions/123/current%2Dprice")), reached);
        assertFalse(reached.get());
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
    }

    @Test
    void normalAuctionRoutes_passThrough() {
        AtomicBoolean reached = new AtomicBoolean(false);
        call(MockServerHttpRequest.get("/auctions/123"), reached);
        assertTrue(reached.get());
    }
}
