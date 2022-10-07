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

* A _ConfigMap_ named `spring-properties`, containing an `application-prod.yml`, a `keycloak.json`, and
  a `logback-spring.xml`
* A _Secret_ named `spring-secret-properties`, containing only the key `SMTP_PASSWORD` with the SMTP password for the
  SMTP server configured via the `application-prod.yml`
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
          image: hermesgermany/galapagos:2.6.0
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

See [application-demo.yml](../application-demo.yml) for a first example. Configuration variables used there can be found
in [application-democonf.properties](../application-democonf.properties) after running the one-time demo setup.

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
