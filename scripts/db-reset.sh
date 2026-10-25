#!/usr/bin/env bash
# Drops and recreates Rostra's four databases and removes Rostra's keys from Redis. ALL DATA IS LOST.
#
#   1. Stop the application:  docker compose stop frontend gateway auth-service auction-service bidding-service notification-service
#      (or stop the five services in your IDE)
#   2. ./scripts/db-reset.sh          (asks for confirmation; add --yes to skip it)
#   3. Start them again (docker compose up -d). Hibernate creates the empty tables on start-up.
#   4. ./scripts/seed.sh
#
# Overrides:  PG_CONTAINER=name  PG_USER=name  REDIS_CONTAINER=name  FORCE=1 (skip the "services still running" check)
set -uo pipefail

# Git Bash on Windows would otherwise rewrite paths such as /bin/sh inside the docker exec arguments.
export MSYS_NO_PATHCONV=1

PG_CONTAINER="${PG_CONTAINER:-rostra-postgres}"
PG_USER="${PG_USER:-rostra}"
REDIS_CONTAINER="${REDIS_CONTAINER:-}"
DATABASES=(rostra_auth rostra_auction rostra_bidding rostra_notification)

abort() { echo "ABORT $*"; exit 1; }

command -v docker >/dev/null 2>&1 || abort "docker not found on PATH"
docker inspect "$PG_CONTAINER" >/dev/null 2>&1 || abort "container '$PG_CONTAINER' not found (set PG_CONTAINER=name)"
[ "$(docker inspect -f '{{.State.Running}}' "$PG_CONTAINER")" = true ] \
  || abort "container '$PG_CONTAINER' is not running (docker start $PG_CONTAINER)"

# Dropping a database under a running service leaves that service broken until it is restarted.
listening=()
for port in 8080 8081 8082 8083 8084; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then listening+=("$port"); fi
done
if [ "${#listening[@]}" -gt 0 ] && [ -z "${FORCE:-}" ]; then
  abort "something is still listening on port(s) ${listening[*]}. Stop the services first (or run with FORCE=1)."
fi

if [ "${1:-}" != "--yes" ]; then
  echo "This DELETES ALL DATA in: ${DATABASES[*]}  (container: $PG_CONTAINER)"
  read -r -p "Type 'reset' to continue: " answer
  [ "$answer" = reset ] || abort "cancelled, nothing was changed"
fi

for db in "${DATABASES[@]}"; do
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" \
    -c "CREATE DATABASE $db" >/dev/null || abort "could not reset $db"
  echo "ok    $db recreated (empty)"
done

# Only Rostra's keys, in case the Redis container is shared with something else.
if [ -z "$REDIS_CONTAINER" ]; then
  REDIS_CONTAINER="$(docker ps --filter publish=6379 --format '{{.Names}}' | head -n1)"
fi
if [ -n "$REDIS_CONTAINER" ]; then
  if docker exec "$REDIS_CONTAINER" sh -c "redis-cli --scan --pattern 'denylist:*' | xargs -r redis-cli del >/dev/null; redis-cli --scan --pattern 'refresh:*' | xargs -r redis-cli del >/dev/null"; then
    echo "ok    removed Rostra's denylist and refresh-token keys from Redis ($REDIS_CONTAINER)"
  else
    echo "WARN  could not clean Redis ($REDIS_CONTAINER). Harmless: old tokens just no longer match a user."
  fi
else
  echo "WARN  no Redis container found; skipped (set REDIS_CONTAINER=name)"
fi

cat <<'MSG'

Done. Kafka topics were left alone.
Next:
  1. start the five services again (this creates the empty tables)
  2. clear localhost cookies in your browser, or sign out, so you are not signed in as a deleted user
  3. ./scripts/seed.sh
MSG
