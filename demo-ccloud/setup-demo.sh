#!/bin/bash

# This script works on both Windows and Linux. For Windows, you should have e.g. the Git Bash installed, which can
# run this script.

CCLOUD_CLI=./bin/confluent
YQ=./bin/yq
KEYCLOAK_DIR=../keycloak

# If you want to use a special cloud provider and/or region for cluster provisioning, please specify here.
# See https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_create.html
# for possible values.
CLUSTER_CLOUD=gcp
CLUSTER_REGION=europe-west1

# The name for the Galapagos Environment within your Confluent Cloud Organization.
# You can override it with environment variable GALA_ENV_NAME
GALAPAGOS_ENV_NAME="${GALA_ENV_NAME:-galapagos-demo}"

# Here, the configuration for the Demo will be written to.
OUTPUT_CONFIG_FILE="../application-democonf.properties"

GALA_SA_DESC="Admin Service Account for Galapagos. DO NOT DELETE."
API_KEY_DESC="Local development for user $(whoami)"

downloadConfluentCli () {
  if [ ! -f "$CCLOUD_CLI" ] && [ ! -f "$CCLOUD_CLI.exe" ]
  then
    curl -sL --http1.1 https://cnfl.io/cli | sh -s -- latest > /dev/null
  fi
}

downloadYq () {
  if [ ! -f "$YQ" ] && [ ! -f "$YQ.exe" ]
  then
    . ./install-yq.sh || exit 1
  fi
}

downloadKeycloak () {
  if [ ! -f "$KEYCLOAK_DIR/bin/kc.sh" ]
  then
    . ./install-keycloak.sh "$KEYCLOAK_DIR" || exit 1
    cp galapagos-demo-realm.json "$KEYCLOAK_DIR/data/import/"
  fi
}

existingConfluentLogin () {
  "$CCLOUD_CLI" login || exit 3
}

signupForConfluent () {
  "$CCLOUD_CLI" cloud-signup
  ERROR_CODE="$?"
  if [ "$ERROR_CODE" != "0" ]
  then
    exit 3
  fi
}

confluentLogin () {
  echo ""
  echo ""

  prompt="Select an option for using Confluent Cloud:"
  options=("I already have a Confluent Cloud account" "I want to sign up for a Confluent Cloud account (no credit card required)" "Cancel")

  PS3="$prompt "
  select opt in "${options[@]}"; do
      case "$REPLY" in
      1) existingConfluentLogin; break;;
      2) signupForConfluent; break;;
      3) exit 3;;
      *) echo "Invalid option. Try another one.";continue;;
      esac
  done

  # validate login
  "$CCLOUD_CLI" environment list > /dev/null || exit 1
}

checkServiceAccount () {
  # check if there already IS a galapagos service account; otherwise, create it
  echo "Checking for Galapagos Service Account..."
  GALA_SA_ID=$("$CCLOUD_CLI" iam service-account list -o yaml | "$YQ" -M '. | map(select(.name == "galapagos")) | .[0].id') || exit 1
  if [ "$GALA_SA_ID" == "null" ]
  then
    echo "Creating a Service Account for Galapagos..."
    GALA_SA_ID=$("$CCLOUD_CLI" iam service-account create galapagos --description "$GALA_SA_DESC" -o yaml | "$YQ" -M '.id') || exit 1

    echo "Granting OrganizationAdmin role to Galapagos Service Account..."
    "$CCLOUD_CLI" iam rbac role-binding create --role OrganizationAdmin --principal "User:$GALA_SA_ID"
  fi
}

createEnvironment () {
  echo "Checking for existing galapagos environment..."
  GALA_ENV_ID=$("$CCLOUD_CLI" environment list -o yaml | "$YQ" -M '. | map(select(.name == "'"$GALAPAGOS_ENV_NAME"'")) | .[0].id') || exit 1
  if [ "$GALA_ENV_ID" == "null" ]
  then
    echo "Creating new environment $GALAPAGOS_ENV_NAME..."
    GALA_ENV_ID=$("$CCLOUD_CLI" environment create "$GALAPAGOS_ENV_NAME" -o yaml | "$YQ" -M '.id') || exit 1
  fi
}

