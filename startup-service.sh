#!/bin/bash

echo -e "Starting [MINI-CDN] servers...\n"

cd origin
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=origin" > ../origin.log 2>&1 &
cd ..

cd edge
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=edge" > ../edge.log 2>&1 &
cd ..

sleep 5

# get the PIDs of origin and edge
ORIGIN_PID=$(lsof -t -i:8080 2>/dev/null)
EDGE_PID=$(lsof -t -i:8081 2>/dev/null)

echo -e "[ORIGIN]: 8080 (PID: $ORIGIN_PID) \n"
echo -e "[EDGE]:   8081 (PID: $EDGE_PID) \n"

