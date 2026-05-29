# rostra

> Real-time auction platform built on event-driven microservices.

A demonstration of distributed systems patterns: Kafka for durable event streaming, WebSockets for real-time client updates, and per-service Postgres databases. Built to explore how Spring Boot microservices communicate reliably under failure conditions.

## Architecture

Four Spring Boot services communicating via Apache Kafka, each owning its own Postgres database.

```
[ React Frontend ] ── HTTPS / WSS ──┐
                                    │
                              [ API Gateway ]
                                    │
        ┌─────────────┬─────────────┼─────────────┐
        ▼             ▼             ▼             ▼
   [ Auth ]      [ Auction ]   [ Bidding ]   [ Notification ]
        │             │             │             ▲
        ▼             ▼             ▼             │
   [ Postgres ]  [ Postgres ]  [ Postgres ]       │
                                    │             │
                                    └─► [ Kafka ] ┘
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3, Spring Security (JWT)
- **Messaging:** Apache Kafka
- **Persistence:** PostgreSQL 16 (one DB per service)
- **Real-time:** WebSockets (Spring + STOMP)
- **Frontend:** React + Vite
- **Infrastructure:** Docker Compose

## Running locally

```bash
docker-compose up -d
```

That spins up Postgres, Kafka, Zookeeper, and a Kafka UI (at http://localhost:8090).

Each Spring service is then started independently from `services/<name>/`.

## Status
Auth and Auction services are complete. 
