# nexus-spring-boot-sdk

[![Maven Central](https://img.shields.io/maven-central/v/dev.unzor.nexus.sdk/nexus-spring-boot-sdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.unzor.nexus.sdk/nexus-spring-boot-sdk)

**Spring Boot** SDK for apps that integrate with a [Nexus](https://github.com/UnzorZ/Nexus)
control plane. A single dependency auto-configures, from `nexus.*` properties,
what's needed to talk to a Nexus server over HTTP, with **no compile dependency
on the backend**.

- **Security** — OIDC login, local JWT validation (resource server), `@perm`
  permission authorization, RP-initiated + back-channel logout.
- **Management** — heartbeat, permission-snapshot cache (fail-closed, wildcard-aware),
  app-declared permission sync, notify, and typed clients for config / vault / metrics.

Licensed under **Apache-2.0** (the Nexus *server* is AGPL-3.0; the client SDK is
permissive to maximize adoption).

## Install

**Gradle (Kotlin DSL)**
```kotlin
implementation("dev.unzor.nexus.sdk:nexus-spring-boot-sdk:0.1.0")
```

**Gradle (Groovy DSL)**
```groovy
implementation 'dev.unzor.nexus.sdk:nexus-spring-boot-sdk:0.1.0'
```

**Maven**
```xml
<dependency>
  <groupId>dev.unzor.nexus.sdk</groupId>
  <artifactId>nexus-spring-boot-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

The SDK depends on Spring Boot 4.0.x; its transitive versions come from the Spring
Boot BOM, so if your app already uses Spring Boot 4.0.x they line up automatically.

## Minimal configuration

```yaml
nexus:
  url: https://nexus.example.com          # your Nexus origin
  app-name: my-app
  security:
    issuer: https://nexus.example.com/p/my-project   # the project realm issuer
    client:
      client-id: my-app-oauth-client
      client-secret: ${OAUTH_CLIENT_SECRET}
    backchannel-logout-path: /logout/backchannel
```

See the reference app at [`examples/spring-client-app`](../../examples/spring-client-app)
for a complete, runnable integration (OIDC login, resource-server `@perm` endpoints,
heartbeat, back-channel logout, and the typed clients).

## Coordinates

| | |
|---|---|
| **groupId** | `dev.unzor.nexus.sdk` |
| **artifactId** | `nexus-spring-boot-sdk` |
| **Java package** | `dev.unzor.nexus.sdk` |
| **License** | Apache-2.0 |
| **Source** | https://github.com/UnzorZ/Nexus (`packages/nexus-spring-boot-sdk`) |
