# galapagos

<p align="center">
<img alt="Galapagos Logo" src="./logo/logo.svg" width="264" height="201">
</p>

Galapagos is a DevOps Self-Service Software for Apache Kafka, including an opinionated approach on how to use Kafka for
event-driven enterprise architecture.

## Fundamentals

See [Event-Driven Architecture Principles](event_driven_architecture_principles.md) for our principles for an
Enterprise-wide Event-Driven Architecture, and [Kafka Guidelines](kafka_guidelines.md) for the guidelines for Kafka
usage which result directly from these principles. Galapagos is our tool to "enforce" these Guidelines, while not
putting additional burden on DevOps teams.

## Demo Setup

The current Demo Setup sets up two Kafka Clusters and a Keycloak instance in a local Minikube. It uses certificate-based
authentication for Kafka.

[Demo Setup](docs/Demo%20Setup.md)

But we **highly recommend** to start with the **Confluent Cloud** based Setup.
See [Confluent Cloud Setup](docs/Confluent%20Cloud%20Setup.md) for details. It only creates a Keycloak instance in a
local Minikube; the Kafka clusters are created in a Confluent Cloud subscription. This consumes much less local
resources and is much easier to setup. Also, you will get a setup where you can run Galapagos from your IDE, enabling
you to start developing on Galapagos and contribute to this great project :-)

## Status

Currently, Galapagos supports self-hosted Kafka Clusters which **must** use Client Certificate-based authentication, as
well as **Confluent Cloud** with their API Key + Secret mechanism. Teams can generate API Keys + secrets for their
applications via the Galapagos UI (or via REST).

**[Discussions](https://github.com/HermesGermany/galapagos/discussions)** are now live! Get in touch - let us talk about
the fundamentals, the setup, recommendations for others...

## Build

To build Galapagos, just run

```
mvn package
```

to get a Spring Boot executable JAR.

## Run

To run Galapagos locally for a first test, just use `start-galapagos.sh` for startup. Please note that you **must** have
executed one of the `devsetup/setup-dev-*.sh` scripts first - see Demo Setup above.

In **production**, you should use one of the Galapagos Docker images available
at [DockerHub](https://hub.docker.com/r/hermesgermany/galapagos/tags). Recommended is using Kubernetes as execution
environment. Configuration can be provided e.g. via ConfigMaps. See
[Kubernetes Docs](docs/Kubernetes.md) for details.
