# Rostra

[![CI](https://github.com/nethunirajapakse/rostra/actions/workflows/ci.yml/badge.svg)](https://github.com/nethunirajapakse/rostra/actions/workflows/ci.yml)

A real-time auction platform built as event-driven microservices. Sellers list items with photos, bidders compete, and prices and notifications update live.

I built it to practise reliability patterns: not losing events when Kafka is down, not processing them twice, and not selling an item to two people.

<img width="1363" height="643" alt="Screenshot 2026-10-25 162119" src="https://github.com/user-attachments/assets/d9f49b79-7743-453a-9a9e-04093b7c8b14" />


**Stack:** Java 21, Spring Boot 3, Spring Cloud Gateway, Kafka, PostgreSQL, Redis, WebSocket, React 19, TypeScript, Tailwind 4

## Try it

You need Docker with Compose and about 4 GB of free memory.

```bash
docker compose --profile app up -d --build   # first build takes a few minutes
bash scripts/seed.sh                         # demo users, auctions, photos and bids
```

Open **http://localhost:3000** and sign in as `demo@rostra.dev` / `Rostra@Demo1`.

Running the services from an IDE instead? See [Development](docs/development.md).

## What it does

- Live bidding: the price updates on every open page, and people get notified when they are outbid or an auction ends.
- Photo uploads: up to 5 per auction, with a cover photo.
- Watchlist, search, sorting and status filters.

## How it fits together

```mermaid
flowchart LR
    Browser["React app"] --> GW["Gateway<br/>JWT and CSRF"]
    GW --> AUTH["auth"]
    GW --> AUC["auction"]
    GW --> BID["bidding"]
    GW --> NOT["notification"]
    BID -- "bid.placed" --> K{{"Kafka"}}
    AUC -- "auction.ended" --> K
    K --> NOT
```

Five Spring Boot services, each with its own database. They call each other over HTTP for commands and use Kafka for events.

## Things worth a look

| Problem | Solution |
|---|---|
| Kafka is down when a bid is placed | Transactional outbox: the event is saved with the bid, then published later |
| Kafka delivers an event twice | Consumers dedupe on the event id |
| Two bids at the same moment | Optimistic locking; a test fires 50 at once and exactly one wins |
| A downstream service is slow | Circuit breaker and retry |

Details: [Reliability](docs/reliability.md), [Authentication](docs/authentication.md), [Photo uploads](docs/photo-uploads.md).

## Documentation

- [Architecture](docs/architecture.md): services, data and events
- [Reliability](docs/reliability.md): the failure cases and how each is handled
- [Authentication](docs/authentication.md): cookies, CSRF and token handling
- [Photo uploads](docs/photo-uploads.md): storage and validation
- [Development](docs/development.md): running from an IDE, reset and seed, tests

## Known limitations

- The schema comes from Hibernate `ddl-auto=update`; Flyway would be the proper fix.
- One shared secret signs all tokens; production would use asymmetric keys.
- WebSocket sessions are in memory, so notification-service is single-instance.
- No payments: winning produces a notification, not a checkout.
