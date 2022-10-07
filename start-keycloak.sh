#!/bin/bash

KEYCLOAK_PORT=8089
KEYCLOAK_ADMIN="admin"
KEYCLOAK_ADMIN_PASSWORD="admin123"

# determine "real" Java binary (Windows / Git Bash compatibility)
JAVA_BIN="$JAVA_HOME/bin/java"
if command -v cygpath.exe >/dev/null
then
  JAVA_BIN=$(cygpath "$JAVA_BIN")
fi

if [ ! -d "keycloak" ]
then
  echo "Keycloak not yet installed locally. Please run ./start-demo.sh first for one-time setup." >&2
  exit 3
fi

# start Keycloak
echo "Starting Keycloak (Port $KEYCLOAK_PORT)..."
echo "(Press Ctrl+C to stop.)"
JAVA="$JAVA_BIN" KEYCLOAK_ADMIN="$KEYCLOAK_ADMIN" KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" ./keycloak/bin/kc.sh start-dev --http-port "$KEYCLOAK_PORT" --import-realm
