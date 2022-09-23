#!/bin/bash

KEYCLOAK_OUTPUT_DIR="$1"
if [ "$KEYCLOAK_OUTPUT_DIR" == "" ]
then
  KEYCLOAK_OUTPUT_DIR="../keycloak"
fi

KEYCLOAK_VERSION="19.0.2"
DOWNLOAD_URL="https://github.com/keycloak/keycloak/releases/download/$KEYCLOAK_VERSION/keycloak-$KEYCLOAK_VERSION.tar.gz"

rm -rf "$KEYCLOAK_OUTPUT_DIR"

echo "Downloading Keycloak $KEYCLOAK_VERSION..."
curl -sLSf "$DOWNLOAD_URL" > "./keycloak.tar.gz"
tar xzf keycloak.tar.gz
rm keycloak.tar.gz
mv "keycloak-$KEYCLOAK_VERSION" "$KEYCLOAK_OUTPUT_DIR"

mkdir -p "$KEYCLOAK_OUTPUT_DIR/data/import"
