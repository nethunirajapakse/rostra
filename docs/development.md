# Development

## Running from an IDE

Start only the infrastructure in Docker, then run the services yourself:

```bash
docker compose up -d --remove-orphans postgres redis kafka zookeeper rustfs
export JWT_SECRET="$(openssl rand -base64 48)"        # same value for the gateway and every service (see .env.example)
cd platform/backend/<name> && mvn spring-boot:run     # gateway, auth, auction, bidding, notification
cd platform/frontend && npm install && npm run dev    # http://localhost:5173
```

In IntelliJ, set `JWT_SECRET` in each run configuration. The Vite dev server proxies API and WebSocket paths to the gateway, so the browser sees a single origin.

## The full stack in Docker

```bash
docker compose --profile app up -d --build   # open http://localhost:3000
docker compose --profile app down -v         # stop and delete the data
```

Optional Kafka UI: `docker compose --profile tools up -d kafka-ui` (http://localhost:8090). The RustFS console is at http://localhost:9001/rustfs/console/ (access key `rostra-dev-key`, secret `rostra-dev-secret-0123`).

## Reset and seed

```bash
# 1. Stop the five services
bash scripts/db-reset.sh        # drops the four databases and clears Rostra's Redis keys
# 2. Start the services again (Hibernate recreates the tables)
bash scripts/seed.sh            # demo@rostra.dev / Rostra@Demo1
```

What the seed creates is described in [scripts/seed/README.md](../scripts/seed/README.md).

## Tests

```bash
# One service at a time. Integration tests use Testcontainers, so Docker must be running.
cd platform/backend/<service> && mvn verify

# End-to-end checks against the running stack
bash scripts/smoke-flow.sh
bash scripts/demo-kafka-outage.sh
```

GitHub Actions runs `mvn verify` for every service, the frontend lint and build, and a Docker job that builds every image, on each push and pull request.
