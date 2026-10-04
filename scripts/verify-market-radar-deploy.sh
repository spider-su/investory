#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
APP_USER="${APP_USER:-}"
APP_PASSWORD="${APP_PASSWORD:-}"

echo "Checking process health: $BASE_URL/actuator/health"
curl --fail --silent --show-error "$BASE_URL/actuator/health"
echo

if [ -z "$APP_USER" ] || [ -z "$APP_PASSWORD" ]; then
  echo "APP_USER/APP_PASSWORD not set; authenticated Radar page checks skipped."
  exit 0
fi

AUTH="$APP_USER:$APP_PASSWORD"

echo "Checking Market Radar page"
curl --fail --silent --show-error --user "$AUTH" --output /dev/null "$BASE_URL/market-radar"

echo "Checking Market Radar operations page"
curl --fail --silent --show-error --user "$AUTH" --output /dev/null "$BASE_URL/market-radar/operations"

echo "Checking Market Radar validation page"
curl --fail --silent --show-error --user "$AUTH" --output /dev/null "$BASE_URL/market-radar/validation"

echo "Market Radar deployment HTTP verification passed."
