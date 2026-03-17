#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "Checking ROUTER availability..."
if ! curl -sf http://localhost:8082/api/cdn/health > /dev/null 2>&1; then
    echo -e "ERROR: [ROUTER] is not running on [8082] \n Start servers first with : ./scripts/runtime/startup-service.sh"
    exit 1
fi

echo -e "[ROUTER] is up. Starting [MINI-CDN CLI]...\n"
cd "$ROOT_DIR/cli"
mvn -q exec:java
