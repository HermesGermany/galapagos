#!/bin/bash

# Sets up a the following, in a running K8S cluster (e.g. minikube):
#
# - A Keycloak instance with a MariaDB backend
#
# And the following in a Confluent Cloud Organization:
#
# - An environment for Galapagos
# - Two Cloud Kafka clusters for Galapagos
# - Required rolebindings and API Keys
#
# Also, required config files for developing / testing Galapagos are created / updated.
#

# Requirements:
# curl must be installed
# kubectl must be on path and setup to use the correct context!

# An environment with this name will be created in your Confluent Cloud Organization
CCLOUD_ENV_NAME=galapagos-dev

# This cloud provider and region will be used. Please note that regions have different names for AWS and GCP!
CCLOUD_CLOUD=gcp
CCLOUD_REGION=europe-west1

# Version of official CCloud CLI tool to use
CCLOUD_VERSION=1.42.0
CCLOUD_PATH=./ccloud
CCLOUD_BIN="$CCLOUD_PATH/ccloud"

# This file will receive the Kafka server properties and Keycloak config file location.
SPRING_PROP_FILE=../application-local.properties

# This file will receive Keycloak settings (relative to SPRING_PROP_FILE!)
KEYCLOAK_JSON_FILE=keycloak-local.json

# ------ Start of Script ------

checkKubectl () {
  # Check that kubectl is set up correctly
  echo "Checking kubectl..."
  kubectl describe nodes >/dev/null || (echo "Kubectl not set up properly, or pointing to a context with insufficient rights."; exit 1)

  if [ ! -f ./kube-ip.conf ]
  then
    echo "No kube-ip.conf found, trying to determine K8S Cluster IP from kubectl config..."
    CONTEXT_NAME=$(kubectl config current-context)
    FOUND_IP=$(kubectl config view | grep -A3 -B3 "$CONTEXT_NAME" | grep "server" | grep -o -P '(?<=\/\/)(([0-9]{1,3}\.){3}[0-9]{1,3})(?=:)' 2>/dev/null)
    if [ "$FOUND_IP" == "" ]
    then
      echo "K8S_IP=" > ./kube-ip.conf
      echo "No IP found! Please open ./kube-ip.conf and add the IP address of your Kubernetes Cluster."
      exit 3
    fi
    echo "Server IP found: $FOUND_IP"
    K8S_IP=$FOUND_IP
    echo "K8S_IP=$FOUND_IP" > ./kube-ip.conf
  else
    . ./kube-ip.conf
  fi

  if [ "$K8S_IP" == "" ]
  then
    echo "Please configure the IP address of the K8S cluster first!"
    echo "Edit ./kube-ip.conf for this."
    exit 3
  fi
}

createKeycloak () {
  local K8S_NAMESPACE=keycloak

  echo ""
  echo "===== Creating Keycloak instance =====" 

  kubectl create namespace "$K8S_NAMESPACE" 2>/dev/null
  
  kubectl create configmap keycloak-config --from-file=./galapagos-dev-realm.json --namespace "$K8S_NAMESPACE" 2>/dev/null
  
  kubectl apply -f keycloak-service-dev.yml --namespace "$K8S_NAMESPACE"

  local PORT
  PORT=$(kubectl get svc --namespace "$K8S_NAMESPACE" | grep keycloak-service | tr -s ' ' | cut -d ' ' -f 5 | cut -d '/' -f 1 | cut -d ':' -f 2)
  KEYCLOAK_URL="http://$K8S_IP:$PORT"
  
  # we could use this URL to specify a frontend URL for keycloak, but if not set, Keycloak
  # derives it from request URL, which is fine for us.
  
  kubectl apply -f keycloak-dev.yml --namespace "$K8S_NAMESPACE"
  
  local SPRING_PROP_PATH
  SPRING_PROP_PATH=$(dirname $SPRING_PROP_FILE)
  
  cat > "$SPRING_PROP_PATH/$KEYCLOAK_JSON_FILE" <<EOF
{
  "auth-server-url":"$KEYCLOAK_URL/auth",
  "realm":"galapagos",
  "resource":"galapagos-webapp-dev",
  "public-client": true,
  "use-resource-role-mappings": true,
  "principal-attribute": "preferred_username"
}
EOF

  echo "keycloak.configurationFile=file:./$KEYCLOAK_JSON_FILE" >> $SPRING_PROP_FILE
  
  echo ""
  echo ""
  echo "===================================================================================="
  echo "Keycloak instance created. You can open the admin console using $KEYCLOAK_URL"
  echo "Note that Keycloak may take up to some minutes (!) to start up."
  echo "===================================================================================="
  echo ""
  echo ""
}

downloadCCloud () {
  if [ ! -f "$CCLOUD_BIN" ] && [ ! -f "$CCLOUD_BIN.exe" ]
  then
    echo "Downloading CCloud CLI... (if download fails e.g. due to proxy, please store ccloud executable in devsetup/ccloud folder)"
    curl -sL --http1.1 https://cnfl.io/ccloud-cli | sh -s -- -b "$CCLOUD_PATH" "v$CCLOUD_VERSION" || exit 1
  fi
}

ccloudLogin () {
  echo ""
  echo "Please enter the e-mail address of a Confluent Cloud OrgAdmin user (will be stored in $SPRING_PROP_FILE):"
  read -r CCLOUD_EMAIL
  echo "Please enter the password for the user (will be stored in $SPRING_PROP_FILE):"
  read -r -s CCLOUD_PASSWORD

  # Perform test login
  export CCLOUD_EMAIL="$CCLOUD_EMAIL"
  export CCLOUD_PASSWORD="$CCLOUD_PASSWORD"
  echo "Logging in..."
  "$CCLOUD_BIN" login || exit 1

  echo "params.ccloudUserName=$CCLOUD_EMAIL" >> "$SPRING_PROP_FILE"
  echo "params.ccloudPassword=$CCLOUD_PASSWORD" >> "$SPRING_PROP_FILE"
}

