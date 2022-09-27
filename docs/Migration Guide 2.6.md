# Galapagos 2.5.x to 2.6.0 Migration Guide

Galapagos 2.6.0 changed the Confluent Cloud REST API endpoints from the "inofficial" ones (backing up the Confluent
Cloud Web UI and requiring user name / password) to
the [official REST API](https://docs.confluent.io/cloud/current/api.html),
requiring API Keys and Secrets.

This means that you have to perform the following steps (explained in detail further below):

1. Create a Galapagos Service Account in your Confluent Cloud organization with **OrganizationAdmin** rights.
2. Create a "Cloud API Key" and Secret for this service account
3. Change your Galapagos Configuration to use this API Key and Secret instead of user name / password
4. Update service account metadata stored in Galapagos.

## 1. Create Galapagos Service Account

### Via Confluent CLI:

```bash
confluent iam service-account create galapagos-admin --description "OrgAdmin for Software Galapagos"
confluent iam rbac role-binding create --role OrganizationAdmin --principal "User:<service-account-id>"
```

Note that the Service Account ID is output by the first command - something like `sa-xyz123`.

### Via Confluent Web UI:

Click the "Burger Menu" in the upper right and select "Accounts & Access". Click on "Service Accounts" and click
"+ Add Service Account". Enter `galapagos-admin` as service account name and `OrgAmin for Software Galapagos` as
description. Click "Next".

In the next step, click your organization node in the tree (usually, the name of your company) and click "Add Role
Assignment". Select "OrganizationAdmin" and "Add".

Click "Review" at the bottom of the screen, review your inputs and click "Create service account".

## 2. Create Cloud API Key

### Via Confluent CLI:

```bash
confluent api-key create --resource cloud --description "API Key for Software Galapagos" --service-account "<service-account-id>"
```

Change the `<service-account-id>` to the ID of the created service account from the previous step - something
like `sa-xyz123`.

Copy the displayed API Key and Secret, so you can find it later (in the next step).

### Via Confluent Web UI:

Click the "Burger Menu" in the upper right and select "Cloud API Keys". Click "+ Add Key". Select **Granular Access**.

Now, select the Service Account created in step 1 (galapagos-admin). Click "Next".

Copy the displayed API Key and Secret, so you can find it later (in the next step).

Enter "API Key for Software Galapagos" as description and click "Download and continue" (the downloaded file also
contains API Key + secret, so store it safely or delete it).

## 3. Update Galapagos Configuration

For the next steps, we assume that your Cloud API Key is `ABCDEF123456` and your Secret is `topsecret123`. You will
have to replace these values with your received values.

### YAML Config

Let's assume your previous configuration looked like this:

```yaml
galapagos:

  kafka:
    environments:
      - id: nonprod
        name: "NONPROD"
        bootstrap-servers: "pkc-12345.europe-west1.gcp.confluent.cloud:9092"
        authentication-mode: ccloud
        ccloud:
          environment-id: "env-abc123"
          cluster-id: "lkc-abc123"
          cluster-api-key: "XYZ999888"
          cluster-api-secret: "shh-my-secret"
          cloudUserName: "myuser@mycompany.somewhere"
          cloudPassword: "wow-this-is-safe"
```

Then change it to this:

```yaml
galapagos:

  kafka:
    environments:
      - id: nonprod
        name: "NONPROD"
        bootstrap-servers: "pkc-12345.europe-west1.gcp.confluent.cloud:9092"
        authentication-mode: ccloud
        ccloud:
          environment-id: "env-abc123"
          cluster-id: "lkc-abc123"
          cluster-api-key: "XYZ999888"
          cluster-api-secret: "shh-my-secret"
          organization-api-key: "ABCDEF123456"
          organization-api-secret: "topsecret123"
          serviceAccountIdCompatMode: true  # this is the default anyway. Fixes a (temporary) quirk of the new API 
```

### Properties config

Similar in .properties files:

Old one:

```properties
galapagos.kafka.environments[0].id=dev
galapagos.kafka.environments[0].name=DEV
galapagos.kafka.environments[0].bootstrap-servers=pkc-12345.europe-west1.gcp.confluent.cloud:9092
galapagos.kafka.environments[0].authenticationMode=ccloud
galapagos.kafka.environments[0].ccloud.cloudUserName=myuser@mycompany.somewhere
galapagos.kafka.environments[0].ccloud.cloudPassword=wow-this-is-safe
galapagos.kafka.environments[0].ccloud.environmentId=env-abc123
galapagos.kafka.environments[0].ccloud.clusterId=lkc-abc123
galapagos.kafka.environments[0].ccloud.clusterApiKey=XYZ999888
galapagos.kafka.environments[0].ccloud.clusterApiSecret=shh-my-secret
```

New:

```properties
galapagos.kafka.environments[0].id=dev
galapagos.kafka.environments[0].name=DEV
galapagos.kafka.environments[0].bootstrap-servers=pkc-12345.europe-west1.gcp.confluent.cloud:9092
galapagos.kafka.environments[0].authenticationMode=ccloud
galapagos.kafka.environments[0].ccloud.organizationApiKey=ABCDEF123456
galapagos.kafka.environments[0].ccloud.organizationApiSecret=topsecret123
galapagos.kafka.environments[0].ccloud.environmentId=env-abc123
galapagos.kafka.environments[0].ccloud.clusterId=lkc-abc123
galapagos.kafka.environments[0].ccloud.clusterApiKey=XYZ999888
galapagos.kafka.environments[0].ccloud.clusterApiSecret=shh-my-secret
# This (below) is the default anyway. Fixes a (temporary) quirk of the new API
galapagos.kafka.environments[0].ccloud.serviceAccountIdCompatMode=true
```

Do this for all your configured environments (clusters).

### 4. Update Service Account metadata stored in Galapagos

The official REST API also changes the way a service account is "identified". The inofficial API used a numeric ID, the
official API uses a "resource ID" like `sa-abc123`. To make things worse, setting ACLs directly in Confluent Cloud Kafka
only works using the old "numeric ID", which is currently **not** being returned by the new API. Confluent offers a
"workaround" API until their Kafka accepts the resource IDs for ACLs as well.

Galapagos now calls this workaround API to get the numeric IDs for service accounts when updating their ACLs (for
example, when an application subscribes to a new topic). To avoid too many of these API calls, you can run a one-time
"admin job" to update the existing service account metadata in Galapagos to contain both the resource and numeric ID.
This is **highly recommended**, although not strictly necessary.

Depending on your setup, you can, for example, configure a Kubernetes "Job" to perform this update. Here is an example
YAML (note: the `spec` should be, in large parts, identical to the YAML for your production instance of Galapagos, e.g.
to use same configuration):

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: galapagos-confluent-metadata-update
spec:
  template:
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
            - name: SPRING_CONFIG_ADDITIONAL-LOCATION
              value: "file:///appconfig/"
            - name: JAVA_TOOL_OPTIONS
              value: "-Dlogging.config=file:///appconfig/logback-spring.xml"
          args:
            - "--spring.profiles.active=prod,actuator"
            - "--spring.mail.password=$(SMTP_PASSWORD)"
            # IMPORTANT is this extra parameter for running the job
            - "--galapagos.jobs.update-confluent-auth-metadata"
          volumeMounts:
            - name: spring-config
              mountPath: "/appconfig"
              readOnly: true
      volumes:
        - name: spring-config
          configMap:
            name: spring-properties
```

Run this job with Kubernetes e.g. by applying this YAML.

Alternatively, you can just run Galapagos with the additional parameter
`--galapagos.jobs.update-confluent-auth-metadata` to run this admin job. But always make sure that Galapagos uses the
same configuration as your production instance, so correct data is being updated.

After the admin job has been run successfully, you can start Galapagos 2.6.0, using the new configuration.
