package com.rostra.bidding.concurrency;

import com.rostra.bidding.outbox.OutboxPoller;
import com.rostra.bidding.support.PostgresIntegrationTest;
import com.rostra.bidding.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fifty different bidders place the same bid at the same moment. Exactly one may win.
 * See README.md in this package for the design and the retry behaviour this test pins down.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Each in-flight bid holds a database connection for the duration of its transaction, and the test
        // wants all 50 in flight together. The default pool of 10 would serialise them.
        properties = "spring.datasource.hikari.maximum-pool-size=60")
class ConcurrentBidIntegrationTest extends PostgresIntegrationTest {

    private static final int BIDDERS = 50;
    private static final UUID AUCTION_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    // starting price 10.00 and increment 1.00, so the lowest valid bid is 11.00
    private static final FakeAuctionService AUCTION = FakeAuctionService.start(
            AUCTION_ID, SELLER_ID, new BigDecimal("10.00"), new BigDecimal("1.00"), BIDDERS);

    @DynamicPropertySource
    static void auctionServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("app.services.auction.base-url", AUCTION::baseUrl);
    }

    @AfterAll
    static void stopFakeAuctionService() {
        AUCTION.close();
    }

    /** Keeps Kafka out of this test: nothing is published, so the outbox row stays exactly as it was written. */
    @MockitoBean
    private OutboxPoller outboxPoller;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fiftyBiddersRaceForTheSameAmount_exactlyOneWins() throws Exception {
        List<Integer> statuses = placeBidsAtTheSameTime();
        Map<Integer, Long> byStatus = statuses.stream()
                .collect(Collectors.groupingBy(s -> s, TreeMap::new, Collectors.counting()));

        // The race really happened: all 50 requests were in flight together and all saw the same version.
        assertThat(AUCTION.allReadersArrived())
                .as("all %d requests were in flight at once", BIDDERS)
                .isTrue();

        // Exactly one bid succeeds. Everyone else is told no (400 "below minimum after retry", or 409).
        assertThat(byStatus.getOrDefault(201, 0L)).as("status counts: %s", byStatus).isEqualTo(1L);
        List<Integer> rejected = statuses.stream().filter(s -> s != 201).toList();
        assertThat(rejected)
                .as("status counts: %s", byStatus)
                .hasSize(BIDDERS - 1)
                .allMatch(s -> s == 400 || s == 409, "rejected with 400 or 409");

        // The auction moved exactly once: one accepted price change, version 0 -> 1.
        assertThat(AUCTION.acceptedPatches()).isEqualTo(1);
        assertThat(AUCTION.version()).isEqualTo(1L);
        // The retry path ran: every bidder tried once and lost the race, except the winner.
        assertThat(AUCTION.patchAttempts()).isEqualTo(BIDDERS);
        assertThat(AUCTION.conflicts()).isEqualTo(BIDDERS - 1);

        // Bidding-service's own database: one bid, one outbox row, and nothing left over from the 49 losers.
        assertThat(count("select count(*) from bids where auction_id = ?")).isEqualTo(1L);
        assertThat(count("""
                select count(*) from outbox_events o
                join bids b on b.id = o.aggregate_id
                where b.auction_id = ?
                """)).isEqualTo(1L);

        Map<String, Object> bid = jdbc.queryForMap(
                "select amount, status from bids where auction_id = ?", AUCTION_ID);
        assertThat((BigDecimal) bid.get("amount")).isEqualByComparingTo("11.00");
        assertThat(bid.get("status")).isEqualTo("ACCEPTED");
        String topic = jdbc.queryForObject("""
                select o.topic from outbox_events o
                join bids b on b.id = o.aggregate_id
                where b.auction_id = ?
                """, String.class, AUCTION_ID);
        assertThat(topic).isEqualTo("bid.placed");
    }

    private Long count(String sql) {
        return jdbc.queryForObject(sql, Long.class, AUCTION_ID);
    }

    /** Releases 50 threads at once, each posting a bid of 11.00 as a different signed-in user. */
    private List<Integer> placeBidsAtTheSameTime() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        URI uri = URI.create("http://127.0.0.1:" + port + "/bids");
        String body = "{\"auctionId\":\"%s\",\"amount\":11.00}".formatted(AUCTION_ID);

        ExecutorService pool = Executors.newFixedThreadPool(BIDDERS);
        CountDownLatch ready = new CountDownLatch(BIDDERS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < BIDDERS; i++) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .header("Cookie", "access_token=" + TestTokens.accessToken(UUID.randomUUID()))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                responses.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("all bidder threads started").isTrue();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