ccloudCreateEnvironment () {
  echo ""
  echo "Creating Environment $CCLOUD_ENV_NAME in your Confluent Cloud Organization..."
  CCLOUD_ENV_ID=$("$CCLOUD_BIN" environment create "$CCLOUD_ENV_NAME" -o yaml | grep "id: " | cut -d' ' -f2) || exit 1
  echo "params.ccloudEnvironmentId=$CCLOUD_ENV_ID" >> "$SPRING_PROP_FILE"
}

ccloudCreateCluster () {
  local CLUSTER_NAME="$1"
  local STAGE_ID="$2"
  echo ""
  echo "Creating Cluster $CLUSTER_NAME in Environment $CCLOUD_ENV_NAME..."
  "$CCLOUD_BIN" kafka cluster create "$CLUSTER_NAME"  --environment "$CCLOUD_ENV_ID" --cloud "$CCLOUD_CLOUD" --region "$CCLOUD_REGION" --type basic --output yaml > "$STAGE_ID.yaml" || exit 1

  local CLUSTER_ID
  local BOOTSTRAP_SERVER
  CLUSTER_ID=$(grep "id: " < "$STAGE_ID.yaml" | cut -d' ' -f2)
  BOOTSTRAP_SERVER=$(grep "endpoint: SASL_SSL://" < "$STAGE_ID.yaml" | cut -d'/' -f3)
  rm "$STAGE_ID.yaml"

  printf -v "CCLOUD_${STAGE_ID}_CLUSTER_ID" '%s' "$CLUSTER_ID"

  echo "params.$STAGE_ID.clusterId=$CLUSTER_ID" >> "$SPRING_PROP_FILE"
  echo "params.$STAGE_ID.bootstrapServer=$BOOTSTRAP_SERVER" >> "$SPRING_PROP_FILE"

  echo ""
  echo "Creating API Key for Galapagos connection to Cluster $CLUSTER_NAME..."
  "$CCLOUD_BIN" api-key create --environment "$CCLOUD_ENV_ID" --resource "$CLUSTER_ID" --description "Galapagos master key. Auto-generated by devsetup script." -o yaml > "$STAGE_ID.yaml" || exit 1

  local API_KEY
  local API_SECRET
  API_KEY=$(grep "key: " < "$STAGE_ID.yaml" | cut -d' ' -f2)
  API_SECRET=$(grep "secret: " < "$STAGE_ID.yaml" | cut -d' ' -f2)

  printf -v "CCLOUD_${STAGE_ID}_API_KEY" '%s' "$API_KEY"
  printf -v "CCLOUD_${STAGE_ID}_API_SECRET" '%s' "$API_SECRET"

  rm "$STAGE_ID.yaml"

  echo "params.$STAGE_ID.apiKey=$API_KEY" >> "$SPRING_PROP_FILE"
  echo "params.$STAGE_ID.apiSecret=$API_SECRET" >> "$SPRING_PROP_FILE"
  echo ""
  echo "Cluster $CLUSTER_NAME created successfully."
}

ccloudImportDemoApplications () {
  echo ""

  CLUSTER_READY=1
  TRY_COUNTER=6
  while [ "$CLUSTER_READY" != "0" ] && [ "$TRY_COUNTER" -gt 0 ]
  do
    echo "Waiting for PROD Cluster to get ready (60s)..."
    sleep 60
    echo "Trying to create topic for demo applications..."
    "$CCLOUD_BIN" kafka topic create "galapagos.internal.known-applications" --partitions 2 --config "cleanup.strategy=compact" \
      --environment "$CCLOUD_ENV_ID" --cluster "$CCLOUD_prod_CLUSTER_ID" 2>/dev/null
    CLUSTER_READY=$?
    TRY_COUNTER=$((TRY_COUNTER-1))
  done

  echo "Importing demo applications into cluster prod_stage..."

  "$CCLOUD_BIN" kafka topic produce "galapagos.internal.known-applications" --delimiter '|' --value-format string \
    --parse-key --environment "$CCLOUD_ENV_ID" --cluster "$CCLOUD_prod_CLUSTER_ID" --api-key "$CCLOUD_prod_API_KEY" \
    --api-secret "$CCLOUD_prod_API_SECRET" < demo-applications.key-and-json || exit 1
}

# Here is the "main script"

echo "" > $SPRING_PROP_FILE

checkKubectl || exit 1
createKeycloak || exit 1
downloadCCloud || exit 1
ccloudLogin || exit 1
ccloudCreateEnvironment || exit 1
ccloudCreateCluster dev_stage dev || exit 1
ccloudCreateCluster prod_stage prod || exit 1
ccloudImportDemoApplications || exit 1

echo ""
echo "Done. $SPRING_PROP_FILE has been created / updated with appropriate properties for Galapagos."
echo ""
echo "For using OAuth2 with Insomnia or another REST client, use"
echo "${KEYCLOAK_URL}/auth/realms/galapagos/protocol/openid-connect/auth"
echo "${KEYCLOAK_URL}/auth/realms/galapagos/protocol/openid-connect/token"
echo "as the Auth and Token endpoints (e.g. for testing Galapagos REST API)."
echo ""
echo ""
echo "Success! To compile, build & run Galapagos, use the start-galapagos.sh script in the project root."
echo ""
