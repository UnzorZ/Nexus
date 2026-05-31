# ADR-0002: Modular Monolith With Spring Modulith

Status: Accepted

## Context

Nexus needs strong internal boundaries, but it will initially run as a single deployable Spring Boot application without Kubernetes.

Splitting into microservices early would add operational complexity without solving a current problem.

## Decision

Nexus will be built as a modular monolith using Spring Modulith.

Each major capability lives in an application module under `dev.unzor.nexus`: `projects`, `apikeys`, `identity`, `permissions`, `modules`, `registry`, `audit`, `notify`, and `admin`.

Cross-module communication should happen through public application services or application events. Modules must not directly access another module's repositories.

## Consequences

- The backend remains simple to run and deploy.
- Module boundaries are testable with Modulith architecture tests.
- Future extraction of a module remains possible if there is a real operational reason.
