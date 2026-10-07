package com.rostra.gateway.security;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Edge authentication. For every request this filter decides whether the {@code access_token} cookie may be
 * forwarded to the services.
 *
 * A cookie is forwarded only if the token has a valid signature, is not expired, is an ACCESS token (refresh
 * tokens share the signing key) and its jti is not on the Redis denylist (set by signout). Otherwise the cookie
 * is dropped and the request continues as anonymous: public endpoints still work, protected ones answer 401.
 *
 * Any client-supplied Authorization header is removed. Services accept Bearer tokens for internal calls
 * (bidding to auction), and a Bearer token sent from outside would skip the denylist check.
 *
 * If Redis is unreachable the denylist check fails open, matching the auth-service, so an outage does not log
 * everyone out. The signature, expiry and type checks still apply.
 */
@Component
public class JwtEdgeFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtEdgeFilter.class);

    static final String ACCESS_COOKIE = "access_token";
    static final String DENYLIST_KEY_PREFIX = "denylist:";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final Duration REDIS_TIMEOUT = Duration.ofMillis(500);

    private final SecretKey signingKey;
    private final ReactiveStringRedisTemplate redis;
    private final CircuitBreaker redisCircuitBreaker;

    public JwtEdgeFilter(EdgeSecurityProperties properties,
                         ReactiveStringRedisTemplate redis,
                         CircuitBreaker redisCircuitBreaker) {
        Objects.requireNonNull(properties.jwt(), "app.jwt.secret (JWT_SECRET) must be set");
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.redis = redis;
        this.redisCircuitBreaker = redisCircuitBreaker;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_COOKIE);

        if (cookie == null) {
            return forward(exchange, chain, false);
        }

        Claims claims = parseAccessClaims(cookie.getValue());
        if (claims == null) {
            return forward(exchange, chain, true);
        }

        return isDenylisted(claims.getId())
                .flatMap(denied -> forward(exchange, chain, denied));
    }

    private Claims parseAccessClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)) || claims.getId() == null) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Mono<Boolean> isDenylisted(String jti) {
        return redis.hasKey(DENYLIST_KEY_PREFIX + jti)
                .timeout(REDIS_TIMEOUT)
                .transformDeferred(CircuitBreakerOperator.of(redisCircuitBreaker))
                .onErrorResume(e -> {
                    log.warn("Denylist check unavailable ({}); failing open for jti={}", e.toString(), jti);
                    return Mono.just(false);
                });
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, boolean dropAccessCookie) {
        ServerHttpRequest original = exchange.getRequest();
        ServerHttpRequest sanitized = original.mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    if (dropAccessCookie) {
                        List<String> kept = new ArrayList<>();
                        original.getCookies().forEach((name, cookies) -> {
                            if (!ACCESS_COOKIE.equals(name)) {
                                cookies.forEach(c -> kept.add(c.getName() + "=" + c.getValue()));
                            }
                        });
                        headers.remove(HttpHeaders.COOKIE);
                        if (!kept.isEmpty()) {
                            headers.add(HttpHeaders.COOKIE, String.join("; ", kept));
                        }
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
