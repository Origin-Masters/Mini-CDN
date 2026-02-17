#!/bin/bash

echo -e "Stopping [MINI-CDN] servers...\n"

# determine the PIDs to kill
ORIGIN_PID=$(lsof -t -i:8080 2>/dev/null)
EDGE_PID=$(lsof -t -i:8081 2>/dev/null)
ROUTER_PID=$(lsof -t -i:8082 2>/dev/null)

[ ! -z "$ORIGIN_PID" ] && kill $ORIGIN_PID && echo -e "[ORIGIN] stopped (PID: $ORIGIN_PID) \n" || echo -e "[ORIGIN] not running \n"
[ ! -z "$EDGE_PID" ] && kill $EDGE_PID && echo -e "[EDGE] stopped (PID: $EDGE_PID) \n" || echo -e "[EDGE] not running \n"
[ ! -z "$ROUTER_PID" ] && kill $ROUTER_PID && echo -e "[ROUTER] stopped (PID: $ROUTER_PID) \n" || echo -e "[ROUTER] not running \n"

echo "Done!"
