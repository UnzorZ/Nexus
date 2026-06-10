# Módulo Identity

Módulo de autenticación y autorización de Nexus. Implementa un **Authorization Server OAuth2 / OIDC** basado en Spring Security y Spring Authorization Server.

---

## Resumen

| Aspecto | Detalle |
|---------|---------|
| **Paquete base** | `dev.unzor.nexus.identity` |
| **Tipo** | Módulo Spring Modulith (`@ApplicationModule(displayName = "Identity")`) |
| **Protocolo** | OAuth 2.0 + OpenID Connect 1.0 |
| **Flujos soportados** | Authorization Code, Refresh Token |
| **Estado** | MVP funcional con almacenamiento en memoria |

---

## Arquitectura

```
identity/
├── api/                              # Capa de API (controladores REST + DTOs)
│   ├── controller/
│   │   └── IdentityHelloController   # Health-check interno
│   └── dto/
│       └── IdentityModuleStatus      # Record de estado del módulo
│
├── application/                      # Capa de aplicación (servicios + config)
│   ├── configuration/
│   │   └── SecurityConfig            # Configuración central de seguridad y OAuth2
│   ├── events/                       # (vacío) Manejadores de eventos de dominio
│   ├── observability/                # (vacío) Métricas y trazabilidad
│   └── service/
│       └── IdentityHelloService      # Servicio de estado del módulo
│
├── domain/                           # Entidades y lógica de identidad por proyecto
│   ├── entity/
│   │   └── ProjectUser              # Usuario aislado dentro de un proyecto
│   ├── enums/
│   │   └── ProjectUserStatus        # Estado operativo del usuario
│   └── exception/
│
├── infrastructure/                   # Adaptadores de frameworks y servicios externos
│   ├── interceptor/
│   └── security/
│       └── ProjectUserPrincipal      # Adaptador de ProjectUser a UserDetails
│
├── oauth/                            # (vacío) Componentes OAuth2 personalizados
├── sessions/                         # (vacío) Gestión de sesiones
├── persistence/                      # (vacío) Repositorios JPA
│   └── repository/
│
├── IdentityApplication               # ApplicationRunner de arranque
└── package-info.java                 # Declaración del módulo Modulith
```

`ProjectUser` y `ProjectUserPrincipal` ya definen el modelo de usuario aislado
por proyecto. Los repositorios y el `UserDetailsService` con resolución
obligatoria del contexto de proyecto siguen pendientes. Las capas `oauth`,
`sessions` y `persistence` están preparadas para la implementación de clientes,
tokens, sesiones y acceso a datos.

La explicación completa del modelo y su separación respecto a las cuentas del
panel está en
[`Cuentas Nexus y usuarios de proyecto`](../auth/accounts-and-project-users.md).

---

## Configuración de Seguridad

La clase principal es [`SecurityConfig`](../../apps/api/src/main/java/dev/unzor/nexus/identity/application/configuration/SecurityConfig.java). Concentra la seguridad web del módulo Identity y los beans del Authorization Server OAuth2/OIDC.

Spring Security no usa una sola configuración global: registra varias `SecurityFilterChain` y aplica la **primera cuya ruta coincida** (`securityMatcher`). El orden lo marca `@Order`: números menores tienen prioridad.

```
Request
   │
   ├─ /oauth2/**, /userinfo, …     → @Order(1) SecurityConfig (Authorization Server)
   ├─ /internal/**, /actuator/**   → @Order(2) shared/SecurityConfiguration
   └─ todo lo demás                → @Order(3) SecurityConfig (default + form login)
```

### Cadena 1 — Authorization Server (`@Order(1)`)

Gestiona los endpoints del Authorization Server (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, etc.).

- Activa **OpenID Connect 1.0** (`oidc(Customizer.withDefaults())`).
- Requiere autenticación en todas las solicitudes antes de emitir códigos o tokens.
- Si el cliente es un navegador (`Accept: text/html`) y el usuario no está autenticado, redirige a `/login` para continuar el flujo Authorization Code.

### Cadena 2 — Endpoints internos (`@Order(2)`)

Definida en [`SecurityConfiguration`](../../apps/api/src/main/java/dev/unzor/nexus/shared/security/SecurityConfiguration.java) del paquete `shared` (no en Identity, pero forma parte del mismo despliegue).