createCluster () {
  local CLUSTER_NAME
  local EXISTING_API_KEY

  CLUSTER_NAME="$1"

  echo "Checking if cluster $CLUSTER_NAME already exists..."
  CLUSTER_ID=$("$CCLOUD_CLI" kafka cluster list --environment "$GALA_ENV_ID" -o yaml | cn="$CLUSTER_NAME" "$YQ" -M '. | map(select(.name == env(cn))) | .[0].id') || exit 1

  if [ "$CLUSTER_ID" == "null" ]
  then
    echo "Creating cluster $CLUSTER_NAME in cloud $CLUSTER_CLOUD and region $CLUSTER_REGION..."
    CLUSTER_ID=$("$CCLOUD_CLI" kafka cluster create "$CLUSTER_NAME" --cloud "$CLUSTER_CLOUD" --region "$CLUSTER_REGION" --type basic --environment "$GALA_ENV_ID" -o yaml | "$YQ" '.id') || exit 1

    echo -n "Waiting for cluster to be provisioned..."
    while [ "$("$CCLOUD_CLI" kafka cluster describe "$CLUSTER_ID" --environment "$GALA_ENV_ID" -o yaml | "$YQ" '.status')" != "UP" ]
    do
      sleep 5
      echo -n "."
    done
    echo ""
  fi

  CLUSTER_BOOTSTRAP_SERVER="$("$CCLOUD_CLI" kafka cluster describe "$CLUSTER_ID" --environment "$GALA_ENV_ID" -o yaml | "$YQ" '.endpoint' | cut -d'/' -f3)"

  "$CCLOUD_CLI" environment use "$GALA_ENV_ID" > /dev/null
  EXISTING_API_KEY=$("$CCLOUD_CLI" api-key list --resource "$CLUSTER_ID" -o yaml | desc="$API_KEY_DESC" "$YQ" -M '. | map(select(.description == env(desc))) | .[0].key') || exit 1
  if [ "$EXISTING_API_KEY" != "null" ]
  then
    echo "DELETING existing $CLUSTER_NAME Cluster API Key $EXISTING_API_KEY..."
    "$CCLOUD_CLI" api-key delete "$EXISTING_API_KEY" || exit 1
  fi

  echo "Creating / updating service account ACLs on cluster $CLUSTER_NAME..."
  "$CCLOUD_CLI" kafka acl create \
    --service-account "$GALA_SA_ID" \
    --environment "$GALA_ENV_ID" \
    --cluster "$CLUSTER_ID" \
    --operations "describe,describe-configs" \
    --cluster-scope \
     --allow > /dev/null || exit 1
  "$CCLOUD_CLI" kafka acl create \
     --service-account "$GALA_SA_ID" \
     --environment "$GALA_ENV_ID" \
     --cluster "$CLUSTER_ID" \
     --operations "create,delete,read,alter,alter-configs,describe,describe-configs" \
     --topic '*' \
     --allow > /dev/null || exit 1
  "$CCLOUD_CLI" kafka acl create \
    --service-account "$GALA_SA_ID" \
    --environment "$GALA_ENV_ID" \
    --cluster "$CLUSTER_ID" \
    --operations "read,describe" \
    --consumer-group '*' \
     --allow > /dev/null || exit 1

  echo "Creating Cluster API Key for cluster $CLUSTER_NAME..."
  "$CCLOUD_CLI" api-key create --resource "$CLUSTER_ID" --description "$API_KEY_DESC" --service-account "$GALA_SA_ID" -o yaml > api-key.tmp.yaml || exit 1

  CLUSTER_API_KEY=$("$YQ" -M '.api_key' < api-key.tmp.yaml)
  CLUSTER_API_SECRET=$("$YQ" -M '.api_secret' < api-key.tmp.yaml)

  rm api-key.tmp.yaml
}

createCloudApiKey () {
  local EXISTING_API_KEY
  # Check if there already is an API Key; if yes, delete it
  EXISTING_API_KEY=$("$CCLOUD_CLI" api-key list --resource cloud -o yaml | desc="$API_KEY_DESC" "$YQ" -M '. | map(select(.description == env(desc))) | .[0].key') || exit 1
  if [ "$EXISTING_API_KEY" != "null" ]
  then
    echo "Will DELETE existing API key $EXISTING_API_KEY with description $API_KEY_DESC..."
    "$CCLOUD_CLI" api-key delete "$EXISTING_API_KEY" || exit 1
  fi

  echo "Creating Cloud API key for Galapagos..."
  "$CCLOUD_CLI" api-key create --resource cloud --description "$API_KEY_DESC" --service-account "$GALA_SA_ID" -o yaml > api-key.tmp.yaml || exit 1

  CLOUD_API_KEY=$("$YQ" -M '.api_key' < api-key.tmp.yaml)
  CLOUD_API_SECRET=$("$YQ" -M '.api_secret' < api-key.tmp.yaml)
  rm api-key.tmp.yaml
}

