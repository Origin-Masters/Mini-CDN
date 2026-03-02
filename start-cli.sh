#!/bin/bash

echo "Checking ROUTER availability..."
if ! curl -sf http://localhost:8082/api/cdn/health > /dev/null 2>&1; then
    echo -e "ERROR: [ROUTER] is not running on [8082] \n Start servers first with : ./start-servers.sh"
    exit 1
fi

echo -e "[ROUTER] is up. Starting [MINI-CDN CLI]...\n"
cd cli
mvn -q exec:java
