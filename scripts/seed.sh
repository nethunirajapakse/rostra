#!/usr/bin/env bash
# Fills an empty Rostra with demo data by calling the real API through the gateway, so users, bids,
# notifications and watchlists are exactly what the app itself would produce. Reads scripts/seed/*.txt.
#
#   ./scripts/seed.sh
#
# Needs the whole stack running on a freshly reset database: `docker compose up` (see db-reset.sh).
# Overrides: GW=http://localhost:8080  SEED_PASSWORD=Rostra@Demo1  SEED_FORCE=1 (seed even if auctions exist)
set -uo pipefail

GW="${GW:-http://localhost:8080}"
SEED_PASSWORD="${SEED_PASSWORD:-Rostra@Demo1}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/seed"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILS=0
fail() { echo "FAIL  $*"; FAILS=$((FAILS + 1)); }
abort() { echo "ABORT $*"; exit 1; }

# ---- helpers (same approach as smoke-flow.sh) ----
xsrf() { [ -f "$1" ] || return 0; awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tail -n1; }
call() { local jar=$1 method=$2 path=$3 data=${4:-}
  local args=(-s -m 20 -c "$jar" -b "$jar" -X "$method" -w $'\n%{http_code}')
  if [ "$method" != GET ]; then args+=(-H "X-XSRF-TOKEN: $(xsrf "$jar")"); fi
  if [ -n "$data" ]; then args+=(-H 'Content-Type: application/json' -d "$data"); fi
  local out; out=$(curl "${args[@]}" "$GW$path"); CODE=${out##*$'\n'}; BODY=${out%$'\n'*}; }
json_str() { printf '%s' "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -n1 | cut -d'"' -f4; }
# Uploads one image file from seed/images/ as the auction's seller (multipart, with the CSRF header).
upload_image() { local jar=$1 auction_id=$2 file=$3
  local out
  # cd first: curl then gets a plain relative file name, which avoids Windows path conversion trouble in Git Bash.
  out=$(cd "$DIR/images" && curl -s -m 60 -c "$jar" -b "$jar" -X POST -H "X-XSRF-TOKEN: $(xsrf "$jar")" \
          -F "file=@$file;type=image/jpeg" -w $'\n%{http_code}' "$GW/auctions/$auction_id/images")
  CODE=${out##*$'\n'}; BODY=${out%$'\n'*}; }
iso_in() { date -u -d "+$1 seconds" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+"$1"S +%Y-%m-%dT%H:%M:%SZ; }

# "2d6h" -> seconds. Accepts d, h, m, s parts in any combination.
to_seconds() { local s=$1 total=0 n
  while [[ $s =~ ^([0-9]+)([dhms])(.*)$ ]]; do
    n=${BASH_REMATCH[1]}
    case ${BASH_REMATCH[2]} in d) total=$((total + n * 86400));; h) total=$((total + n * 3600));; m) total=$((total + n * 60));; s) total=$((total + n));; esac
    s=${BASH_REMATCH[3]}
  done
  [ -z "$s" ] || return 1
  echo "$total"; }

# Prints the data lines of a file: no comments, no blanks, no carriage returns.
rows() { tr -d '\r' < "$1" | grep -vE '^[[:space:]]*(#|$)'; }

# ---- checks before touching anything ----
for f in users auctions bids watchlist; do [ -f "$DIR/$f.txt" ] || abort "missing $DIR/$f.txt"; done
call "$TMP/anon.txt" GET "/auctions?size=1"
[ "$CODE" = 200 ] || abort "gateway not answering at $GW (got '$CODE'). Are the five services running?"
existing=$(printf '%s' "$BODY" | grep -oE '"totalElements"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' | head -n1)
if [ "${existing:-0}" -gt 0 ] && [ -z "${SEED_FORCE:-}" ]; then
  abort "there are already $existing auction(s). Run scripts/db-reset.sh first (or SEED_FORCE=1 to add anyway)."
fi

# ---- 1. users ----
echo "== users"
while IFS='|' read -r key first last email; do
  jar="$TMP/jar_$key.txt"
  call "$jar" POST /auth/signup "{\"firstName\":\"$first\",\"lastName\":\"$last\",\"email\":\"$email\",\"password\":\"$SEED_PASSWORD\"}"
  if [ "$CODE" != 201 ]; then fail "signup $email -> $CODE $BODY"; continue; fi
  call "$jar" POST /auth/signin "{\"email\":\"$email\",\"password\":\"$SEED_PASSWORD\"}"
  if [ "$CODE" != 200 ]; then fail "signin $email -> $CODE"; continue; fi
  call "$jar" GET /auth/me
  uid=$(json_str "$BODY" userId)
  if [ -z "$uid" ]; then fail "no userId for $email"; continue; fi
  echo "$uid" > "$TMP/uid_$key.txt"
  echo "ok    $key  $email"
done < <(rows "$DIR/users.txt")
[ "$FAILS" -eq 0 ] || abort "user creation failed, nothing else was done. Fix the above and reset before retrying."

# ---- 2. auctions ----
echo "== auctions"
while IFS='|' read -r key seller title desc price inc starts ends; do
  jar="$TMP/jar_$seller.txt"
  [ -f "$jar" ] || { fail "auction $key: unknown seller '$seller'"; continue; }
  s=$(to_seconds "$starts") && e=$(to_seconds "$ends") || { fail "auction $key: bad duration '$starts' / '$ends'"; continue; }
  call "$jar" POST /auctions "{\"title\":\"$title\",\"description\":\"$desc\",\"startingPrice\":$price,\"minIncrement\":$inc,\"startsAt\":\"$(iso_in "$s")\",\"endsAt\":\"$(iso_in "$e")\"}"
  if [ "$CODE" != 201 ]; then fail "auction $key -> $CODE $BODY"; continue; fi
  id=$(json_str "$BODY" id)
  echo "$id" > "$TMP/aid_$key.txt"
  echo "$starts" > "$TMP/starts_$key.txt"
  echo "$s" > "$TMP/startsec_$key.txt"
  echo "ok    $key  $title"
done < <(rows "$DIR/auctions.txt")

# ---- 2b. photos (two per auction, files named <auction key>-1.jpg, -2.jpg ...) ----
echo "== photos"
photos=0
while IFS='|' read -r key seller _rest; do
  [ -f "$TMP/aid_$key.txt" ] || continue
  for file in "$DIR/images/$key"-*.jpg; do
    [ -f "$file" ] || continue
    upload_image "$TMP/jar_$seller.txt" "$(cat "$TMP/aid_$key.txt")" "$(basename "$file")"
    if [ "$CODE" = 201 ]; then photos=$((photos + 1)); else fail "photo $(basename "$file") -> $CODE $BODY"; fi
  done
done < <(rows "$DIR/auctions.txt")
echo "ok    $photos photos uploaded"

# The scheduler activates auctions every 10 s. Bids are only accepted on ACTIVE ones.
echo "== waiting for the scheduler to activate the auctions that start within a minute"
deadline=$((SECONDS + 90))
for f in "$TMP"/startsec_*.txt; do
  key=${f##*/startsec_}; key=${key%.txt}
  [ "$(cat "$f")" -le 60 ] || continue
  while :; do
    call "$TMP/anon.txt" GET "/auctions/$(cat "$TMP/aid_$key.txt")"
    [ "$(json_str "$BODY" status)" = ACTIVE ] && break
    [ "$SECONDS" -lt "$deadline" ] || { fail "$key did not become ACTIVE in time"; break; }
    sleep 2
  done
done

# ---- 3. bids ----
echo "== bids"
placed=0
while IFS='|' read -r auction bidder amount; do
  jar="$TMP/jar_$bidder.txt"
  [ -f "$TMP/aid_$auction.txt" ] && [ -f "$jar" ] || { fail "bid $auction/$bidder: unknown auction or bidder"; continue; }
  call "$jar" POST /bids "{\"auctionId\":\"$(cat "$TMP/aid_$auction.txt")\",\"amount\":$amount}"
  if [ "$CODE" = 201 ]; then placed=$((placed + 1)); else fail "bid $bidder on $auction at $amount -> $CODE $BODY"; fi
done < <(rows "$DIR/bids.txt")
echo "ok    $placed bids placed"

# ---- 4. watchlist ----
echo "== watchlist"
watched=0
while IFS='|' read -r user auction; do
  jar="$TMP/jar_$user.txt"
  [ -f "$TMP/aid_$auction.txt" ] && [ -f "$jar" ] || { fail "watch $user/$auction: unknown user or auction"; continue; }
  call "$jar" POST "/auctions/$(cat "$TMP/aid_$auction.txt")/watch"
  if [ "$CODE" = 200 ]; then watched=$((watched + 1)); else fail "watch $user -> $auction -> $CODE $BODY"; fi
done < <(rows "$DIR/watchlist.txt")
echo "ok    $watched watchlist entries"

# ---- summary ----
echo
if [ "$FAILS" -eq 0 ]; then echo "Seed finished with no errors."; else echo "Seed finished with $FAILS error(s), see FAIL lines above."; fi
cat <<MSG

Sign in at http://localhost:5173 with:
  demo@rostra.dev   /   $SEED_PASSWORD
(every seeded account uses that password, e.g. mira.fernando@example.com)

Three short auctions finish about two minutes after seeding. When they do, demo gets a "you won"
notification for the fly rod, and Lena wins the Polaroid. Leave the services running until then. Run the script again only after db-reset.sh.
MSG
[ "$FAILS" -eq 0 ]
