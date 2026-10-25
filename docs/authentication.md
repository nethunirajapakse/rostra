# Authentication

- Sign-in sets two `HttpOnly`, `SameSite=Strict` cookies: a 15-minute access JWT and a 7-day refresh JWT.
- Refresh rotates the pair. Sign-out adds the access token to a denylist in Redis.
- The gateway validates the cookie, drops any `Authorization` header sent by the client, and enforces double-submit CSRF on writes: the `XSRF-TOKEN` cookie must match the `X-XSRF-TOKEN` header.
- Services re-verify the token locally and never call auth-service.
- Anonymous requests get `401`; forbidden ones get `403`. Unauthenticated WebSocket upgrades get `401`.

## Signing secret

One HMAC secret (`JWT_SECRET`, at least 32 bytes) signs all tokens, and the gateway and every service must share it. `docker-compose.yml` has a throwaway default so the Docker route works with no setup. For development from an IDE, see [Development](development.md). Production would use asymmetric keys.
