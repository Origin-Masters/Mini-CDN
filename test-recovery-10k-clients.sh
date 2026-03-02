#!/bin/bash
set -euo pipefail

# Admin token for protected endpoints.
TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
# Test file used for warmup/load/recovery checks.
FILE="recovery-10k.txt"

# 1) Make sure services are up (start them if router health fails).
echo "[1/7] Ensure services are running..."
if ! curl -sf -H "X-Admin-Token: $TOKEN" "http://localhost:8082/api/cdn/health" >/dev/null 2>&1; then
  echo "  router is down -> starting services"
  ./startup-service.sh >/dev/null
fi

# 2) Ensure router can route EU requests to the edge node.
echo "[2/7] Register edge at router..."
curl -sf -X POST -H "X-Admin-Token: $TOKEN" \
  "http://localhost:8082/api/cdn/routing?region=EU&url=http://localhost:8081" >/dev/null

# 3) Upload one small file to origin as benchmark.
echo "[3/7] Upload test file to origin..."
curl -sf -X PUT -H "X-Admin-Token: $TOKEN" -H "Content-Type: application/octet-stream" \
  --data "recovery-test" "http://localhost:8080/api/origin/admin/files/$FILE" >/dev/null

# 4) Warm edge cache and verify MISS -> HIT behavior before restart.
echo "[4/7] Warm edge cache (MISS -> HIT)..."
curl -sD - "http://localhost:8081/api/edge/files/$FILE" -o /dev/null | tr -d '\r' | grep -q "X-Cache: MISS"
curl -sD - "http://localhost:8081/api/edge/files/$FILE" -o /dev/null | tr -d '\r' | grep -q "X-Cache: HIT"

# 5) Simulate 10,000 unique clients via router.
echo "[5/7] Send 10,000 client requests..."
for i in $(seq 1 10000); do
  curl -sf -o /dev/null -H "X-Client-Id: client-$i" \
    "http://localhost:8082/api/cdn/files/$FILE?region=EU"
  if [ $((i % 1000)) -eq 0 ]; then
    echo "  progress: $i/10000"
  fi
done

# 6) Restart and measure how long recovery takes.
echo "[6/7] Restart services and measure recovery..."
SECONDS=0
./shutdown-services.sh >/dev/null
./startup-service.sh >/dev/null

# Wait for router liveness (max 60s).
echo "  waiting for router health..."
for _ in $(seq 1 60); do
  if curl -sf -H "X-Admin-Token: $TOKEN" "http://localhost:8082/api/cdn/health" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -sf -H "X-Admin-Token: $TOKEN" "http://localhost:8082/api/cdn/health" >/dev/null 2>&1 \
  || { echo "FAIL: router health timeout"; exit 1; }

# Wait until routing endpoint is functional again (expects redirect 307).
echo "  waiting for router redirect..."
for _ in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' \
    "http://localhost:8082/api/cdn/files/$FILE?region=EU&clientId=probe")"
  if [ "$code" = "307" ]; then
    break
  fi
  sleep 1
done
code="$(curl -s -o /dev/null -w '%{http_code}' \
  "http://localhost:8082/api/cdn/files/$FILE?region=EU&clientId=probe")"
[ "$code" = "307" ] || { echo "FAIL: router did not recover redirect (last status: $code)"; exit 1; }

# 7) Validate edge cache survived restart and NFA time budget is met.
echo "[7/7] Verify recovered edge cache + time budget..."
curl -sD - "http://localhost:8081/api/edge/files/$FILE" -o /dev/null | tr -d '\r' | grep -q "X-Cache: HIT" \
  || { echo "FAIL: cache recovery expected HIT"; exit 1; }

# Requirement: recovery must be strictly less than 10 seconds.
if [ "$SECONDS" -ge 10 ]; then
  echo "FAIL: ${SECONDS}s >= 10s"
  exit 1
fi

echo "OK: ${SECONDS}s < 10s with 10000 clients"
