# Concurrent bid placement: how it works and what the test proves

`ConcurrentBidIntegrationTest` fires 50 `POST /bids` requests at once, from 50 different signed-in users, all
bidding `11.00` on an auction that stands at `10.00` with a minimum increment of `1.00`. It asserts that
exactly one bid wins and the other 49 are refused, with nothing left behind.

## The concurrency model

Bidding-service does not own the auction's price. auction-service does, and it guards the price with
**optimistic locking** on a version number. A bid is placed like this (`BidServiceImpl.placeBid`):

1. **Read** the auction from auction-service: current price, minimum increment, `version`.
2. **Validate** locally: the bidder is not the seller (`403`), the auction is `ACTIVE` (`409` if not), and
   `amount >= currentPrice + minIncrement` (`400` if the bid is too low).
3. **Write** the new price with `PATCH /auctions/{id}/current-price`, sending `expectedVersion` (the version
   from step 1). auction-service applies it only if that is still the current version, and bumps the version.
   If someone else got in first it answers `409`.
4. **Persist** the bid and its outbox row in one database transaction. This happens only after step 3
   succeeded, so a refused bid leaves no rows.

A `409` in step 3 is turned into an `ObjectOptimisticLockingFailureException`, and `placeBid` is
`@Retryable` for exactly that exception: up to **3 attempts**, with a 50 ms backoff that doubles. Every retry
starts again from step 1, so it sees the winner's price.

## What happens with 50 identical bids

1. All 50 read version `0` and all pass validation (`11.00 >= 10.00 + 1.00`).
2. All 50 send `PATCH ... expectedVersion=0`. auction-service serialises them: the first one wins and moves
   the auction to version `1`. The other 49 get `409`.
3. Each loser retries. The re-read now shows price `11.00`, so the minimum is `12.00`, and `11.00` fails
   validation in step 2: **`400`, "Bid amount 11.00 is below minimum required 12.00"**.

So the expected outcome is **1 × `201` and 49 × `400`**. A loser is never retried a second time, because after
the retry its bid is simply too low. The `@Recover` method (`400`, "auction state changed too rapidly") only
fires if three attempts in a row hit a conflict, which needs a stream of *higher* competing bids, not 50
equal ones. The test accepts `400` or `409` for the 49, as the original issue specified.

## How the test makes it deterministic

* **Real HTTP, real security.** The requests go to the running service on a random port with a signed
  `access_token` cookie, through `JwtAuthFilter` and `SecurityConfig`.
* **A fake auction-service** (`FakeAuctionService`, a small JDK HTTP server) implements the two calls and the
  same version rule as the real one. It holds back the first 50 reads until all 50 have arrived, and answers
  each with the state as it was on arrival. Every bidder therefore reads version `0`, however fast or slow the
  machine is. The test asserts that this happened (`allReadersArrived`) before it trusts the other numbers.
* **A connection pool of 60.** Each in-flight bid keeps a database connection for its transaction; with the
  default pool of 10 the 50 requests would queue instead of racing.
* **No Kafka.** `OutboxPoller` is mocked, so nothing is published and the outbox row stays as written.

## What is asserted

| Check | Expected |
|---|---|
| responses with `201` | exactly 1 |
| the other responses | 49, each `400` or `409` |
| accepted price changes at auction-service | 1, so the version went `0 -> 1` once |
| `PATCH` attempts / conflicts | 50 / 49: every bidder raced, 49 lost and retried |
| rows in `bids` for the auction | 1 (`11.00`, `ACCEPTED`) |
| rows in `outbox_events` for that bid | 1 (topic `bid.placed`) |

## Running it

```bash
cd platform/backend/bidding-service
mvn test -Dtest=ConcurrentBidIntegrationTest
```

Needs Docker (the database is a Postgres 16 container started by Testcontainers). It takes well under a minute,
most of it container and Spring start-up.
