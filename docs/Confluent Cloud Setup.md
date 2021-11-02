# Confluent Cloud (Development & Demo) Setup

The following Setup allows you to run the Galapagos codebase locally against Confluent Cloud. You can test Galapagos'
functionality, configure it to your needs and even change some code if necessary.

## Prerequisites

You will need the following:

* A valid subscription for Confluent Cloud, with payment details configured (even if in free tier, because payment
  details must be configured to be allowed to allocate clusters).
* The user name and password of an OrgAdmin of your Confluent Cloud organization. Login with this data must be possible
  at https://confluent.cloud/.
* A running Minikube for the Keycloak instance (if you already have a running Keycloak, see [Keycloak.md](./Keycloak.md)
  on how to set up the Galapagos realm).
* On Windows: Git Bash or Cygwin for exeuting the Demo Setup Script
* `kubectl` on `PATH` and configured for access to the Minikube (including current context to be set to the Minikube)
* Apache Maven installed

## Setup Minikube

If you do not already have a Minikube available, refer to the installation instructions for Minikube here:
[Minikube Installation](https://kubernetes.io/de/docs/tasks/tools/install-minikube/)

I run Minikube in an Ubuntu VM, but within this VM, in `--vm-driver=none` mode. I start Minikube with these parameters:

```
  minikube start --vm-driver=none --cpus 2 --memory 4096
```

Make sure that your environment executing the Minikube has enough memory available.

I recommend running the Galapagos Demo Setup Script on the same machine (or VM) where you installed Minikube, as your
`kubectl` will be already configured correctly. Otherwise, please make sure that your `kubectl` is configured to use
your Minikube (including setting the current context of `kubectl` correctly).

## Clone repository

If you have not already done so, checkout the source code of Galapagos, as this contains the DEV Setup scripts.

```
git clone https://github.com/HermesGermany/galapagos.git
```

## Execute setup script

After cloning the repository, cd into the `galapagos/devsetup` sub folder and execute `./setup-dev-ccloud.sh`. On
Windows, we recommend using Git Bash or Cygwin for execution.

The script will run a while and create a `keycloak` namespaces in your Minikube. Additionally, you will be asked for the
Confluent Cloud credentials, and all required resources are created in the Confluent Cloud.

After completion, the configuration file `application-local.properties` in the Galapagos root will have been created.
Use the `start-galapagos.sh` script to build & run Galapagos. This will take a while - see later section for IDE run
configuration.

After successful startup, visit http://localhost:8080/app to open & test Galapagos. Use user / pass `admin1` for
administrative access or `user1` for "normal user" access to Galapagos (when using the Keycloak configured by the setup
script).

## Cleanup / Restart Setup

In the Confluent Cloud, delete the environment `galapagos-dev` and re-run the `setup-dev-ccloud.sh` script for having
the Confluent Cloud resources re-created.

If you also want to redeploy the Keycloak to your Minikube, delete the `keycloak` namespace from your minikube before
re-running the setup script:

```
kubectl delete namespace keycloak
```

## IDE Run Configuration

To be able to run Galapagos directly from your IDE (e.g. for development), you will need to configure **two** run
configurations: One for the Backend, and one for the Frontend.

### Backend Run Configuration

To run the Galapagos Backend from within the IDE, configure a Run Configuration which runs the Java Main Class
`com.hermesworld.ais.galapagos.GalapagosApplication`. Program arguments should
be `--spring.profiles.active=ccloud,local`.

### Frontend Run Configuration

To run the Galapagos Frontend, you must run the NPM target `start` from within the `ui` subfolder. Either do this via
command line (npm must be installed):

```
cd ui && npm run start
```

Or configure a Node.js or Angular Run Configuration in your IDE (this depends a lot on the IDE used) which does this for
you.

### Accessing Galapagos while running in IDE

Please note that the Galapagos Frontend is, by default, accessible via http://localhost:4200/app (instead of Port 8080)
when running backend and frontend separately. This URL should be opened in a browser to open Galapagos then.
