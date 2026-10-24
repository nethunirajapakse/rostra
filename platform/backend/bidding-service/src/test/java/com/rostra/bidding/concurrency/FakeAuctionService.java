package com.rostra.bidding.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for auction-service with just the two calls bidding-service makes, and the same rule the real
 * one applies in AuctionServiceImpl.updateCurrentPrice: the price moves only if the caller's expectedVersion is
 * still the current version (otherwise 409), and the version goes up by one with every accepted change.
 *
 * To make the race deterministic, the first {@code concurrentReaders} reads are all held back until that many
 * have arrived, and each is answered with the state as it was when it arrived. So every bidder sees the same
 * (soon to be stale) version, however fast or slow the machine is.
 */
final class FakeAuctionService implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final UUID auctionId;
    private final UUID sellerId;
    private final BigDecimal minIncrement;
    private final Instant startsAt = Instant.now().minus(1, ChronoUnit.HOURS);
    private final Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

    private final Object lock = new Object();
    private BigDecimal currentPrice;   // guarded by lock
    private long version;              // guarded by lock

    private final CountDownLatch readersArrived;
    private final AtomicInteger patchAttempts = new AtomicInteger();
    private final AtomicInteger acceptedPatches = new AtomicInteger();
    private final AtomicInteger conflicts = new AtomicInteger();

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpServer server;

    private FakeAuctionService(UUID auctionId, UUID sellerId, BigDecimal startingPrice, BigDecimal minIncrement,
                               int concurrentReaders) throws IOException {
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.currentPrice = startingPrice;
        this.minIncrement = minIncrement;
        this.readersArrived = new CountDownLatch(concurrentReaders);

        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        this.server.createContext("/auctions/", this::handle);
        this.server.setExecutor(executor);
        this.server.start();
    }

    static FakeAuctionService start(UUID auctionId, UUID sellerId, BigDecimal startingPrice,
                                    BigDecimal minIncrement, int concurrentReaders) {
        try {
            return new FakeAuctionService(auctionId, sellerId, startingPrice, minIncrement, concurrentReaders);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** True once every one of the expected concurrent reads had arrived (i.e. the race really happened). */
    boolean allReadersArrived() {
        return readersArrived.getCount() == 0;
    }

    long version() {
        synchronized (lock) {
            return version;
        }
    }

    int patchAttempts() {
        return patchAttempts.get();
    }

    int acceptedPatches() {
        return acceptedPatches.get();
    }

    int conflicts() {
        return conflicts.get();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    // ---- HTTP handling -------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String auctionPath = "/auctions/" + auctionId;
            if ("GET".equals(method) && path.equals(auctionPath)) {
                handleRead(exchange);
            } else if ("PATCH".equals(method) && path.equals(auctionPath + "/current-price")) {
                handlePriceUpdate(exchange);
            } else {
                respond(exchange, 404, "{\"message\":\"not found\"}");
            }
        } catch (Exception e) {
            respond(exchange, 500, "{\"message\":\"" + e.getClass().getSimpleName() + "\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleRead(HttpExchange exchange) throws Exception {
        String snapshot;
        synchronized (lock) {
            snapshot = view(currentPrice, version);
        }
        readersArrived.countDown();
        readersArrived.await(10, TimeUnit.SECONDS);
        respond(exchange, 200, snapshot);
    }

    private void handlePriceUpdate(HttpExchange exchange) throws Exception {
        if (exchange.getRequestHeaders().getFirst("X-Service-Token") == null) {
            respond(exchange, 401, "{\"message\":\"service token required\"}");
            return;
        }
        JsonNode request = JSON.readTree(exchange.getRequestBody());
        BigDecimal newPrice = request.get("newPrice").decimalValue();
        long expectedVersion = request.get("expectedVersion").asLong();
        patchAttempts.incrementAndGet();

        int status;
        String body;
        synchronized (lock) {
            if (expectedVersion != version) {
                conflicts.incrementAndGet();
                status = 409;
                body = "{\"message\":\"Auction version mismatch\"}";
            } else if (newPrice.compareTo(currentPrice.add(minIncrement)) < 0) {
                conflicts.incrementAndGet();
                status = 409;
                body = "{\"message\":\"Bid below minimum required\"}";
            } else {
                currentPrice = newPrice;
                version++;
                acceptedPatches.incrementAndGet();
                status = 200;
                body = view(currentPrice, version);
            }
        }
        respond(exchange, status, body);
    }

    private String view(BigDecimal price, long atVersion) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", auctionId.toString());
        node.put("sellerId", sellerId.toString());
        node.put("currentPrice", price);
        node.put("minIncrement", minIncrement);
        node.put("startsAt", startsAt.toString());
        node.put("endsAt", endsAt.toString());
        node.put("status", "ACTIVE");
        node.put("version", atVersion);
        return node.toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
