#!/bin/bash


# shell script for NFA-C2 with our built exec-JARs.
# we simulate a real world crash by
# running `kill -9` on the Edge process,

# orgin and router keep running the whole time, only the Edge is restarted.


# Admin-Token for out REST endPoints.
# the token is expected to be in the header
# if token not set for some reason, use a default value
TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"

# Absoluter path for the directory so exec JAR paths are correct
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# base urls of the three services, we use for API calls.
ORIGIN_URL="http://localhost:8080"
EDGE_URL="http://localhost:8081"
ROUTER_URL="http://localhost:8082"

# Region wehre is edge is registered at the router, also used in the edge config.
# Standard is set to EU,
REGION="${RECOVERY_REGION:-EU}"

# unique test file name to avoid conflicts with other test
FILE="recovery-crash-$(date +%s).txt"

# content of our test file at the origin
PAYLOAD="crash-safe-payload-NFA-C2-$(date +%s)"

# TTL of the test file at the edge in milliseconds.
# !! for slow laptops set it high enough,
# file TTL Warmup, Crash and Restart
# nicht regulär aus dem Cache abläuft und dadurch einen falschen MISS erzeugt.
FILE_TTL_MS="${RECOVERY_FILE_TTL_MS:-300000}"

# paths to out executable JARs, expected to be built by `mvn package` beforehand.
ORIGIN_JAR="$ROOT_DIR/origin/target/origin-1.0-SNAPSHOT-exec.jar"
EDGE_JAR="$ROOT_DIR/edge/target/edge-1.0-SNAPSHOT-exec.jar"
ROUTER_JAR="$ROOT_DIR/router/target/router-1.0-SNAPSHOT-exec.jar"

# Log-files der drei gestarteten Prozesse.
ORIGIN_LOG="$ROOT_DIR/origin.log"
EDGE_LOG="$ROOT_DIR/edge.log"
ROUTER_LOG="$ROOT_DIR/router.log"

# Wartet darauf, dass ein HTTP-Endpunkt erfolgreich antwortet.
# Parameter:
#   $1 = URL
#   weitere Parameter = optionale curl-Argumente
wait_http_ok() {
  local url="$1"
  shift || true

  # Bis zu 60 Versuche mit jeweils 0.5 s Pause -> +/- 30 seconds Timeout.
  for _ in $(seq 1 60); do
    if curl -sf "$@" "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done

  echo "[ERROR]: Health-Check Error für $url"
  exit 1
}

# read the X-Cache header for the test file from the edge.
# expected values are "MISS" before the crash and "HIT" after the crash
cache_header() {
  curl -sD - "$EDGE_URL/api/edge/files/$FILE" -o /dev/null \
    | tr -d '\r' \
    | awk -F': ' '/^X-Cache:/{print $2}'
}

# PRÜFEN OB UNSERE JARS VORHANDEN SIND

# Prüft vorab, ob alle benötigten exec-JARs existieren.
# Falls nicht, bricht das Skript mit Hinweis auf den Maven-Build ab.
for jar in "$ORIGIN_JAR" "$EDGE_JAR" "$ROUTER_JAR"; do
  [ -f "$jar" ] || {
    echo "[ERROR]: exec JAR missing !!: $jar"
    echo "[WARNING]: zuerst 'mvn -DskipTests package' ausführen."
    exit 1
  }
done

echo "[1/7] Stop old services on 8080/8081/8082"
# Beendet eventuell noch laufende alte Prozesse
kill $(lsof -ti :8082 2>/dev/null) 2>/dev/null || true
kill $(lsof -ti :8081 2>/dev/null) 2>/dev/null || true
kill $(lsof -ti :8080 2>/dev/null) 2>/dev/null || true
sleep 2

# use SIGKILL if still running
kill -9 $(lsof -ti :8082 2>/dev/null) 2>/dev/null || true
kill -9 $(lsof -ti :8081 2>/dev/null) 2>/dev/null || true
kill -9 $(lsof -ti :8080 2>/dev/null) 2>/dev/null || true
sleep 1

echo "[2/7] Start origin, edge and router from exec JARs"
# Wie exportieren den Token, damit die gestarteten Prozesse es verwenden können.
export MINICDN_ADMIN_TOKEN="$TOKEN"

# Startet den Origin-Dienst im Hintergrund und schreibt Logs in origin.log.
(
  cd "$ROOT_DIR/origin"
  exec java -jar "$ORIGIN_JAR" --spring.profiles.active=origin > "$ORIGIN_LOG" 2>&1
) &
# Wait until health endPoint of origin is OK to use
wait_http_ok "$ORIGIN_URL/api/origin/health"

# START  [EDGE] WITH :
# -active Profile "edge"
# - Port 8081
# - Lin to origin
# - set REGION
(
  cd "$ROOT_DIR/edge"
  exec java -jar "$EDGE_JAR" \
    --spring.profiles.active=edge \
    --server.port=8081 \
    --origin.base-url="$ORIGIN_URL" \
    --edge.region="$REGION" > "$EDGE_LOG" 2>&1
) &
# WAIT UNTIL EDGE IS READY
wait_http_ok "$EDGE_URL/api/edge/health"

