# galapagos

<p align="center">
<img alt="Galapagos Logo" src="./logo/logo.svg" width="264" height="201">
</p>

Galapagos is a DevOps Self-Service Software for Apache Kafka, including an opinionated approach on how to use Kafka for event-driven enterprise architecture.

## Fundamentals

See [Event-Driven Architecture Principles](event_driven_architecture_principles.md) for our principles for an Enterprise-wide Event-Driven Architecture, and [Kafka Guidelines](kafka_guidelines.md) for the guidelines for Kafka usage which result directly from these principles. Galapagos is our tool to "enforce" these Guidelines, while not putting additional burden on DevOps teams.

## Demo Setup

[Demo Setup](docs/Demo%20Setup.md)

## Status

Initial Source Code has been **published**. You can now checkout the source and build Galapagos yourself. Additional docs on project structure and how-tos will be added, stay tuned.

**[Discussions](https://github.com/HermesGermany/galapagos/discussions)** are now live! Get in touch - let us talk about the fundamentals, the setup, recommendations for others...

## Build

To build Galapagos, just run

```
mvn package
```

to get a Spring Boot executable JAR.

## Run

To **run** Galapagos, you'll need additional services like (at least) two Kafka clusters and a Keycloak. You'll find a script which configures a local _Minikube_ to provide these dependencies in the `devsetup` folder. Additional documentation for this setup will follow, but you can already check the `setup-dev.sh` script to find out what is required to run Galapagos. If you have made it through the _Demo Setup_ successfully, the DEV Setup should be no problem for you.
