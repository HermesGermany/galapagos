#!/bin/bash

# Sets up a the following, in a running K8S cluster (e.g. minikube):
#
# - A Kafka PseudoDEV instance (including Zookeeper instance)
# - A Kafka PseudoPROD instance (including Zookeeper instance)
# - A Keycloak instance with a MariaDB backend
# - Galapagos
# - Required Services, ConfigMaps, Secrets, and persistent volume claims
#

# Requirements:
# kubectl must be on path and set up to use the correct context!
# openssl on path
# JAVA_HOME must point to JDK 9 or a newer JDK


# Check this directory after running this script for CA and other interesting files
TMP_FILES_DIR="./.demofiles"

if [ ! -f "$JAVA_HOME/bin/keytool" ]
then
  echo "JAVA_HOME must be set and point to a valid directory containing JDK 9 or later."
  exit 3
fi

if ! command -v openssl &> /dev/null
then
  echo "openssl must be on PATH. If using Windows, please consider using Git Bash or Cygwin."
  exit 3
fi


JAVA_BASE_DIR="$JAVA_HOME"

JAVA_MAJOR_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | cut -d '"' -f 2 | cut -d '.' -f 1)
if [ "$JAVA_MAJOR_VERSION" -lt 9 ]
then
  echo "JAVA_HOME must be set and point to a valid directory containing JDK 9 or later."
  exit 3
fi

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

OPENSSL_CMD="openssl"
SUBJECT_PREFIX="/"

# on MinGW (e.g. Git Bash), we need slightly different syntax for openssl calls
if [ "$OSTYPE" == "msys" ]
then
  OPENSSL_CMD="winpty openssl"
  SUBJECT_PREFIX="//"
fi

mkdir -p "$TMP_FILES_DIR" || exit 1
rm -f "$TMP_FILES_DIR/*"

# Create a self-signed root certificate and key
CA_KEY_FILE="$TMP_FILES_DIR/ca.key"
CA_FILE="$TMP_FILES_DIR/ca.cer"
$OPENSSL_CMD req -x509 -nodes -newkey rsa:2048 -sha256 -keyout $CA_KEY_FILE.tmp -out $CA_FILE -subj "${SUBJECT_PREFIX}CN=Kafka DEMO CA" -days 1000 || exit 1
# convert key to unencrypted RSA key
$OPENSSL_CMD rsa -in $CA_KEY_FILE.tmp -out $CA_KEY_FILE || exit 1
rm $CA_KEY_FILE.tmp

# Prepare globals
KEYCLOAK_URL=""

# Create Galapagos Service first, to determine final URL
# The URL is required for the Keycloak configuration (redirect URI)
echo ""
echo "===== Preparing Galapagos Service and URL ====="
kubectl create namespace galapagos || exit 1
kubectl apply -f galapagos-demo-service.yml --namespace galapagos
GALAPAGOS_PORT=$(kubectl get svc --namespace galapagos | grep galapagos-service | tr -s ' ' | cut -d ' ' -f 5 | cut -d '/' -f 1 | cut -d ':' -f 2)
GALAPAGOS_URL="http://$K8S_IP:$GALAPAGOS_PORT"

createKeycloak () {
  local K8S_NAMESPACE=keycloak
  
  echo ""
  echo "===== Creating Keycloak instance =====" 

  kubectl create namespace "$K8S_NAMESPACE"

  cat galapagos-demo-realm.json | sed "s%<GALAPAGOS_URL>%$GALAPAGOS_URL%g" > $TMP_FILES_DIR/galapagos-dev-realm.json
  
  kubectl create configmap keycloak-config --from-file=$TMP_FILES_DIR/galapagos-dev-realm.json --namespace "$K8S_NAMESPACE"
  
  kubectl apply -f keycloak-service.yml --namespace "$K8S_NAMESPACE"

  local PORT
  PORT=$(kubectl get svc --namespace "$K8S_NAMESPACE" | grep keycloak-service | tr -s ' ' | cut -d ' ' -f 5 | cut -d '/' -f 1 | cut -d ':' -f 2)
  KEYCLOAK_URL="http://$K8S_IP:$PORT"
  
  # we could use this URL to specify a frontend URL for keycloak, but if not set, Keycloak
  # derives it from request URL, which is fine for us.
  
  kubectl apply -f keycloak.yml --namespace "$K8S_NAMESPACE"
  
  cat > "$TMP_FILES_DIR/keycloak.json" <<EOF
{
  "auth-server-url":"$KEYCLOAK_URL/auth",
  "realm":"galapagos",
  "resource":"galapagos-webapp-dev",
  "public-client": true,
  "use-resource-role-mappings": true,
  "principal-attribute": "preferred_username"
}
EOF
}