# Startet den Router/CDN-Dienst im Hintergrund.
(
  cd "$ROOT_DIR/router"
  exec java -jar "$ROUTER_JAR" --spring.profiles.active=cdn > "$ROUTER_LOG" 2>&1
) &
# WAit until Router is ready,
# health endpoint requires admin token, so we pass it in the header
wait_http_ok "$ROUTER_URL/api/cdn/health" -H "X-Admin-Token: $TOKEN"

echo "[3/7] Create persisted state"
# register the edge at the router
# afterwards the edge is known to the router
curl -sf -X POST -H "X-Admin-Token: $TOKEN" \
  "$ROUTER_URL/api/cdn/routing?region=$REGION&url=$EDGE_URL" >/dev/null

# PUT test file to the origin, so it can be cached at the edge.
# Diese Datei verwenden wir später, um zu checken ob nach dem Crash Daten verloren gingen.
curl -sf -X PUT -H "X-Admin-Token: $TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data "$PAYLOAD" \
  "$ORIGIN_URL/api/origin/admin/files/$FILE" >/dev/null

# Setzt eine ausreichend lange TTL für genau diese Datei auf der Edge,
# damit ein späterer Cache-MISS nicht durch reguläres Ablaufen der TTL verursacht wird.
curl -sf -X PUT -H "X-Admin-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"prefix\":\"$FILE\",\"ttlMs\":$FILE_TTL_MS}" \
  "$EDGE_URL/api/edge/admin/config/ttl" >/dev/null

echo "[4/7] Warm edge cache (MISS -> HIT)"
# Erster Zugriff: Datei sollte noch nicht im Edge-Cache liegen -> MISS.
CACHE_FIRST="$(cache_header)"

# Zweiter Zugriff: Datei sollte jetzt im Cache liegen -> HIT.
CACHE_SECOND="$(cache_header)"

echo "  before crash #1: $CACHE_FIRST"
echo "  before crash #2: $CACHE_SECOND"

# Assertions on the expected Cache behaviour
[ "$CACHE_FIRST" = "MISS" ]
[ "$CACHE_SECOND" = "HIT" ]



# Read the body before the crash, to have the expected content for later comparison.
BODY_BEFORE="$(curl -sf "$EDGE_URL/api/edge/files/$FILE")"
[ "$BODY_BEFORE" = "$PAYLOAD" ] || {
  echo "[ERROR]: not the same payload before crash"
  exit 1
}

echo "[5/7] Crash edge process with kill -9"
# Ermittelt die PID des Edge-Prozesses anhand des Ports 8081.
EDGE_PID="$(lsof -ti :8081 2>/dev/null || true)"
[ -n "$EDGE_PID" ] || {
  echo "[ERROR]: [EDGE] PID not found on port 8081"
  exit 1
}

# we simulate a real world crash by calling `kill -9` on the edge process,
kill -9 $EDGE_PID

echo "[6/7] Restart edge and measure recovery"
# Gemessen wird nur die Recovery time der Edge,
# da Origin und Router die ganze Zeit weiterlaufen.
SECONDS=0

# Startet die Edge neu mit denselben Parametern wie zuvor.
(
  cd "$ROOT_DIR/edge"
  exec java -jar "$EDGE_JAR" \
    --spring.profiles.active=edge \
    --server.port=8081 \
    --origin.base-url="$ORIGIN_URL" \
    --edge.region="$REGION" > "$EDGE_LOG" 2>&1
) &

# Wartet, bis die Edge wieder antwortet.
wait_http_ok "$EDGE_URL/api/edge/health"

# Speichert die gemessene Recovery time in Sekunden.
recovery_seconds="$SECONDS"

echo "[7/7] Verify recovery, no data loss and time "
# Wir checken nach dem Neustart den Cache-Status.
# Erwartung: weiterhin HIT, also erfolgreicher Cache-Recovery.
CACHE_AFTER="$(cache_header)"
echo "  after crash #1: $CACHE_AFTER"
[ "$CACHE_AFTER" = "HIT" ] || {
  echo "[ERROR]: cache recovery expected HIT"
  exit 1
}

# Prüft, ob der Dateinhalt nach dem Crash unverändert geblieben ist.
BODY_AFTER="$(curl -sf "$EDGE_URL/api/edge/files/$FILE")"
[ "$BODY_AFTER" = "$PAYLOAD" ] || {
  echo "[ERROR]: data loss detected"
  exit 1
}

# Recovery-Time muss unter 10 Sekunden bleiben.
[ "$recovery_seconds" -lt 10 ] || {
  echo "[ERROR]: ${recovery_seconds}s >= 10s"
  exit 1
}

# Erfolgsfall: Cache wiederhergestellt, keine Daten verloren, Zeit von x secs eingehalten.
echo "[OK]: crash recovery successful, no data loss, ${recovery_seconds}s < 10s"