importDemoData () {
  echo "Creating known-applications topic (if not already exists)..."
  "$CCLOUD_CLI" kafka topic create galapagos.internal.known-applications --cluster "$PROD_CLUSTER_ID" --environment "$GALA_ENV_ID" --if-not-exists --partitions 1 --config "cleanup.policy=compact" || exit 1

  echo "Importing Demo data..."
  "$CCLOUD_CLI" kafka topic produce galapagos.internal.known-applications --parse-key --api-secret "$PROD_API_SECRET" \
    --cluster "$PROD_CLUSTER_ID" --environment "$GALA_ENV_ID" --api-key "$PROD_API_KEY" --delimiter '|' < demodata/known-applications.txt 2>import-data.err

  IMPORT_EXIT_CODE="$?"

  if [ "$IMPORT_EXIT_CODE" -gt 0 ]
  then
    echo "ERROR: Importing demo data failed" >&2
    cat import-data.err >&2
  fi

  rm import-data.err

  return $IMPORT_EXIT_CODE
}

writeDemoConfig () {
  echo "demo.environment-id=$GALA_ENV_ID" > "$OUTPUT_CONFIG_FILE"
  # shellcheck disable=SC2129
  echo "demo.organization-api-key=$CLOUD_API_KEY" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.organization-api-secret=$CLOUD_API_SECRET" >> "$OUTPUT_CONFIG_FILE"

  echo "demo.nonprod.cluster-id=$NONPROD_CLUSTER_ID" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.nonprod.bootstrap.servers=$NONPROD_BOOTSTRAP_SERVER" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.nonprod.cluster-api-key=$NONPROD_API_KEY" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.nonprod.cluster-api-secret=$NONPROD_API_SECRET" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.prod.cluster-id=$PROD_CLUSTER_ID" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.prod.bootstrap.servers=$PROD_BOOTSTRAP_SERVER" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.prod.cluster-api-key=$PROD_API_KEY" >> "$OUTPUT_CONFIG_FILE"
  echo "demo.prod.cluster-api-secret=$PROD_API_SECRET" >> "$OUTPUT_CONFIG_FILE"
}

echo ""
echo "Galapagos Local Demo Setup (Confluent Cloud based)"
echo ""
echo "This script will create several resources in your Confluent Cloud organization to be used by Galapagos."
echo "If you do not yet have a Confluent Cloud account, you can sign up for one during the process."
echo "No charges apply, no credit card required."
echo ""
echo "Additionally, Keycloak is downloaded and configured by this script. It will be located in subdirectory 'keycloak'."
echo ""
echo "Afterwards, local configuration files will be created, so you can run Galapagos locally for a cool demo."
echo ""

downloadConfluentCli || exit 3
downloadYq || exit 3
downloadKeycloak || exit 3
confluentLogin || exit 2
createEnvironment || exit 1
checkServiceAccount || exit 1
createCloudApiKey || exit 1
createCluster "prod" || exit 1
PROD_CLUSTER_ID="$CLUSTER_ID"
PROD_BOOTSTRAP_SERVER="$CLUSTER_BOOTSTRAP_SERVER"
PROD_API_KEY="$CLUSTER_API_KEY"
PROD_API_SECRET="$CLUSTER_API_SECRET"
createCluster "nonprod" || exit 1
NONPROD_CLUSTER_ID="$CLUSTER_ID"
NONPROD_BOOTSTRAP_SERVER="$CLUSTER_BOOTSTRAP_SERVER"
NONPROD_API_KEY="$CLUSTER_API_KEY"
NONPROD_API_SECRET="$CLUSTER_API_SECRET"
importDemoData || exit 1
writeDemoConfig || exit 1

echo "Configuration file $(basename "$OUTPUT_CONFIG_FILE") has been written."
echo ""
echo "You can now start the Galapagos Demo using ./start-demo.sh in Galapagos root directory."
echo ""
