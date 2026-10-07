#!/usr/bin/env bash
# Fault-tolerance demo: an auction ends while Kafka is DOWN. Nothing may be lost.
#   1. a seller and a bidder, a short auction, one bid (Kafka up: everything flows)
#   2. docker stop rostra-kafka
#   3. the auction ends: its state is correct in the database, but no notification can be delivered yet
#   4. docker start rostra-kafka -> the outbox poller publishes the waiting event -> winner and seller are told
#
# Needs the same running stack as smoke-flow.sh plus the docker CLI.   Usage: ./scripts/demo-kafka-outage.sh
set -uo pipefail

GW="${GW:-http://localhost:8080}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-rostra-kafka}"
TMP="$(mktemp -d)"
SELLER_JAR="$TMP/seller.txt"; BIDDER_JAR="$TMP/bidder.txt"; ANON_JAR="$TMP/anon.txt"
RUN="$RANDOM$RANDOM"; PASS='Passw0rd!smoke'; fail=0
# Never leave the broker stopped, whatever happens.
trap 'docker start "$KAFKA_CONTAINER" >/dev/null 2>&1; rm -rf "$TMP"' EXIT

check() { if [ "$2" = "$3" ]; then echo "ok    $1 ($3)"; else echo "FAIL  $1 (expected $2, got $3)"; fail=1; fi; }
info() { echo "      $*"; }
abort() { echo "ABORT $*"; exit 1; }
xsrf() { [ -f "$1" ] || return 0; awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tail -n1; }
call() {
  local jar=$1 method=$2 path=$3 data=${4:-}
  local args=(-s -m 15 -c "$jar" -b "$jar" -X "$method" -w $'\n%{http_code}')
  if [ "$method" != GET ]; then args+=(-H "X-XSRF-TOKEN: $(xsrf "$jar")"); fi
  if [ -n "$data" ]; then args+=(-H 'Content-Type: application/json' -d "$data"); fi
  local out; out=$(curl "${args[@]}" "$GW$path"); CODE=${out##*$'\n'}; BODY=${out%$'\n'*}
}
json_str() { printf '%s' "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -n1 | cut -d'"' -f4; }
iso_in() { date -u -d "+$1 seconds" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+"$1"S +%Y-%m-%dT%H:%M:%SZ; }
count_type() { printf '%s' "$1" | grep -oE "\"type\"[[:space:]]*:[[:space:]]*\"$2\"" | wc -l | tr -d ' '; }

docker inspect "$KAFKA_CONTAINER" >/dev/null 2>&1 || abort "docker container '$KAFKA_CONTAINER' not found (set KAFKA_CONTAINER)"
code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$GW/actuator/health"); [ "$code" != 000 ] || abort "gateway not reachable at $GW"

register() {
  local jar=$1 label=$2 email="demo-$2-$RUN@rostra.dev"
  call "$jar" POST /auth/signup "{\"firstName\":\"Demo\",\"lastName\":\"$label\",\"email\":\"$email\",\"password\":\"$PASS\"}"
  call "$jar" POST /auth/signin "{\"email\":\"$email\",\"password\":\"$PASS\"}"
  [ "$CODE" = 200 ] || abort "$label could not sign in ($CODE)"
  call "$jar" GET /auth/me; USER_ID=$(json_str "$BODY" userId)
}
echo "== setup"
register "$SELLER_JAR" seller; SELLER_ID=$USER_ID
register "$BIDDER_JAR" bidder; BIDDER_ID=$USER_ID
call "$SELLER_JAR" POST /auctions "{\"title\":\"Outage demo $RUN\",\"description\":\"kafka outage demo\",\"startingPrice\":50.00,\"minIncrement\":5.00,\"startsAt\":\"$(iso_in 10)\",\"endsAt\":\"$(iso_in 55)\"}"
check "auction created" 201 "$CODE"
AUCTION_ID=$(json_str "$BODY" id); [ -n "$AUCTION_ID" ] || abort "no auction id: $BODY"
status=""
for _ in $(seq 1 40); do call "$ANON_JAR" GET "/auctions/$AUCTION_ID"; status=$(json_str "$BODY" status); [ "$status" = ACTIVE ] && break; sleep 1; done
check "auction is ACTIVE" ACTIVE "$status"
call "$BIDDER_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":60.00}"
check "bid placed while Kafka is up" 201 "$CODE"
placed=0
for _ in $(seq 1 15); do call "$BIDDER_JAR" GET /me/notifications; placed=$(count_type "$BODY" BID_PLACED); [ "$placed" -ge 1 ] && break; sleep 1; done
check "bid confirmation delivered (Kafka up)" 1 "$placed"

echo "== Kafka goes down"
docker stop "$KAFKA_CONTAINER" >/dev/null 2>&1 || abort "could not stop $KAFKA_CONTAINER"
info "$KAFKA_CONTAINER stopped; waiting for the auction to end (about a minute)..."
for _ in $(seq 1 60); do call "$ANON_JAR" GET "/auctions/$AUCTION_ID"; status=$(json_str "$BODY" status); [ "$status" = ENDED ] && break; sleep 2; done
check "auction still ended correctly with Kafka down" ENDED "$status"
check "winner recorded with Kafka down" "$BIDDER_ID" "$(json_str "$BODY" winnerId)"
sleep 12
call "$BIDDER_JAR" GET /me/notifications
check "no 'you won' yet: the event is waiting in the outbox" 0 "$(count_type "$BODY" AUCTION_WON)"

echo "== Kafka comes back"
docker start "$KAFKA_CONTAINER" >/dev/null 2>&1 || abort "could not start $KAFKA_CONTAINER"
info "$KAFKA_CONTAINER started; waiting for the outbox to deliver (Kafka needs ~20-40s to be ready)..."
won=0; sold=0
for _ in $(seq 1 90); do
  call "$BIDDER_JAR" GET /me/notifications; won=$(count_type "$BODY" AUCTION_WON)
  call "$SELLER_JAR" GET /me/notifications; sold=$(count_type "$BODY" AUCTION_ENDED_AS_SELLER)
  [ "$won" -ge 1 ] && [ "$sold" -ge 1 ] && break
  sleep 2
done
check "winner was told after Kafka recovered" 1 "$won"
check "seller was told after Kafka recovered" 1 "$sold"

echo
if [ "$fail" = 0 ]; then echo "demo passed: nothing was lost during the outage"; else echo "demo FAILED"; fi
exit $fail
