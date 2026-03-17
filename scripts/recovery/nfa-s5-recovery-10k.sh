#!/bin/bash
set -euo pipefail

# Simple NFA-S5 recovery using the built exec JARs.
# It keeps the 10k progress output,

TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

ORIGIN_URL="http://localhost:8080"
EDGE_URL="http://localhost:8081"
ROUTER_URL="http://localhost:8082"
REGION="${RECOVERY_REGION:-EU}"
FILE="recovery-10k.txt"

# The 10k request phase can outlive the default 60s cache TTL.
# We set a dedicated TTL for the test file for post-restart HIT check
FILE_TTL_MS="${RECOVERY_FILE_TTL_MS:-300000}"

ORIGIN_JAR="$ROOT_DIR/origin/target/origin-1.0-SNAPSHOT-exec.jar"
EDGE_JAR="$ROOT_DIR/edge/target/edge-1.0-SNAPSHOT-exec.jar"
ROUTER_JAR="$ROOT_DIR/router/target/router-1.0-SNAPSHOT-exec.jar"

ORIGIN_LOG="$ROOT_DIR/origin.log"
EDGE_LOG="$ROOT_DIR/edge.log"
ROUTER_LOG="$ROOT_DIR/router.log"

ADMIN_HEADER=(-H "X-Admin-Token: $TOKEN")

now_ms() {
  local ts
  # Works on systems with and without native millisecond output in `date`.
  ts="$(date +%s%3N 2>/dev/null || true)"
  case "$ts" in
    *N*|"") echo "$(( $(date +%s) * 1000 ))" ;;
    *) echo "$ts" ;;
  esac
}

pid_for_port() {
  # We only care about the process that currently owns the listening port.
  lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
}

stop_port() {
  local port="$1"
  local pid

  pid="$(pid_for_port "$port")"
  [ -n "$pid" ] || return 0

  # Try a normal stop first.
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    [ -z "$(pid_for_port "$port")" ] && return 0
    sleep 0.2
  done

  # If the port is still busy, force the process down.
  pid="$(pid_for_port "$port")"
  [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
}

wait_http_ok() {
  local url="$1"
  shift || true

  # Do not continue until the service really answers on its health endpoint.
  for _ in $(seq 1 60); do
    if curl -sf "$@" "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done

  echo "FAIL: Health-Check Timeout fuer $url"
  exit 1
}

start_service() {
  local workdir="$1"
  local jar="$2"
  local log="$3"
  shift 3

  (
    cd "$workdir"
    # Each service logs to its own file to keep failures debuggable.
    exec java -jar "$jar" "$@" > "$log" 2>&1
  ) &
}

start_stack() {
  export MINICDN_ADMIN_TOKEN="$TOKEN"

  # Start in dependency order: origin -> edge -> router.
  start_service "$ROOT_DIR/origin" "$ORIGIN_JAR" "$ORIGIN_LOG" --spring.profiles.active=origin
  wait_http_ok "$ORIGIN_URL/api/origin/health"

  start_service "$ROOT_DIR/edge" "$EDGE_JAR" "$EDGE_LOG" \
    --spring.profiles.active=edge \
    --server.port=8081 \
    --origin.base-url="$ORIGIN_URL" \
    --edge.region="$REGION"
  wait_http_ok "$EDGE_URL/api/edge/health"

  start_service "$ROOT_DIR/router" "$ROUTER_JAR" "$ROUTER_LOG" --spring.profiles.active=cdn
  wait_http_ok "$ROUTER_URL/api/cdn/health" "${ADMIN_HEADER[@]}"
}

stop_stack() {
  stop_port 8082
  stop_port 8081
  stop_port 8080
}

cache_header() {
  # The edge exposes MISS/HIT via the X-Cache header.
  curl -sD - "$EDGE_URL/api/edge/files/$FILE" -o /dev/null \
    | tr -d '\r' \
    | awk -F': ' '/^X-Cache:/{print $2}'
}

wait_router_redirect() {
  local code

  # Recovery is only complete when real CDN routing works again, not just health.
  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' \
      "$ROUTER_URL/api/cdn/files/$FILE?region=$REGION&clientId=probe")"
    [ "$code" = "307" ] && return 0
    sleep 0.5
  done

  echo "FAIL: Router liefert nach Neustart keinen Redirect."
  exit 1
}

for jar in "$ORIGIN_JAR" "$EDGE_JAR" "$ROUTER_JAR"; do
  [ -f "$jar" ] || {
    echo "FAIL: exec JAR fehlt: $jar"
    echo "Hinweis: zuerst 'mvn -DskipTests package' ausfuehren."
    exit 1
  }
done

echo "[1/7] Stop old services on 8080/8081/8082"
stop_stack

echo "[2/7] Start origin, edge and router from exec JARs"
start_stack

echo "[3/7] Create persisted state"
# Router needs the edge in its routing table
curl -sf -X POST "${ADMIN_HEADER[@]}" \
  "$ROUTER_URL/api/cdn/routing?region=$REGION&url=$EDGE_URL" >/dev/null
# Store one small file at the origin; this is the file we warm and recover later.
curl -sf -X PUT "${ADMIN_HEADER[@]}" \
  -H "Content-Type: application/octet-stream" \
  --data "recovery-test" \
  "$ORIGIN_URL/api/origin/admin/files/$FILE" >/dev/null
# Keep this test file alive long enough for the load phase plus restart.
curl -sf -X PUT "${ADMIN_HEADER[@]}" \
  -H "Content-Type: application/json" \
  -d "{\"prefix\":\"$FILE\",\"ttlMs\":$FILE_TTL_MS}" \
  "$EDGE_URL/api/edge/admin/configs/expirations" >/dev/null

echo "[4/7] Warm edge cache (MISS -> HIT)"
# First request fills the cache, second request proves the cache is active.
CACHE_FIRST="$(cache_header)"
CACHE_SECOND="$(cache_header)"
echo "  before restart #1: $CACHE_FIRST"
echo "  before restart #2: $CACHE_SECOND"
[ "$CACHE_FIRST" = "MISS" ]
[ "$CACHE_SECOND" = "HIT" ]

echo "[5/7] Send 10,000 client requests"
for i in $(seq 1 10000); do
  # Requests go through the router to simulate client traffic under load.
  curl -sf -o /dev/null -H "X-Client-Id: client-$i" \
    "$ROUTER_URL/api/cdn/files/$FILE?region=$REGION"
  if [ $((i % 1000)) -eq 0 ]; then
    echo "  progress: $i/10000"
  fi
done

echo "[6/7] Restart services and measure recovery"
stop_stack
# Only the restart/recovery window counts for the <10s requirement.
start_ms="$(now_ms)"
start_stack
wait_router_redirect

echo "[7/7] Verify recovered cache and time budget"
CACHE_AFTER_RESTART="$(cache_header)"
echo "  after restart #1: $CACHE_AFTER_RESTART"
# After a successful recovery, the warmed cache entry must still be present.
[ "$CACHE_AFTER_RESTART" = "HIT" ] || {
  echo "FAIL: cache recovery expected HIT"
  exit 1
}

recovery_ms="$(( $(now_ms) - start_ms ))"
[ "$recovery_ms" -lt 10000 ] || {
  echo "FAIL: ${recovery_ms}ms >= 10000ms"
  exit 1
}

echo "OK: ${recovery_ms}ms < 10000ms with 10000 clients"
