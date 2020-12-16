# Demo Setup

The following Demo Setup allows you to get a first impression of Galapagos and the underlying concepts. The full demo
setup consists of the following and will be set up for you by the setup script:

* Two Kafka Clusters (each consisting of only one broker and one Zookeeper instance)
* A Keycloak instance, including a MariaDB backend
* Galapagos
* All the required certificate stuff

## Prerequisites

To run Galapagos locally and get an idea what it can do for you, you will need the following prerequisites:

* A running Minikube (can be local, remote, or in a VM)
* On Windows: Git Bash or Cygwin for exeuting the Demo Setup Script
* `kubectl` on `PATH` and configured for access to the Minikube (including current context to be set to the Minikube)
* `openssl` on `PATH`
* `JAVA_HOME` must be set and point to JDK 9 or a later JDK.
* Lots of memory ;-)  (especially for the two Kafka brokers we are going to set up.)
  Around 8 GB will be used by the containers. See _Reduce Memory Consumption_ if you do not have that much memory
  available.

## Setup Minikube

If you do not already have a Minikube available, refer to the installation instructions for Minikube here:
[Minikube Installation](https://kubernetes.io/de/docs/tasks/tools/install-minikube/)

Please make sure that Minikube allocates enough resources. I run Minikube in an Ubuntu VM, but within this VM, in
`--vm-driver=none` parameter. The VM has 10 GB allocated memory. I start Minikube with these parameters:

```
  minikube start --vm-driver=none --cpus 2 --memory 8192
```

In the context of this demo, I recommend running the Galapagos Demo Setup Script on the same machine (or VM) where you
installed Minikube, as your `kubectl` will be already configured correctly. Otherwise, please make sure that your
`kubectl` is configured to use your Minikube (including setting the current context of `kubectl` correctly).

## Clone repository

If you have not already done so, checkout the source code of Galapagos, as this contains the Demo Setup script.

```
git clone https://github.com/HermesGermany/galapagos.git
```

## Execute setup script

After cloning the repository, cd into the `galapagos/demo` sub folder and execute `./setup-demo.sh`. On Windows, we
recommend using Git Bash or Cygwin for execution.

The script will run a while and create the following namespaces in your Minikube:

* `galapagos` - The Galapagos application, including all required configuration and secrets
* `keycloak` - The Keycloak instance for authentication
* `kafka-pseudodev` - A Kafka cluster with one broker, including a Zookeeper instance
* `kafka-pseudoprod` - Another Kafka cluster with one broker and a Zookeeper instance

After completion, the script will provide you will the URL to Galapagos. This assumes that your Minikube provides direct
access to running services of type `NodePort`, on the IP of the Minikube itself. If not, additional Kubernetes
networking configuration may be required (e.g. adding an Ingress).

Note that starting up all the services will take some time - several **minutes** is completely normal. You can check the
progress of the Demo initialization using the script `./demo-status.sh`.

## Reduce Memory Consumption

If you do not have 8 GB of (free!) memory available, you may try to reduce the memory assigned to the Kafka brokers. For
this, edit `kafka.yml` in the `demo` subfolder and change this:

```
  limits:
    memory: "2048Mi"
    cpu: "250m"
```

to e.g.

```
  limits:
    memory: "1024Mi"
    cpu: "250m"
```

Note that you may encounter problems with Kafka when running on "only" 1 GB of RAM. We observed multiple restarts
(due to Out Of Memory) of the Kafka pods when using 1 GB memory as a limit.

## Cleanup / Restart Demo Setup

Use the `clear-demo.sh` script in the `demo` subfolder to delete all created namespaces from your Minikube. This may
take a while. Afterwards, you can use `setup-demo.sh` to recreate everything, e.g. after changing some Kubernetes files.
