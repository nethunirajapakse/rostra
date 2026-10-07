package com.rostra.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Some endpoints exist only for service-to-service calls. They are reachable on the service port but must
 * never be reachable through the public gateway, so they answer 404 here.
 *
 * Today that is the auction price update: the bidding-service calls it directly after validating a bid.
 */
@Component
public class InternalRouteGuardFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Decoded path, any method: matching on the raw path would miss encoded variants such as current%2Dprice.
        String path = exchange.getRequest().getURI().getPath().toLowerCase(Locale.ROOT);
        if (path.contains("current-price")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -30;
    }
}
