#!/bin/bash

echo -e "Stopping [MINI-CDN] servers...\n"

# Resolve only server listeners for the port (avoid killing clients connected to that port).
pid_for_listen_port() {
	local port="$1"
	lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null
}

stop_service() {
	local name="$1"
	local port="$2"
	local pid
	pid="$(pid_for_listen_port "$port")"

	if [ -n "$pid" ]; then
		kill $pid && echo -e "[$name] stopped (PID: $pid, port: $port) \n" || echo -e "[$name] stop failed (PID: $pid) \n"
	else
		echo -e "[$name] not running (port: $port) \n"
	fi
}

stop_service "ORIGIN" 8080
stop_service "EDGE" 8081
stop_service "ROUTER" 8082

echo "Done!"
