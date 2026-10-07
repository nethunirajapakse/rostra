#!/usr/bin/env bash
# End-to-end check of the whole platform through the gateway, using real cookie auth:
#   two users -> auction created -> activated by the scheduler -> bid placed -> price moved -> notification stored.
#
# Needs running: docker compose up (Postgres, Redis, Kafka), gateway, auth, auction, bidding, notification.
# Usage: ./scripts/smoke-flow.sh            (override the gateway URL with GW=http://host:8080)
set -uo pipefail

GW="${GW:-http://localhost:8080}"
TMP="$(mktemp -d)"
WS_PIDS=()
trap 'for p in "${WS_PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done; rm -rf "$TMP"' EXIT
SELLER_JAR="$TMP/seller.txt"
BIDDER_JAR="$TMP/bidder.txt"
RIVAL_JAR="$TMP/rival.txt"
ANON_JAR="$TMP/anon.txt"
RUN="$RANDOM$RANDOM"
PASS='Passw0rd!smoke'
fail=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "ok    $1 ($3)"; else echo "FAIL  $1 (expected $2, got $3)"; fail=1; fi
}
info() { echo "      $*"; }
abort() { echo "ABORT $*"; exit 1; }

xsrf() { [ -f "$1" ] || return 0; awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tail -n1; }

# call <jar> <METHOD> <path> [json body]   ->  sets CODE and BODY
call() {
  local jar=$1 method=$2 path=$3 data=${4:-}
  local args=(-s -m 15 -c "$jar" -b "$jar" -X "$method" -w $'\n%{http_code}')
  if [ "$method" != GET ]; then args+=(-H "X-XSRF-TOKEN: $(xsrf "$jar")"); fi
  if [ -n "$data" ]; then args+=(-H 'Content-Type: application/json' -d "$data"); fi
  local out
  out=$(curl "${args[@]}" "$GW$path")
  CODE=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

# json_str <json> <field>  ->  first string value of "field" (responses are compact JSON)
json_str() { printf '%s' "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -n1 | cut -d'"' -f4; }

iso_in() { date -u -d "+$1 seconds" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+"$1"S +%Y-%m-%dT%H:%M:%SZ; }

echo "== preflight"
code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$GW/actuator/health")
[ "$code" != "000" ] || abort "gateway not reachable at $GW (is it running?)"
call "$ANON_JAR" GET /auctions
check "auction-service answers through the gateway" 200 "$CODE"
[ "$CODE" = 200 ] || abort "start auction-service (and Postgres) first"

echo "== users"
register() { # jar label  -> sets USER_ID
  local jar=$1 label=$2 email="smoke-$2-$RUN@rostra.dev"
  call "$jar" POST /auth/signup "{\"firstName\":\"Smoke\",\"lastName\":\"$label\",\"email\":\"$email\",\"password\":\"$PASS\"}"
  check "$label signs up" 201 "$CODE"
  call "$jar" POST /auth/signin "{\"email\":\"$email\",\"password\":\"$PASS\"}"
  check "$label signs in" 200 "$CODE"
  call "$jar" GET /auth/me
  USER_ID=$(json_str "$BODY" userId)
  [ -n "$USER_ID" ] || abort "could not read $label's user id from /auth/me: $BODY"
}
register "$SELLER_JAR" seller;  SELLER_ID=$USER_ID
register "$BIDDER_JAR" bidder;  BIDDER_ID=$USER_ID
register "$RIVAL_JAR" rival;    RIVAL_ID=$USER_ID

echo "== auction"
STARTS=$(iso_in 25)
ENDS=$(iso_in 900)
call "$SELLER_JAR" POST /auctions \
  "{\"title\":\"Smoke auction $RUN\",\"description\":\"end-to-end smoke test\",\"startingPrice\":100.00,\"minIncrement\":10.00,\"startsAt\":\"$STARTS\",\"endsAt\":\"$ENDS\"}"
check "seller creates an auction" 201 "$CODE"
AUCTION_ID=$(json_str "$BODY" id)
[ -n "$AUCTION_ID" ] || abort "no auction id in response: $BODY"
check "auction belongs to the seller (identity reached auction-service)" "$SELLER_ID" "$(json_str "$BODY" sellerId)"

call "$ANON_JAR" GET "/auctions/$AUCTION_ID"
check "anonymous visitor can read the auction" 200 "$CODE"
call "$ANON_JAR" POST /auctions '{}'
check "anonymous visitor cannot create one" 401 "$CODE"
call "$BIDDER_JAR" PATCH "/auctions/$AUCTION_ID" '{"title":"hijacked"}'
check "another user cannot edit the auction" 403 "$CODE"
call "$SELLER_JAR" PATCH "/auctions/$AUCTION_ID" '{"title":"Smoke auction (edited)"}'
check "seller can edit their own auction" 200 "$CODE"

info "waiting for the start time + scheduler tick (up to ~40s)..."
status=""
for _ in $(seq 1 60); do
  call "$ANON_JAR" GET "/auctions/$AUCTION_ID"
  status=$(json_str "$BODY" status)
  [ "$status" = "ACTIVE" ] && break
  sleep 1
done
check "auction becomes ACTIVE" ACTIVE "$status"

echo "== websocket listeners (live push through the gateway)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WS_URL="${GW/http/ws}/ws/notifications"
WS_OK=no
if command -v node >/dev/null 2>&1 && node -e 'process.exit(typeof WebSocket==="function"?0:1)' 2>/dev/null; then
  WS_OK=yes
  token_of() { awk '$6=="access_token"{print $7}' "$1" | tail -n1; }
  ws_start() { # label token seconds
    node "$SCRIPT_DIR/ws-listen.mjs" "$WS_URL" "$2" "$TMP/ws-$1.txt" "$3" >/dev/null 2>&1 &
    WS_PIDS+=("$!")
  }
  ws_start seller "$(token_of "$SELLER_JAR")" 60
  ws_start bidder "$(token_of "$BIDDER_JAR")" 60
  ws_start rival  "$(token_of "$RIVAL_JAR")" 60
  ws_start anon   "" 8
  for _ in $(seq 1 10); do
    grep -q '^OPEN' "$TMP/ws-seller.txt" 2>/dev/null && grep -q '^OPEN' "$TMP/ws-bidder.txt" 2>/dev/null \
      && grep -q '^OPEN' "$TMP/ws-rival.txt" 2>/dev/null && break
    sleep 1
  done
  ws_has() { grep -c "$2" "$TMP/ws-$1.txt" 2>/dev/null || true; }
  opened=no; grep -q '^OPEN' "$TMP/ws-seller.txt" 2>/dev/null && opened=yes
  check "logged-in user can open a WebSocket through the gateway" yes "$opened"
  sleep 2
  anon_open=no; grep -q '^OPEN' "$TMP/ws-anon.txt" 2>/dev/null && anon_open=yes
  check "an anonymous WebSocket is refused at the gateway (never opens)" no "$anon_open"
else
  info "skipping WebSocket checks: needs Node 22+ on PATH (node -v)"
fi

echo "== bidding"
call "$ANON_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":110.00}"
check "anonymous visitor cannot bid" 401 "$CODE"
call "$BIDDER_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":110.00}"
check "bidder places a valid bid" 201 "$CODE"
check "bid is recorded for the bidder" "$BIDDER_ID" "$(json_str "$BODY" bidderId)"
call "$BIDDER_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":115.00}"
check "bid below the minimum increment is rejected" 400 "$CODE"
call "$ANON_JAR" GET "/auctions/$AUCTION_ID"
moved=$(printf '%s' "$BODY" | grep -Eo '"currentPrice"[[:space:]]*:[[:space:]]*110(\.0+)?[,}]' | wc -l | tr -d ' ')
check "auction price moved to 110 (bidding called auction with its own service token)" 1 "$moved"