- Aplica solo a `/internal/**` y `/actuator/**` mediante `securityMatcher`.
- Permite acceso libre a `/internal/**` y `/actuator/health` (health-checks de módulos y de la aplicación).
- Deshabilita CSRF en esas rutas para permitir llamadas con `curl`, probes o clientes HTTP sin sesión.
- Requiere autenticación HTTP Basic para el resto de endpoints de Actuator (métricas, info, etc.).

Esta cadena existe en `shared` porque los endpoints `/internal/**` los exponen varios módulos (Identity, Projects, Permissions, etc.), no solo Identity.

### Cadena 3 — Default (`@Order(3)`)

Cubre el resto de rutas de la aplicación.

- Permite sin autenticación: `/login`, `/identity/login.css`, `/.well-known/appspecific/**`, `/error`.
- Requiere autenticación en todo lo demás.
- Habilita **form login** con página personalizada (`LoginController` → plantilla Thymeleaf `identity/login`).

### Beans OAuth2 definidos en `SecurityConfig`

| Bean | Responsabilidad | Estado MVP |
|------|-----------------|------------|
| `userDetailsService` | Usuarios que pueden iniciar sesión en `/login` | Un usuario en memoria (`user` / `password`) |
| `registeredClientRepository` | Clientes OAuth registrados | Un cliente `oidc-client` para el frontend Next.js |
| `jwkSource` | Claves RSA para firmar JWT (access token, id token) | Par de claves nuevo en cada arranque |
| `jwtDecoder` | Valida JWT firmados con `jwkSource` | Derivado de las mismas claves |
| `authorizationServerSettings` | Rutas e issuer del Authorization Server | Valores por defecto de Spring |

La URL base del frontend (`nexus.frontend-base-url`) se usa al construir el `RegisteredClient` para las redirect URIs de login y logout.

---

## Componentes OAuth2/OIDC

### Usuario en memoria

```java
UserDetails user = User.withDefaultPasswordEncoder()
    .username("user")
    .password("password")
    .roles("USER")
    .build();
```

> **Nota:** Este usuario es temporal para el MVP. La implementación final debe usar un `UserDetailsService` respaldado por la base de datos.

### Cliente OIDC registrado

| Parámetro | Valor |
|-----------|-------|
| **Client ID** | `oidc-client` |
| **Client Secret** | `secret` (codificación `{noop}`) |
| **Auth Method** | `client_secret_basic` |
| **Grant Types** | `authorization_code`, `refresh_token` |
| **Redirect URI** | `{frontend-url}/login/oauth2/code/oidc-client` |
| **Post-logout URI** | `{frontend-url}/` |
| **Scopes** | `openid`, `profile` |
| **Consent** | Requerido (`requireAuthorizationConsent = true`) |

La URL base del frontend se configura con la variable de entorno `NEXUS_FRONTEND_BASE_URL` (por defecto `http://127.0.0.1:3000`).

### Firma de tokens (JWK)

- Algoritmo: **RSA 2048-bit**.
- Se genera un par de claves en cada arranque (en memoria).
- El key ID es un UUID aleatorio.
- Los tokens JWT se firman y verifican con este par de claves.

> **Nota:** Al reiniciar la aplicación, las claves RSA cambian y los tokens anteriores dejan de ser válidos. La implementación final debe persistir las claves.

---

## Endpoints

### Health-check interno

```
GET /internal/identity/hello
```

**Respuesta:**

```json
{
  "module": "identity",
  "status": "UP",
  "message": "identity module started"
}
```

Este endpoint es público (permitido por la cadena de filtros `@Order(2)` de `shared/SecurityConfiguration`).

### Endpoints del Authorization Server

Gestionados automáticamente por Spring Authorization Server:

| Endpoint | Descripción |
|----------|-------------|
| `GET /oauth2/authorize` | Inicia el flujo Authorization Code |
| `POST /oauth2/token` | Intercambia código o refresh token por access token |
| `GET /oauth2/jwks` | JSON Web Key Set público para verificación de tokens |
| `GET /userinfo` | Información del usuario autenticado (OIDC) |
| `POST /connect/logout` | Cierre de sesión OIDC |
| `GET /.well-known/openid-configuration` | Discovery document de OIDC |

---

## Flujo de Autenticación

