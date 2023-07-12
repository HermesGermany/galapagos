#!/bin/bash

KEYCLOAK_ADMIN="admin"
KEYCLOAK_ADMIN_PASSWORD="admin123"

if [ "$JAVA_HOME" == "" ]
then
  echo "JAVA_HOME is not set. Please set JAVA_HOME to point to a local JDK installation."
  exit 3
fi

# determine "real" Java binary (Windows / Git Bash compatibility)
JAVA_BIN="$JAVA_HOME/bin/java"
if command -v cygpath.exe >/dev/null
then
  JAVA_BIN=$(cygpath "$JAVA_BIN")
fi

if [ ! -d "keycloak" ] || [ ! -f application-democonf.properties ]
then
  echo "One-Time demo setup has to be run first. Press any key to run it now, or Ctrl+C to exit."
  read -r -n 1
  cd demo-ccloud || exit 2
  ./setup-demo.sh || exit 2
  cd .. || exit 2
fi

# start Keycloak
echo "Starting Keycloak (background task)..."
KEYCLOAK_PID=$(JAVA="$JAVA_BIN" KEYCLOAK_ADMIN="$KEYCLOAK_ADMIN" KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ./keycloak/bin/kc.sh start-dev --http-port 8089 --import-realm > keycloak.log & echo $!)
echo "Keycloak started with PID $KEYCLOAK_PID"

echo "Starting Galapagos (via Maven). Stop the application any time with Ctrl+C."
echo ""
echo "Use user1/user1 or admin1/admin1 for login at http://localhost:8080, once the application runs."
echo ""
KEYCLOAK_URL=http://localhost:8089 KEYCLOAK_CLIENT_ID=galapagos-webapp-dev ./mvnw package spring-boot:run -DskipTests -Dspring-boot.run.profiles=democonf,demo,oauth2,actuator

echo "Shutting down Keycloak..."
kill "$KEYCLOAK_PID" 2>/dev/null
