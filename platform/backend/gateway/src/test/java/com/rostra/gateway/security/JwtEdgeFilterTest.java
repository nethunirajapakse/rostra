package com.rostra.gateway.security;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JwtEdgeFilterTest {

    private static final String SECRET = "test-only-secret-key-not-for-production-use-32bytes+";

    private ReactiveStringRedisTemplate redis;
    private JwtEdgeFilter filter;

    @BeforeEach
    void setUp() {
        redis = Mockito.mock(ReactiveStringRedisTemplate.class);
        filter = new JwtEdgeFilter(
                new EdgeSecurityProperties(new EdgeSecurityProperties.Jwt(SECRET), new EdgeSecurityProperties.Cookie(false)),
                redis,
                CircuitBreaker.ofDefaults("redis-test"));
    }

    private static String token(String type) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private ServerWebExchange forwarded(MockServerHttpRequest.BaseBuilder<?> builder) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seen.set(ex);
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(builder), chain).block();
        assertNotNull(seen.get(), "request should have been forwarded");
        return seen.get();
    }

    /** What the services actually receive: the forwarded Cookie header (NettyRoutingFilter sends headers). */
    private static String cookieHeader(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE);
        return header == null ? "" : header;
    }

    @Test
    void validAccessToken_isForwarded() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        String jwt = token("ACCESS");

        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/auctions")
                .cookie(new HttpCookie("access_token", jwt)));

        assertTrue(cookieHeader(out).contains("access_token=" + jwt));
    }

    @Test
    void denylistedToken_isDropped_butOtherCookiesSurvive() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(true));

        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/bids")
                .cookie(new HttpCookie("access_token", token("ACCESS")), new HttpCookie("XSRF-TOKEN", "abc")));

        assertFalse(cookieHeader(out).contains("access_token"));
        assertTrue(cookieHeader(out).contains("XSRF-TOKEN=abc"));
    }

    @Test
    void refreshTokenUsedAsAccessToken_isDropped() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));

        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/bids")
                .cookie(new HttpCookie("access_token", token("REFRESH"))));

        assertFalse(cookieHeader(out).contains("access_token"));
    }

    @Test
    void garbageToken_isDropped() {
        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/bids")
                .cookie(new HttpCookie("access_token", "not-a-jwt")));

        assertFalse(cookieHeader(out).contains("access_token"));
    }

    @Test
    void authorizationHeader_isAlwaysStripped() {
        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/bids")
                .header("Authorization", "Bearer " + token("ACCESS")));

        assertNull(out.getRequest().getHeaders().getFirst("Authorization"));
    }

    @Test
    void redisFailure_failsOpen() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        String jwt = token("ACCESS");

        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/auctions")
                .cookie(new HttpCookie("access_token", jwt)));

        assertTrue(cookieHeader(out).contains("access_token=" + jwt));
    }

    private HttpStatus statusWhenFiltered(MockServerHttpRequest.BaseBuilder<?> builder) {
        AtomicReference<Boolean> reachedBackend = new AtomicReference<>(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(builder);
        filter.filter(exchange, ex -> {
            reachedBackend.set(true);
            return Mono.empty();
        }).block();
        assertFalse(reachedBackend.get(), "request must not reach the backend");
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void webSocket_withoutCookie_isRefusedAtTheEdge() {
        assertEquals(HttpStatus.UNAUTHORIZED, statusWhenFiltered(MockServerHttpRequest.get("/ws/notifications")));
    }

    @Test
    void webSocket_withGarbageCookie_isRefusedAtTheEdge() {
        assertEquals(HttpStatus.UNAUTHORIZED, statusWhenFiltered(MockServerHttpRequest.get("/ws/notifications")
                .cookie(new HttpCookie("access_token", "not-a-jwt"))));
    }

    @Test
    void webSocket_withDenylistedToken_isRefusedAtTheEdge() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(true));
        assertEquals(HttpStatus.UNAUTHORIZED, statusWhenFiltered(MockServerHttpRequest.get("/ws/notifications")
                .cookie(new HttpCookie("access_token", token("ACCESS")))));
    }

    @Test
    void webSocket_withValidCookie_isForwarded() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        String jwt = token("ACCESS");

        ServerWebExchange out = forwarded(MockServerHttpRequest.get("/ws/notifications")
                .cookie(new HttpCookie("access_token", jwt)));

        assertTrue(cookieHeader(out).contains("access_token=" + jwt));
    }
}