createKafkaCluster () {
  local ENV_NAME=$1
  local K8S_NAMESPACE=kafka-pseudo${ENV_NAME}

  echo ""
  echo "===== Creating Kafka $ENV_NAME cluster ====="
  
  # create namespace
  kubectl create namespace "$K8S_NAMESPACE" || exit 1
  
  # create certificates
  echo "Generating new certificates for cluster..."
  $OPENSSL_CMD req -nodes -new -newkey rsa:2048 -sha256 -keyout "$TMP_FILES_DIR/server.key" -out "$TMP_FILES_DIR/csr.pem" -subj "${SUBJECT_PREFIX}CN=${K8S_NAMESPACE},OU=AIS"
  $OPENSSL_CMD x509 -req -days 360 -in "$TMP_FILES_DIR/csr.pem" -CA $CA_FILE -CAkey $CA_KEY_FILE -CAcreateserial -out "$TMP_FILES_DIR/server.cer" -sha256

  # remove files which would otherwise cause keytool errors
  rm -f "$TMP_FILES_DIR/truststore.jks"
  rm -f "$TMP_FILES_DIR/server.jks"
  "$JAVA_BASE_DIR/bin/keytool" -import -trustcacerts -noprompt -alias ca -file $CA_FILE -keystore "$TMP_FILES_DIR/truststore.jks" -storepass changeit -deststoretype JKS
  $OPENSSL_CMD pkcs12 -export -in "$TMP_FILES_DIR/server.cer" -inkey "$TMP_FILES_DIR/server.key" -out "$TMP_FILES_DIR/server.p12" -name server -CAfile $CA_FILE -caname root -password pass:changeit
  "$JAVA_BASE_DIR/bin/keytool" -importkeystore -deststorepass changeit -destkeypass changeit -destkeystore "$TMP_FILES_DIR/server.jks" -deststoretype JKS -srckeystore "$TMP_FILES_DIR/server.p12" -srcstoretype PKCS12 -srcstorepass changeit -alias server
  
  echo "changeit" > "$TMP_FILES_DIR/keystorepass"

  kubectl create secret generic kafka-server-certs --from-file="$TMP_FILES_DIR/truststore.jks" --from-file="$TMP_FILES_DIR/server.jks" --from-file="$TMP_FILES_DIR/keystorepass" --namespace "$K8S_NAMESPACE"

  # create zookeeper
  local ZOOKEEPER_OUTPUT
  ZOOKEEPER_OUTPUT=$(kubectl apply -f zookeeper.yml --namespace "$K8S_NAMESPACE")
  if [[ ! "$ZOOKEEPER_OUTPUT" =~ "zookeeper-service unchanged" ]]
  then
    echo "Waiting for Zookeeper to be up (30s)..."
    sleep 30s
  fi
  
  # Create and expose Kafka Service first, to get external port
  kubectl apply -f kafka-service.yml --namespace "$K8S_NAMESPACE"

  # get port
  local PORT
  PORT=$(kubectl get svc --namespace "$K8S_NAMESPACE" | grep kafka-service | tr -s ' ' | cut -d ' ' -f 5 | cut -d '/' -f 1 | cut -d ':' -f 2)
  
  # create Kafka ConfigMap on the fly, now that we have external address
  kubectl create configmap kafka-config "--from-literal=advertised.listeners=SSL://$K8S_IP:$PORT,SASL_PLAINTEXT://127.0.0.1:29092" --namespace "$K8S_NAMESPACE"
    
  # create Kafka
  kubectl apply -f kafka.yml --namespace "$K8S_NAMESPACE"
}

createGalapagos() {
  local K8S_NAMESPACE=galapagos

  echo ""
  echo "===== Creating Galapagos instance ====="

  kubectl create configmap spring-properties --from-file="application-demo.properties" --from-file="$TMP_FILES_DIR/keycloak.json" --namespace "$K8S_NAMESPACE"
  kubectl create secret generic kafka-ca --from-file="$CA_FILE" --from-file="$CA_KEY_FILE" --namespace "$K8S_NAMESPACE"

  kubectl apply -f galapagos-demo.yml --namespace "$K8S_NAMESPACE"
}

importDemoApplications() {
  local K8S_NAMESPACE=galapagos

  echo ""
  echo "===== Importing demo applications ====="

  # This can be removed in a later version. Currently, it is the best try to avoid a racing condition for
  # creating the Galapagos Metadata Topics in Kafka.
  sleep 10s

  kubectl create configmap demo-applications --from-file="demo-applications.json" --namespace "$K8S_NAMESPACE"
  kubectl apply -f import-demo-data.yml --namespace "$K8S_NAMESPACE"
}

createKeycloak
createKafkaCluster "dev"
createKafkaCluster "prod"
createGalapagos
importDemoApplications

echo ""
echo ""
echo "*******************************************************************************"
echo ""
echo "Galapagos DEMO Setup has been initialized successfully"
echo '(please check above output for errors).'
echo ""
echo "You can determine the status of the Demo initialization with"
echo "./demo-status.sh"
echo ""
echo "Please open $GALAPAGOS_URL to open Galapagos once Setup is done."
echo "Note that Kafka and Keycloak may take several MINUTES to start up;"
echo "Galapagos will not be accessible before all services are up and running."
echo ""
echo "Galapagos preconfigured users: admin1/admin1 and user1/user1"
echo ""
echo "You can access the Keycloak instance using $KEYCLOAK_URL"
echo "Keycloak admin user: admin/admin"
echo ""
echo "*******************************************************************************"
echo ""
