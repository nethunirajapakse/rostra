#!/usr/bin/env bash
# End-to-end check of the cookie auth flow through the gateway (needs: docker compose up, gateway + auth-service running).
# Usage: ./scripts/smoke-auth.sh            (override the gateway URL with GW=http://host:8080)
set -uo pipefail

GW="${GW:-http://localhost:8080}"
JAR="$(mktemp)"
EMAIL="smoke$RANDOM@rostra.dev"
PASS='Passw0rd!smoke'
fail=0

status() { curl -s -o /dev/null -w '%{http_code}' -c "$JAR" -b "$JAR" "$@"; }
xsrf()   { awk '$6=="XSRF-TOKEN"{print $7}' "$JAR" | tail -n1; }
check()  { # name expected actual
  if [ "$2" = "$3" ]; then echo "ok    $1 ($3)"; else echo "FAIL  $1 (expected $2, got $3)"; fail=1; fi
}
JSON=(-H 'Content-Type: application/json')

check "signup"                         201 "$(status "${JSON[@]}" -X POST "$GW/auth/signup" \
      -d "{\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"
check "signin"                         200 "$(status "${JSON[@]}" -X POST "$GW/auth/signin" \
      -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"
check "me with cookie"                 200 "$(status "$GW/auth/me")"
check "refresh without CSRF header"    403 "$(status -X POST "$GW/auth/refresh")"
check "refresh with CSRF header"       200 "$(status -X POST -H "X-XSRF-TOKEN: $(xsrf)" "$GW/auth/refresh")"
check "me after rotation"              200 "$(status "$GW/auth/me")"
check "price endpoint hidden"          404 "$(status -X PATCH -H "X-XSRF-TOKEN: $(xsrf)" "${JSON[@]}" \
      "$GW/auctions/00000000-0000-0000-0000-000000000000/current-price" -d '{}')"
check "bearer header ignored at edge"  401 "$(curl -s -o /dev/null -w '%{http_code}' \
      -H 'Authorization: Bearer not-a-real-token' "$GW/auth/me")"

OLD_ACCESS="$(awk '$6=="access_token"{print $7}' "$JAR" | tail -n1)"
check "signout"                        200 "$(status -X POST -H "X-XSRF-TOKEN: $(xsrf)" "$GW/auth/signout")"
check "me after signout"               401 "$(status "$GW/auth/me")"
check "old access token is revoked"    401 "$(curl -s -o /dev/null -w '%{http_code}' -b "access_token=$OLD_ACCESS" "$GW/auth/me")"

rm -f "$JAR"
exit $fail
