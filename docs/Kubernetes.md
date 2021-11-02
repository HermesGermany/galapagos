# Galapagos on Kubernetes

For production use, it is recommended to run Galapagos on **Kubernetes**. You should use one of the official Docker
images available at [DockerHub](https://hub.docker.com/r/hermesgermany/galapagos/tags?ordering=name).

Usually, you would setup the following elements in Kubernetes for running Galapagos:

* The Galapagos _Deployment_,
* A Galapagos _Service_,
* An _Ingress_ to make Galapagos accessible from outside Kubernetes,
* A _ConfigMap_ containing your specific Galapagos configuration properties,
* A _Secret_ for secret properties,
* Optionally, a _Secret_ containing the Galapagos intermediate CAs for generating certificates.

## Example YAMLs

### Galapagos Deployment

The following example expects these objects to exist:

* A _ConfigMap_ named `spring-properties`, containing an `application-prod.properties`, a `keycloak.json`, and
  a `logback-spring.xml`
* A _Secret_ named `spring-secret-properties`, containing only the key `SMTP_PASSWORD` with the SMTP password for the
  SMTP server configured via the `application-prod.properties`
* Another _Secret_ named `kafka-ca`, containing the public certificates & private keys of the Intermediate CAs, so
  Galapagos can create client certificates for applications. Note that this is not required when using **Confluent
  Cloud** for managing your Kafka Clusters.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: galapagos-deployment
  labels:
    app: galapagos
spec:
  replicas: 2
  revisionHistoryLimit: 1
  strategy:
    type: RollingUpdate
  selector:
    matchLabels:
      app: galapagos
  template:
    metadata:
      labels:
        app: galapagos
    spec:
      containers:
        - name: galapagos-container
          image: hermesgermany/galapagos:2.1.0
          resources:
            limits:
              cpu: "1"
              memory: "900Mi"
            requests:
              cpu: "0.5"
              memory: "300Mi"
          env:
            - name: SMTP_PASSWORD
              valueFrom:
                secretKeyRef:
                  # A Secret containing secret properties for direct use in this YAML.
                  name: spring-secret-properties
                  key: SMTP_PASSWORD
            - name: SPRING_CONFIG_ADDITIONAL-LOCATION
              # In this directory, we will mount the ConfigMap with the application properties.
              value: "file:///appconfig/"
            - name: JAVA_TOOL_OPTIONS
              # This way, you can specify your custom Logback configuration for logging.
              value: "-Dlogging.config=file:///appconfig/logback-spring.xml"
          args:
            # actuator is required to have the Galapagos version number available via Spring Boot Actuator endpoint.
            - "--spring.profiles.active=prod,actuator"
            - "--spring.mail.password=$(SMTP_PASSWORD)"
          livenessProbe:
            httpGet:
              path: /actuator/info
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 30
          volumeMounts:
            # If you use certificate-based authentication, mount your Intermediate CAs here 
            - name: ca
              mountPath: "/tmp/ca"
              readOnly: true
            - name: spring-config
              mountPath: "/appconfig"
              readOnly: true
      volumes:
        - name: ca
          secret:
            # This secret contains the Intermediate CAs for Galapagos. They will be referenced from the app properties.
            secretName: kafka-ca
        - name: spring-config
          configMap:
            name: spring-properties
```

### Example application properties

An `application-prod.properties` could look like this:

```properties
keycloak.configurationFile=file:///appconfig/keycloak.json

spring.mail.host=smtp.my.company.tld.domain
spring.mail.port=25
spring.mail.username=mailuser
spring.mail.password=<provided via commandline>
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.connectiontimeout=5000

galapagos.mail.sender=Galapagos <galapagos@my.company.tld.domain>
galapagos.mail.admin-recipients=galapagos-admins@my.company.tld.domain

# First Kafka Cluster: An on-prem hosted one, client-certificate based authentication.
galapagos.kafka.environments[0].id=devtest
galapagos.kafka.environments[0].name=DEV/TEST
galapagos.kafka.environments[0].bootstrap-servers=first.kafkabroker.test.internal:9092,second.kafkabroker.test.internal:9092
galapagos.kafka.environments[0].authenticationMode=certificates
# This refers to the mounted secret!
galapagos.kafka.environments[0].certificates.caCertificateFile=file:/tmp/ca/kafka_dev_ca.cer
galapagos.kafka.environments[0].certificates.caKeyFile=file:/tmp/ca/kafka_dev_ca.key
galapagos.kafka.environments[0].certificates.applicationCertificateValidity=P730D
galapagos.kafka.environments[0].certificates.developerCertificateValidity=P90D
# Galapagos will generate a client certificate with this DN on the fly. Your Kafka Cluster should be configured to accept
# this user name as a cluster admin with unlimited access.
galapagos.kafka.environments[0].certificates.clientDn=CN\=kafkaadmin
galapagos.kafka.environments[0].stagingOnly=false

# Second Kafka Cluster: A Confluent Cloud based cluster, API Key + Secret based authentication.
galapagos.kafka.environments[1].id=prod
galapagos.kafka.environments[1].name=PROD
galapagos.kafka.environments[1].bootstrap-servers=pkc-12345.europe-west1.gcp.confluent.cloud:9092
galapagos.kafka.environments[1].authenticationMode=ccloud
galapagos.kafka.environments[1].ccloud.cloudUserName=galapagos@my.company.tld.domain
galapagos.kafka.environments[1].ccloud.cloudPassword=TopSecret123
galapagos.kafka.environments[1].ccloud.environmentId=env-abc123
galapagos.kafka.environments[1].ccloud.clusterId=lkc-12345
galapagos.kafka.environments[1].ccloud.clusterApiKey=ABCDEFGHI123456
galapagos.kafka.environments[1].ccloud.clusterApiSecret=TopSecret999!@MuchMoreSecrets
galapagos.kafka.environments[1].stagingOnly=true

# The "prod" environment is used for storing cross-cluster metadata, e.g. application owner requests.
galapagos.kafka.production-environment=prod

# If set to true, Galapagos will not perform any modification operations in Kafka, e.g. not create or delete ACLs or topics.
galapagos.kafka.readonly=false

# Naming Customization (see src/main/resources/application.properties for available options)
galapagos.naming.events.additionRules.allowPascalCase=true
galapagos.naming.data.additionRules.allowPascalCase=true
galapagos.naming.commands.additionRules.allowPascalCase=true

# Take your time for initializing repositories
galapagos.initialRepositoryLoadWaitTime=10s
galapagos.repositoryLoadIdleTime=3s

# The fields of a list of Custom Links
galapagos.customLinks.links[0].id=naming-convention
galapagos.customLinks.links[0].href=https://wiki.my.company.tld.domain/Kafka+Naming+Conventions
galapagos.customLinks.links[0].label=MyCompany Kafka Naming Conventions
galapagos.customLinks.links[0].linkType=EDUCATIONAL

# Enable creation of topics with sensitive data, where approval for subscriptions is required
# (This is an opt-in feature per topic; creators can still choose to leave their topics open for direct subscriptions)
info.toggles.subscriptionApproval=true

# Enable more greedy deletion of JSON schemas
info.toggles.schemaDeleteWithSub=true
```

### Example Keycloak config

An example `keycloak.json` could look like this:

```json
{
  "auth-server-url": "https://keycloak.my.company.tld.domain/auth/",
  "realm": "galapagos",
  "resource": "galapagos-webapp-prod",
  "public-client": true,
  "use-resource-role-mappings": true,
  "principal-attribute": "preferred_username"
}
```

### Galapagos Service

The Galapagos _Service_ Kubernetes definition is quite straightforward:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: galapagos-service
spec:
  selector:
    app: galapagos
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
```

### Galapagos Ingress

The _Ingress_ is also not that complex:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: galapagos-ingress
spec:
  tls:
    - hosts:
        - galapagos.my.company.tld.domain
      secretName: tls-secret
  rules:
    - host: galapagos.my.company.tld.domain
      http:
        paths:
          - backend:
              service:
                name: galapagos-service
                port:
                  number: 80 
```
