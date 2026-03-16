#!/bin/bash
set -euo pipefail

ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
ORIGIN="${ORIGIN_BASE_URL:-http://localhost:8080}"
EDGE="${EDGE_BASE_URL:-http://localhost:8081}"
ROUTER="${ROUTER_BASE_URL:-http://localhost:8082}"
REGION="${RECOVERY_REGION:-EU}"
FILE="recovery-crash-$(date +%s).txt"
PAYLOAD="crash-safe-payload"

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

now_ms() {
  local ts
  ts="$(date +%s%3N 2>/dev/null || true)"
  case "$ts" in
    *N*|"") echo "$(( $(date +%s) * 1000 ))" ;;
    *) echo "$ts" ;;
  esac
}

edge_cache_header() {
  curl -sD - "$EDGE/api/edge/files/$FILE" -o /dev/null \
    | tr -d '\r' \
    | awk -F': ' '/^X-Cache:/{print $2}'
}

ensure_up() {
  if ! curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/health" >/dev/null 2>&1; then
    timeout 90 ./startup-service.sh >/dev/null
  fi

  for _ in $(seq 1 40); do
    if curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" "$ROUTER/api/cdn/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done

  echo "FAIL: Router konnte nicht gestartet werden (Health-Timeout)."
  exit 1
}

cleanup() {
  curl -sf -X DELETE -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

log "[1/7] Dienste prüfen/starten"
ensure_up

log "[2/7] Routing und Testdaten vorbereiten"
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "$ROUTER/api/cdn/routing?region=$REGION&url=$EDGE" >/dev/null

curl -sf -X PUT -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data "$PAYLOAD" \
  "$ORIGIN/api/origin/admin/files/$FILE" >/dev/null

log "[3/7] Cache vor Crash aufwärmen (MISS -> HIT)"
first_cache="$(edge_cache_header)"
second_cache="$(edge_cache_header)"
[ "$first_cache" = "MISS" ]
[ "$second_cache" = "HIT" ]

before_body="$(curl -sf "$EDGE/api/edge/files/$FILE")"
[ "$before_body" = "$PAYLOAD" ]

log "[4/7] EDGE-Prozess hart beenden (Crash-Simulation)"
# lsof kann mehrere PIDs liefern (z. B. Java-Prozess + Child-Prozess).
# Daher alle ermittelten PIDs robust zeilenweise terminieren.
mapfile -t EDGE_PIDS < <(lsof -t -i:8081 2>/dev/null | awk '!seen[$0]++' || true)
[ "${#EDGE_PIDS[@]}" -gt 0 ] || { echo "FAIL: EDGE PID nicht gefunden"; exit 1; }
for pid in "${EDGE_PIDS[@]}"; do
  [[ "$pid" =~ ^[0-9]+$ ]] || continue
  kill -9 "$pid" 2>/dev/null || true
done

log "[5/7] EDGE neu starten und Recovery-Zeit messen"
start_ms="$(now_ms)"
(
  cd edge
  mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=edge" > ../edge.log 2>&1
) &

for _ in $(seq 1 20); do
  if curl -sf "$EDGE/api/edge/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done
curl -sf "$EDGE/api/edge/health" >/dev/null 2>&1 || { echo "FAIL: EDGE Recovery Timeout"; exit 1; }
end_ms="$(now_ms)"
recovery_ms="$((end_ms - start_ms))"

log "[6/7] Zustand und Datenintegrität nach Recovery prüfen"
after_cache="$(edge_cache_header)"
[ "$after_cache" = "HIT" ] || { echo "FAIL: erwarteter Cache-HIT nach Crash-Recovery, erhalten: $after_cache"; exit 1; }

after_body="$(curl -sf "$EDGE/api/edge/files/$FILE")"
[ "$after_body" = "$PAYLOAD" ] || { echo "FAIL: Datenverlust erkannt"; exit 1; }

log "[7/7] NFA-Grenze prüfen (< 10s)"
if [ "$recovery_ms" -ge 10000 ]; then
  echo "FAIL: Recovery ${recovery_ms}ms >= 10000ms"
  exit 1
fi

echo "OK: Crash-Recovery erfolgreich, kein Datenverlust, Recovery ${recovery_ms}ms (<10s)."
