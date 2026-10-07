package com.rostra.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF protection for every service, enforced once at the edge (double-submit cookie).
 *
 * The browser keeps the auth tokens in cookies, so a hostile site could make the browser send them. To stop
 * that, state-changing requests must also carry the value of the readable XSRF-TOKEN cookie in an
 * X-XSRF-TOKEN header. A hostile site cannot read our cookie, so it cannot set the header.
 *
 * Signup and signin are exempt: there is no session to ride on yet.
 */
@Component
public class GatewayCsrfFilter implements GlobalFilter, Ordered {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private static final Set<HttpMethod> UNSAFE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final Set<String> EXEMPT_PATHS = Set.of("/auth/signup", "/auth/signin");

    private final SecureRandom random = new SecureRandom();
    private final EdgeSecurityProperties properties;

    public GatewayCsrfFilter(EdgeSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpCookie existing = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        boolean hasToken = existing != null && !existing.getValue().isEmpty();

        // Hand out a token on the first response so the client always has one before its first write.
        if (!hasToken) {
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().addCookie(newTokenCookie());
                return Mono.empty();
            });
        }

        if (!requiresCheck(exchange)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (hasToken && header != null && matches(existing.getValue(), header)) {
            return chain.filter(exchange);
        }
        return reject(exchange);
    }

    private boolean requiresCheck(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null || !UNSAFE_METHODS.contains(method)) {
            return false;
        }
        return !EXEMPT_PATHS.contains(exchange.getRequest().getURI().getRawPath());
    }

    private static boolean matches(String cookieValue, String headerValue) {
        return MessageDigest.isEqual(
                cookieValue.getBytes(StandardCharsets.UTF_8),
                headerValue.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseCookie newTokenCookie() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(false) // the frontend must be able to read it and echo it in a header
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path("/")
                .build();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"status\":403,\"error\":\"Forbidden\",\"messages\":[\"Missing or invalid CSRF token\"]}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -20;
    }
}
