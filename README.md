# rostra

> Real-time auction platform built on event-driven microservices.

A demonstration of distributed systems patterns: Kafka for durable event streaming, WebSockets for real-time client updates, and per-service Postgres databases. Built to explore how Spring Boot microservices communicate reliably under failure conditions.

## Architecture

Four Spring Boot services behind a gateway, communicating via Apache Kafka, each owning its own Postgres database.

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

## Authentication

Cookie sessions, enforced at the gateway.

- `POST /auth/signup`, `/auth/signin`, `/auth/refresh`, `/auth/signout`, `GET /auth/me`.
- Signin sets two `HttpOnly`, `SameSite=Strict` cookies: a 15-minute access JWT (`access_token`) and a 7-day
  refresh JWT (`refresh_token`, only sent to `/auth/*`). Both carry a `jti`.
- Refresh rotates the pair: the old refresh `jti` is deleted from Redis, so replaying it fails.
- Signout puts the access `jti` on a Redis denylist (TTL = remaining lifetime) and deletes the refresh `jti`.
- Gateway: drops any `Authorization` header, forwards the `access_token` cookie only if it has a valid signature,
  is unexpired, is an ACCESS token and is not denylisted, and enforces CSRF (double-submit `XSRF-TOKEN` cookie
  and `X-XSRF-TOKEN` header) on every POST/PUT/PATCH/DELETE except signup and signin.
- Services re-verify signature, expiry and token type locally. They never call the auth-service.
- Redis calls sit behind a circuit breaker and fail open, so a Redis outage does not log everyone out.
- `PATCH /auctions/{id}/current-price` is internal (bidding to auction) and answers 404 through the gateway.

Browser clients: send `credentials: 'include'` and copy the `XSRF-TOKEN` cookie into an `X-XSRF-TOKEN` header
on writes.

## Tech stack

- **Backend:** Java 21, Spring Boot 3, Spring Security (JWT)
- **Messaging:** Apache Kafka
- **Persistence:** PostgreSQL 16 (one DB per service), Redis (token revocation)
- **Real-time:** WebSockets (Spring + STOMP)
- **Frontend:** React + Vite
- **Infrastructure:** Docker Compose

## Running locally

```bash
docker-compose up -d
```

That spins up Postgres, Redis, Kafka, Zookeeper, and a Kafka UI (at http://localhost:8090).

Set `JWT_SECRET` first (see `.env.example`); the gateway and every service refuse to start without it.
Then start each Spring service from `platform/backend/<name>/` (`mvn spring-boot:run`).

## Status
Auth (cookie sessions, Redis revocation), Auction, Bidding and Notification services are in place; the WebSocket layer and the React UI are not built yet.
