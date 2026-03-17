#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Lade den Token aus der Umgebungsvariable// fallback : secret-token
ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"

echo -e "Starting [MINI-CDN] servers...\n"

cd "$ROOT_DIR/origin"
echo "Starting ORIGIN..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=origin" > "$ROOT_DIR/origin.log" 2>&1 &

cd "$ROOT_DIR/edge"
echo "Starting EDGE..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=edge" > "$ROOT_DIR/edge.log" 2>&1 &

cd "$ROOT_DIR/router"
echo -e "Starting ROUTER...\n\n"
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=cdn" > "$ROOT_DIR/router.log" 2>&1 &

sleep 6

# get the PIDs of origin, edge and router
ORIGIN_PID=$(lsof -t -i:8080 2>/dev/null)
EDGE_PID=$(lsof -t -i:8081 2>/dev/null)
ROUTER_PID=$(lsof -t -i:8082 2>/dev/null)

echo -e "[ORIGIN]: 8080 (PID: $ORIGIN_PID) \n"
echo -e "[EDGE]:   8081 (PID: $EDGE_PID) \n"
echo -e "[ROUTER]: 8082 (PID: $ROUTER_PID) \n"

# Warte auf den Router (hier wird der ADMIN_TOKEN verwendet)
until curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" http://localhost:8082/api/cdn/health > /dev/null 2>&1; do
    sleep 2
done

# Edge registrieren (hier wird der ADMIN_TOKEN verwendet)
echo -e "\nRegistering [EDGE] at [ROUTER]..."
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8082/api/cdn/routing?region=EU&url=http://localhost:8081" > /dev/null
echo "[EDGE] registered for region [EU]"
