#!/bin/bash
set -euo pipefail

ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
ORIGIN="${ORIGIN_BASE_URL:-http://localhost:8080}"
EDGE="${EDGE_BASE_URL:-http://localhost:8081}"
ROUTER="${ROUTER_BASE_URL:-http://localhost:8082}"
REGION="${RECOVERY_REGION:-EU}"
FILE="recovery-cache-$(date +%s).txt"

cleanup() {
  curl -sf -X DELETE -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

now_ms() {
  local ts
  ts="$(date +%s%3N 2>/dev/null || true)"
  case "$ts" in
    *N*|"") echo "$(( $(date +%s) * 1000 ))" ;;
    *) echo "$ts" ;;
  esac
}

cache_header() {
  # Use GET to exercise the real cache path (HEAD only checks metadata and does not warm cache).
  curl -sD - "$EDGE/api/edge/files/$FILE" -o /dev/null \
    | tr -d '\r' \
    | awk -F': ' '/^X-Cache:/{print $2}'
}

echo "[1/6] Ensure [SERVICES] are running"
if ! curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/health" >/dev/null 2>&1; then
  ./startup-service.sh >/dev/null
fi

echo "[2/6] Ensure [EDGE] is registered at router"
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "$ROUTER/api/cdn/routing?region=$REGION&url=$EDGE" >/dev/null

echo "[3/6] Upload test file to [ORIGIN]"
curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data "cache-recovery-test" \
  "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null

echo "[4/6] Check [EDGE] (expect MISS then HIT)..."
CACHE_FIRST="$(cache_header)"
CACHE_SECOND="$(cache_header)"
echo "  before restart #1: $CACHE_FIRST"
echo "  before restart #2: $CACHE_SECOND"

[ "$CACHE_FIRST" = "MISS" ]
[ "$CACHE_SECOND" = "HIT" ]

echo "[5/6] Restart [SERVICES]..."
start_ms="$(now_ms)"
./shutdown-services.sh >/dev/null
./startup-service.sh >/dev/null

until curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/health" >/dev/null 2>&1; do
  sleep 1
done
end_ms="$(now_ms)"

echo "[6/6] Verify [CACHE] recovery after restart (expect HIT)..."
CACHE_AFTER_RESTART="$(cache_header)"
echo "  after restart #1: $CACHE_AFTER_RESTART"
[ "$CACHE_AFTER_RESTART" = "HIT" ]

echo "OK : [EDGE] [CACHE] recovered successfully (${start_ms} -> ${end_ms}, $((end_ms - start_ms)) ms)."
