#!/bin/bash
set -e

ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
ORIGIN="http://localhost:8080"
EDGE="http://localhost:8081"
ROUTER="http://localhost:8082"
REGION="EU"
FILE="recovery-basic-$(date +%s).txt"

# idea : set up state, restart services,
# verify state is preserved and is persistent after restart.

# Ensure services are up before configuring state.
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/health" >/dev/null 2>&1 || ./startup-service.sh >/dev/null
sleep 6


# Configure router, origin, and edge state.
# Register edge at router for the test region.
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "$ROUTER/api/cdn/routing?region=$REGION&url=$EDGE" >/dev/null

# Upload a test file to origin.
curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data "basic" \
  "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null

# Set edge cache defaults (TTL, max entries, replacement strategy).
curl -sf -X PATCH -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"defaultTtlMs":120000,"maxEntries":150,"replacementStrategy":"LFU"}' \
  "$EDGE/api/edge/admin/config" >/dev/null

# Set edge prefix-specific TTL for recovery-basic.
curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prefix":"recovery-basic","ttlMs":240000}' \
  "$EDGE/api/edge/admin/config/ttl" >/dev/null

# Restart to test persistence of the state.
./shutdown-services.sh >/dev/null
./startup-service.sh >/dev/null
sleep 6

# Verify state and a cache MISS on first fetch.
# Routing still contains the region.
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/routing" | grep -qi "$REGION"

# Edge config still uses LFU.
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$EDGE/api/edge/admin/config" | grep -q '"replacementStrategy":"LFU"'

# TTL config still contains recovery-basic.
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$EDGE/api/edge/admin/config/ttl" | grep -q '"recovery-basic":240000'

# First fetch should be a cache MISS.
CACHE=$(curl -sI "$EDGE/api/edge/files/$FILE" | tr -d '\r' | awk -F': ' '/^X-Cache:/{print $2}')

[ "$CACHE" = "MISS" ]

echo "OK"
