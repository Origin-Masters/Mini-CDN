#!/bin/bash

echo -e "Starting [MINI-CDN] servers...\n"

cd origin
echo "Starting ORIGIN..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=origin" > ../origin.log 2>&1 &
cd ..

cd edge
echo "Starting EDGE..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=edge" > ../edge.log 2>&1 &
cd ..

cd router
echo -e "Starting ROUTER...\n\n"
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=cdn" > ../router.log 2>&1 &
cd ..

sleep 6

# get the PIDs of origin, edge and router
ORIGIN_PID=$(lsof -t -i:8080 2>/dev/null)
EDGE_PID=$(lsof -t -i:8081 2>/dev/null)
ROUTER_PID=$(lsof -t -i:8082 2>/dev/null)

echo -e "[ORIGIN]: 8080 (PID: $ORIGIN_PID) \n"
echo -e "[EDGE]:   8081 (PID: $EDGE_PID) \n"
echo -e "[ROUTER]: 8082 (PID: $ROUTER_PID) \n"


until curl -sf -H "X-Admin-Token: secret-token" http://localhost:8082/api/cdn/health > /dev/null 2>&1; do
    sleep 2
done

# Edge registrieren
echo -e "\nRegistering [EDGE] at [ROUTER]..."
curl -sf -X POST -H "X-Admin-Token: secret-token" \
  "http://localhost:8082/api/cdn/routing?region=EU&url=http://localhost:8081" > /dev/null
echo "[EDGE] registered for region [EU]"