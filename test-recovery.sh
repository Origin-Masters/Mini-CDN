#!/bin/bash
set -e

ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
ORIGIN="http://localhost:8080"
EDGE="http://localhost:8081"
ROUTER="http://localhost:8082"
FILE="recovery-basic-$(date +%s).txt"

# prepare state
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "$ROUTER/api/cdn/routing?region=eu-west&url=$EDGE" >/dev/null

curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data "basic" \
  "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null

curl -sf -X PATCH -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"defaultTtlMs":120000,"maxEntries":150,"replacementStrategy":"LFU"}' \
  "$EDGE/api/edge/admin/config" >/dev/null

curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prefix":"recovery-basic","ttlMs":240000}' \
  "$EDGE/api/edge/admin/config/ttl" >/dev/null

# restart
./shutdown-services.sh >/dev/null
./startup-service.sh >/dev/null

# verify
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/routing" | grep -qi eu-west
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$EDGE/api/edge/admin/config" | grep -q '"replacementStrategy":"LFU"'
curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$EDGE/api/edge/admin/config/ttl" | grep -q '"recovery-basic":240000'

CACHE=$(curl -sI "$EDGE/api/edge/files/$FILE" | tr -d '\r' | awk -F': ' '/^X-Cache:/{print $2}')
[ "$CACHE" = "MISS" ]

echo "OK"