echo "== internal price endpoint is closed to everyone but bidding-service"
AUCTION_DIRECT="${AUCTION_DIRECT:-http://localhost:8082}"
ACCESS=$(awk '$6=="access_token"{print $7}' "$BIDDER_JAR" | tail -n1)
PRICE_BODY='{"newPrice":999,"expectedVersion":0}'
call "$BIDDER_JAR" PATCH "/auctions/$AUCTION_ID/current-price" "$PRICE_BODY"
check "through the gateway the endpoint does not exist" 404 "$CODE"
code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' -d "$PRICE_BODY" "$AUCTION_DIRECT/auctions/$AUCTION_ID/current-price")
check "directly on auction-service: anonymous is rejected" 401 "$code"
code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' -X PATCH -H "Cookie: access_token=$ACCESS" -H 'Content-Type: application/json' -d "$PRICE_BODY" "$AUCTION_DIRECT/auctions/$AUCTION_ID/current-price")
check "directly on auction-service: a logged-in user is forbidden" 403 "$code"

call "$SELLER_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":120.00}"
check "seller cannot bid on their own auction" 403 "$CODE"
call "$RIVAL_JAR" POST /bids "{\"auctionId\":\"$AUCTION_ID\",\"amount\":130.00}"
check "rival outbids the first bidder" 201 "$CODE"

echo "== notifications (needs Kafka + notification-service)"
count_type() { printf '%s' "$1" | grep -oE "\"type\"[[:space:]]*:[[:space:]]*\"$2\"" | wc -l | tr -d ' '; }
seller_n=0; bidder_placed=0; bidder_outbid=0; rival_placed=0; notif_code=200
for _ in $(seq 1 25); do
  call "$SELLER_JAR" GET /me/notifications; notif_code=$CODE
  [ "$CODE" = 200 ] || break
  seller_n=$(count_type "$BODY" BID_RECEIVED_ON_YOUR_AUCTION)
  call "$BIDDER_JAR" GET /me/notifications
  bidder_placed=$(count_type "$BODY" BID_PLACED); bidder_outbid=$(count_type "$BODY" OUTBID)
  call "$RIVAL_JAR" GET /me/notifications
  rival_placed=$(count_type "$BODY" BID_PLACED)
  [ "$seller_n" -ge 2 ] && [ "$bidder_outbid" -ge 1 ] && [ "$rival_placed" -ge 1 ] && break
  sleep 1
done
check "notification inbox answers" 200 "$notif_code"
if [ "$notif_code" != 200 ]; then
  info "is notification-service running? it also needs Kafka (docker ps should list rostra-kafka)"
fi
check "seller is told about both bids" 2 "$seller_n"
check "first bidder got a bid confirmation" 1 "$bidder_placed"
check "first bidder was told they were outbid" 1 "$bidder_outbid"
check "rival got a bid confirmation" 1 "$rival_placed"
if [ "$WS_OK" = yes ]; then
  sleep 1
  check "seller received both bids live" 2 "$(ws_has seller BID_RECEIVED_ON_YOUR_AUCTION | tr -d ' ')"
  check "first bidder was pushed the outbid notice live" 1 "$(ws_has bidder OUTBID | tr -d ' ')"
  check "rival was pushed a bid confirmation live" 1 "$(ws_has rival BID_PLACED | tr -d ' ')"
fi
call "$BIDDER_JAR" GET /me/notifications/unread-count
check "unread-count answers" 200 "$CODE"

echo
if [ "$fail" = 0 ]; then echo "all checks passed"; else echo "some checks FAILED"; fi
exit $fail
