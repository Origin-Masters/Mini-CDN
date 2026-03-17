#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Lade den Token aus der Umgebungsvariable// fallback : secret-token
ADMIN_TOKEN="${MINICDN_ADMIN_TOKEN:-secret-token}"
WAIT_SECONDS="${STARTUP_WAIT_SECONDS:-90}"

echo -e "Starting [MINI-CDN] servers...\n"

port_pid() {
    lsof -t -i:"$1" 2>/dev/null | head -n 1
}

start_module_if_needed() {
    local port="$1"
    local module_name="$2"
    local profile="$3"
    local label="$4"
    local log_file="$5"
    local pid

    pid="$(port_pid "$port")"
    if [[ -n "$pid" ]]; then
        echo "$label already running on [$port] (PID: $pid)"
        return 0
    fi

    echo "Starting $label..."
    (
        cd "$ROOT_DIR" &&
        mvn -pl "$module_name" -am spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=$profile"
    ) > "$log_file" 2>&1 &
}

start_module_if_needed 8080 origin origin ORIGIN "$ROOT_DIR/origin.log"
start_module_if_needed 8081 edge edge EDGE "$ROOT_DIR/edge.log"
start_module_if_needed 8082 router cdn ROUTER "$ROOT_DIR/router.log"

echo

deadline=$((SECONDS + WAIT_SECONDS))
while (( SECONDS < deadline )); do
    if curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" http://localhost:8082/api/cdn/health > /dev/null 2>&1; then
        break
    fi
    sleep 2
done

# get the PIDs of origin, edge and router
ORIGIN_PID=$(port_pid 8080)
EDGE_PID=$(port_pid 8081)
ROUTER_PID=$(port_pid 8082)

echo -e "[ORIGIN]: 8080 (PID: $ORIGIN_PID) \n"
echo -e "[EDGE]:   8081 (PID: $EDGE_PID) \n"
echo -e "[ROUTER]: 8082 (PID: $ROUTER_PID) \n"

if [[ -z "$ROUTER_PID" ]]; then
    echo "ERROR: [ROUTER] did not start. Check $ROOT_DIR/router.log"
    exit 1
fi

if ! curl -sf -H "X-Admin-Token: $ADMIN_TOKEN" http://localhost:8082/api/cdn/health > /dev/null 2>&1; then
    echo "ERROR: [ROUTER] is listening on [8082] but health check failed. Check $ROOT_DIR/router.log"
    exit 1
fi

# Edge registrieren (hier wird der ADMIN_TOKEN verwendet)
echo -e "\nRegistering [EDGE] at [ROUTER]..."
curl -sf -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8082/api/cdn/routings?region=EU&url=http://localhost:8081" > /dev/null
echo "[EDGE] registered for region [EU]"
