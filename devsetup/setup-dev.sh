#!/bin/bash

# Sets up a the following, in a running K8S cluster (e.g. minikube):
#
# - A Kafka PseudoDEV instance
# - A Kafka PseudoPROD instance
# - A Keycloak instance with a MariaDB backend
#
# Also, required certificates and config files for developing Galapagos are created / updated.
#

# Requirements:
# kubectl must be on path and setup to use the correct context!
# openssl on path
# JAVA_HOME must point to JDK 9 or a newer JDK


# This file will receive the Kafka server properties and Keycloak config file location.
SPRING_PROP_FILE=../application-local.properties

# This file will receive Keycloak settings (relative to SPRING_PROP_FILE!)
KEYCLOAK_JSON_FILE=keycloak-local.json

# These files will receive auto-generated self-signed CA certificate and key.
CA_FILE=../ca.cer
CA_KEY_FILE=../ca.key


if [ ! -f "$JAVA_HOME/bin/keytool" ]
then
  echo "JAVA_HOME must be set and point to a valid directory containing JDK 9 or later."
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


# Create a self-signed root certificate and key
if [ ! -f $CA_FILE ]
then
  $OPENSSL_CMD req -x509 -nodes -newkey rsa:2048 -sha256 -keyout $CA_KEY_FILE.tmp -out $CA_FILE -subj "${SUBJECT_PREFIX}CN=Kafka DEV CA" -days 1000 || exit 1
  # convert key to unencrypted RSA key
  $OPENSSL_CMD rsa -in $CA_KEY_FILE.tmp -out $CA_KEY_FILE || exit 1
  rm $CA_KEY_FILE.tmp
  
  $OPENSSL_CMD req -nodes -new -newkey rsa:2048 -sha256 -keyout ../local-admin-client.key -out csr.pem -subj "${SUBJECT_PREFIX}CN=kafkaadmin"
  $OPENSSL_CMD x509 -req -days 360 -in csr.pem -CA $CA_FILE -CAkey $CA_KEY_FILE -CAcreateserial -out ../local-admin-client.cer -sha256
  $OPENSSL_CMD pkcs12 -export -out ../local-admin-client.p12 -in ../local-admin-client.cer -inkey ../local-admin-client.key -password pass:changeit
  rm csr.pem
  echo "Created a local client certificate with admin rights for use with Kafka tools in ../local-admin-client.p12 (passwort: changeit)"
fi

echo "" > $SPRING_PROP_FILE

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

createKafkaCluster () {
  local ENV_NAME=$1
  local K8S_NAMESPACE=kafka-pseudo${ENV_NAME}

  echo ""
  echo "===== Creating Kafka $ENV_NAME cluster =====" 
  
  # create namespaces, if not existing
  kubectl create namespace "$K8S_NAMESPACE" 2>/dev/null
  
  # create certificates
  kubectl get secret kafka-server-certs --namespace "$K8S_NAMESPACE" >/dev/null 2>/dev/null
  local CERT_EXISTS=$?
  if [ "$CERT_EXISTS" != "0" ]
  then
    echo "Generating new certificates for cluster..." 
    $OPENSSL_CMD req -nodes -new -newkey rsa:2048 -sha256 -keyout server.key -out csr.pem -subj "${SUBJECT_PREFIX}CN=${K8S_NAMESPACE},OU=AIS"
    $OPENSSL_CMD x509 -req -days 360 -in csr.pem -CA $CA_FILE -CAkey $CA_KEY_FILE -CAcreateserial -out server.cer -sha256

    "$JAVA_BASE_DIR/bin/keytool" -import -trustcacerts -noprompt -alias ca -file $CA_FILE -keystore truststore.jks -storepass changeit
    $OPENSSL_CMD pkcs12 -export -in server.cer -inkey server.key -out server.p12 -name server -CAfile $CA_FILE -caname root -password pass:changeit
    "$JAVA_BASE_DIR/bin/keytool" -importkeystore -deststorepass changeit -destkeypass changeit -destkeystore server.jks -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass changeit -alias server
  
    echo "changeit" > keystorepass

    kubectl create secret generic kafka-server-certs --from-file=truststore.jks --from-file=server.jks --from-file=keystorepass --namespace "$K8S_NAMESPACE"

    rm server.key
    rm server.cer
    rm server.p12
    rm server.jks
    rm csr.pem
    rm truststore.jks
    rm keystorepass
  fi
  
  # create zookeeper
  local ZOOKEEPER_OUTPUT
  ZOOKEEPER_OUTPUT=$(kubectl apply -f zookeeper-dev.yml --namespace "$K8S_NAMESPACE")
  if [[ ! "$ZOOKEEPER_OUTPUT" =~ "zookeeper-service unchanged" ]]
  then
    echo "Waiting for Zookeeper to be up (30s)..."
    sleep 30s
  fi
  
  # Create and expose Kafka Service first, to get external port
  kubectl apply -f kafka-service-dev.yml --namespace "$K8S_NAMESPACE"

  # get port
  local PORT
  PORT=$(kubectl get svc --namespace "$K8S_NAMESPACE" | grep kafka-service | tr -s ' ' | cut -d ' ' -f 5 | cut -d '/' -f 1 | cut -d ':' -f 2)
  
  # create Kafka ConfigMap on the fly, now that we have external address
  kubectl create configmap kafka-config "--from-literal=advertised.listeners=SSL://$K8S_IP:$PORT,SASL_PLAINTEXT://127.0.0.1:29092" --namespace "$K8S_NAMESPACE" 2>/dev/null
    
  # create Kafka
  kubectl apply -f kafka-dev.yml --namespace "$K8S_NAMESPACE"
  
  echo ""
  echo ""
  echo "===================================================================================="
  echo "Kafka Cluster $ENV_NAME created. You can connect using $K8S_IP:$PORT"
  echo "===================================================================================="
  echo ""
  echo ""
  
  echo "galapagos.dev.${ENV_NAME}Environment.servers=$K8S_IP:$PORT" >> $SPRING_PROP_FILE
}

createKeycloak
createKafkaCluster "dev"
createKafkaCluster "prod"

echo "Done. $SPRING_PROP_FILE has been created / updated with appropriate properties for Galapagos."
echo ""
echo "For using OAuth2 with Insomnia or another REST client, use"
echo "${KEYCLOAK_URL}/auth/realms/galapagos/protocol/openid-connect/auth"
echo "${KEYCLOAK_URL}/auth/realms/galapagos/protocol/openid-connect/token"
echo "as the Auth and Token endpoints."
echo ""