```
┌──────────┐    1. GET /oauth2/authorize    ┌──────────────────┐
│  Frontend │ ──────────────────────────────► │  Nexus (Identity) │
│  (Next.js) │                                │  Authorization    │
│           │ ◄────────────────────────────── │  Server           │
│           │    2. 302 → /login              │                   │
│           │ ──────────────────────────────► │                   │
│           │    3. POST /login (credentials) │                   │
│           │ ◄────────────────────────────── │                   │
│           │    4. Consent screen            │                   │
│           │ ──────────────────────────────► │                   │
│           │    5. Approve scopes            │                   │
│           │ ◄────────────────────────────── │                   │
│           │    6. 302 → redirect_uri?code=  │                   │
│           │ ──────────────────────────────► │                   │
│           │    7. POST /oauth2/token        │                   │
│           │ ◄────────────────────────────── │                   │
│           │    8. { access_token, id_token, │                   │
│           │       refresh_token }           │                   │
└──────────┘                                 └──────────────────┘
```

---

## Configuración (application.properties)

```properties
# ── Usuario por defecto (en memoria) ──────────────────────
spring.security.user.name=user
spring.security.user.password=password

# ── URL base del frontend ─────────────────────────────────
nexus.frontend-base-url=${NEXUS_FRONTEND_BASE_URL:http://127.0.0.1:3000}

# ── Cliente OIDC ──────────────────────────────────────────
spring.security.oauth2.authorizationserver.client.oidc-client.registration.client-id=oidc-client
spring.security.oauth2.authorizationserver.client.oidc-client.registration.client-secret={noop}secret
spring.security.oauth2.authorizationserver.client.oidc-client.registration.redirect-uris[0]=${nexus.frontend-base-url}/login/oauth2/code/oidc-client
spring.security.oauth2.authorizationserver.client.oidc-client.registration.scopes[0]=openid
spring.security.oauth2.authorizationserver.client.oidc-client.registration.scopes[1]=profile
spring.security.oauth2.authorizationserver.client.oidc-client.require-authorization-consent=true
```

### Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `NEXUS_FRONTEND_BASE_URL` | `http://127.0.0.1:3000` | URL base del frontend para redirects OIDC |

---

## Testing con Postman

Existe una colección Postman en [`docs/api/postman/`](../api/postman/README.md) para probar el flujo OIDC completo:

1. Discovery → obtener el documento OIDC.
2. Authorize → iniciar sesión en el navegador.
3. Token → intercambiar el código por tokens.
4. UserInfo → obtener datos del usuario.
5. Refresh → renovar el access token.

---

## Dependencias

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server'
testImplementation 'org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server-test'
```

---

## Estado actual y roadmap

### ✅ Implementado (MVP)

- [x] Authorization Server OAuth2/OIDC funcional.
- [x] Flujo Authorization Code + Refresh Token.
- [x] Firma de tokens JWT con RSA 2048.
- [x] Cliente OIDC configurado para el frontend Next.js.
- [x] Health-check interno del módulo.
- [x] Colección Postman para pruebas manuales.

### 🔲 Pendiente

- [ ] **Persistencia de usuarios** — Migrar de `InMemoryUserDetailsManager` a JPA con entidades de dominio (`domain/entity/`).
- [ ] **Persistencia de clientes** — Migrar de `InMemoryRegisteredClientRepository` a repositorio JPA (`persistence/repository/`).
- [ ] **Persistencia de tokens/autorizaciones** — Almacenar autorizaciones OAuth2 en base de datos.
- [ ] **Persistencia de claves JWK** — Evitar invalidar tokens al reiniciar la aplicación.
- [ ] **Gestión de usuarios** — Registro, edición de perfil, cambio de contraseña.
- [ ] **Roles y permisos** — Integración con el módulo `permissions` (ver [ADR-0003](../adr/0003-positive-permissions-in-mvp.md)).
- [ ] **Eventos de dominio** — Publicar eventos de registro/login/logout para otros módulos (`application/events/`).
- [ ] **Observabilidad** — Métricas de login, fallos, tokens emitidos (`application/observability/`).
- [ ] **Gestión de sesiones** — Revocación, listado de sesiones activas (`sessions/`).
- [ ] **Migraciones de base de datos** — Flyway/Liquibase para las tablas de identidad.

---

## Relación con otros módulos

```
                    ┌─────────────┐
                    │   Identity   │
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │ Permissions  │ │   Audit      │ │   Admin      │
   └─────────────┘ └─────────────┘ └─────────────┘
```

- **Permissions** — Identity provee el usuario autenticado; Permissions evalúa sus permisos.
- **Audit** — Identity emite eventos de autenticación que Audit puede registrar.
- **Admin** — Admin consume servicios de Identity para gestión de usuarios.
- **Projects / API Keys** — Verifican la identidad del solicitante a través de los tokens emitidos por este módulo.
