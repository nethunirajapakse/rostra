package com.rostra.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayCsrfFilterTest {

    private final GatewayCsrfFilter filter = new GatewayCsrfFilter(
            new EdgeSecurityProperties(new EdgeSecurityProperties.Jwt("unused"), new EdgeSecurityProperties.Cookie(false)));

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder);
    }

    private boolean runs(MockServerWebExchange exchange) {
        AtomicBoolean reached = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            reached.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return reached.get();
    }

    @Test
    void postWithoutToken_isRejectedWith403() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.post("/bids"));
        assertFalse(runs(ex));
        assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
    }

    @Test
    void postWithMatchingCookieAndHeader_passes() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.post("/bids")
                .cookie(new HttpCookie("XSRF-TOKEN", "abc123"))
                .header("X-XSRF-TOKEN", "abc123"));
        assertTrue(runs(ex));
    }

    @Test
    void postWithMismatchedHeader_isRejected() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.post("/bids")
                .cookie(new HttpCookie("XSRF-TOKEN", "abc123"))
                .header("X-XSRF-TOKEN", "different"));
        assertFalse(runs(ex));
        assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
    }

    @Test
    void headerWithoutCookie_isRejected() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.delete("/auctions/1")
                .header("X-XSRF-TOKEN", "abc123"));
        assertFalse(runs(ex));
        assertEquals(HttpStatus.FORBIDDEN, ex.getResponse().getStatusCode());
    }

    @Test
    void signinAndSignup_areExempt() {
        assertTrue(runs(exchange(MockServerHttpRequest.post("/auth/signin"))));
        assertTrue(runs(exchange(MockServerHttpRequest.post("/auth/signup"))));
    }

    @Test
    void refreshAndSignout_requireToken() {
        assertFalse(runs(exchange(MockServerHttpRequest.post("/auth/refresh"))));
        assertFalse(runs(exchange(MockServerHttpRequest.post("/auth/signout"))));
    }

    @Test
    void getRequest_isNeverChecked_andGetsATokenCookie() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/auctions"));
        assertTrue(runs(ex));
        ex.getResponse().setComplete().block();
        assertNotNull(ex.getResponse().getCookies().getFirst("XSRF-TOKEN"));
        assertFalse(ex.getResponse().getCookies().getFirst("XSRF-TOKEN").isHttpOnly());
    }

    @Test
    void existingToken_isNotReissued() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/auctions")
                .cookie(new HttpCookie("XSRF-TOKEN", "abc123")));
        assertTrue(runs(ex));
        ex.getResponse().setComplete().block();
        assertNull(ex.getResponse().getCookies().getFirst("XSRF-TOKEN"));
    }
}
