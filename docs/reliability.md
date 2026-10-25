# Reliability

| Problem | Solution |
|---|---|
| Kafka is down when a bid is placed | **Transactional outbox**: the event is saved with the bid in one transaction, then published by a poller. Also used for `auction.ended`. |
| Kafka delivers an event twice | Consumers **dedupe** on the source event id. |
| Two bids at the same moment | **Optimistic locking** on the auction, with a retry. |
| Auction service is slow or failing | **Resilience4j** circuit breaker and retry. |
| Redis is down | Gateway checks **fail open** behind a circuit breaker. |
| Internal endpoint called from outside | The gateway blocks it, and it only accepts a short-lived `SERVICE` token. |

## Outbox

Publishing to Kafka inside the request would lose the event if Kafka were down, and publishing after the commit could lose it if the service crashed in between. Instead the event is written to an outbox table in the same transaction as the bid. A poller sends pending rows to Kafka and marks them sent. If Kafka is down, rows wait until it returns.

`scripts/demo-kafka-outage.sh` shows this: it stops Kafka, places bids, restarts Kafka, and checks that every event arrived.

## Concurrent bids

Auction-service owns the price and guards it with a version number. A bid is accepted only if the version it read is still current; otherwise it is refused with `409`. A test fires 50 simultaneous bids and asserts that exactly one wins. The full explanation is in [the concurrency README](../platform/backend/bidding-service/src/test/java/com/rostra/bidding/concurrency/README.md).

## Duplicate events

Kafka delivers at least once, so consumers record the source event id and ignore any id they have already processed.
