#!/usr/bin/env bash
# End-to-end check of the whole platform through the gateway, using real cookie auth:
#   two users -> auction created -> activated by the scheduler -> bid placed -> price moved -> notification stored.
#
# Needs running: docker compose up (Postgres, Redis, Kafka), gateway, auth, auction, bidding, notification.
# Usage: ./scripts/smoke-flow.sh            (override the gateway URL with GW=http://host:8080)
set -uo pipefail

GW="${GW:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SELLER_JAR="$TMP/seller.txt"
BIDDER_JAR="$TMP/bidder.txt"
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

echo "== auction"
STARTS=$(iso_in 3)
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

info "waiting for the scheduler to activate it (it ticks every 10s)..."
status=""
for _ in $(seq 1 30); do
  call "$ANON_JAR" GET "/auctions/$AUCTION_ID"
  status=$(json_str "$BODY" status)
  [ "$status" = "ACTIVE" ] && break
  sleep 1
done
check "auction becomes ACTIVE" ACTIVE "$status"

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
check "auction price moved to 110 (bidding called auction with the forwarded token)" 1 "$moved"

echo "== notifications (needs Kafka + notification-service)"
for_auction() { printf '%s' "$1" | grep -oE "\"auctionId\"[[:space:]]*:[[:space:]]*\"$AUCTION_ID\"" | wc -l | tr -d ' '; }
bidder_count=0; seller_count=0; notif_code=200
for _ in $(seq 1 20); do
  call "$BIDDER_JAR" GET /me/notifications
  notif_code=$CODE
  [ "$CODE" = 200 ] || break
  bidder_count=$(for_auction "$BODY")
  call "$SELLER_JAR" GET /me/notifications
  seller_count=$(for_auction "$BODY")
  [ $((bidder_count + seller_count)) -gt 0 ] && break
  sleep 1
done
check "notification inbox answers" 200 "$notif_code"
if [ "$notif_code" != 200 ]; then
  info "is notification-service running? it also needs Kafka (docker ps should list rostra-kafka)"
fi
has=no; [ $((bidder_count + seller_count)) -gt 0 ] && has=yes
check "the bid produced a notification (bidding -> Kafka -> notification-service)" yes "$has"
info "bidder inbox: $bidder_count, seller inbox: $seller_count for this auction"
info "today the bidder is told about their own bid and the seller hears nothing; that changes in the next step"
call "$BIDDER_JAR" GET /me/notifications/unread-count
check "unread-count answers" 200 "$CODE"

echo
if [ "$fail" = 0 ]; then echo "all checks passed"; else echo "some checks FAILED"; fi
exit $fail
