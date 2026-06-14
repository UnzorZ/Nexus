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
| **Estado** | OAuth persistente en PostgreSQL; login de panel separado en `admin` |

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
   ├─ /panel/**, /api/panel/**     → @Order(3) admin/PanelSecurityConfiguration (NexusAccount + CSRF)
   ├─ /p/**                         → @Order(4) identity/ProjectSecurityConfiguration (reservado ProjectUser)
   └─ resto                         → @Order(5) SecurityConfig (denyAll por defecto)
```

El panel Nexus **no** usa el Authorization Server global. Las cuentas
`NexusAccount` inician sesión en `/panel/login` con sesión HTTP y CSRF
(`XSRF-TOKEN` / `X-XSRF-TOKEN`). Los flujos OAuth persistentes preparan
`ProjectUser` por proyecto bajo `/p/{projectSlug}/**` (diseño futuro).

### Cadena 1 — Authorization Server (`@Order(1)`)

Gestiona los endpoints del Authorization Server (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, etc.).

- Activa **OpenID Connect 1.0** (`oidc(Customizer.withDefaults())`).
- Requiere autenticación en todas las solicitudes antes de emitir códigos o tokens.
- Si el cliente es un navegador (`Accept: text/html`) y el usuario no está autenticado, redirige a `/oauth2/authentication-required`. **No** usa `/panel/login` ni `NexusAccountUserDetailsService`.

### Cadena 2 — Endpoints internos (`@Order(2)`)

Definida en [`SecurityConfiguration`](../../apps/api/src/main/java/dev/unzor/nexus/shared/security/SecurityConfiguration.java) del paquete `shared` (no en Identity, pero forma parte del mismo despliegue).

- Aplica solo a `/internal/**` y `/actuator/**` mediante `securityMatcher`.
- Permite acceso libre a `/internal/**` y `/actuator/health` (health-checks de módulos y de la aplicación).
- Deshabilita CSRF en esas rutas para permitir llamadas con `curl`, probes o clientes HTTP sin sesión.
- Requiere autenticación HTTP Basic para el resto de endpoints de Actuator (métricas, info, etc.).

Esta cadena existe en `shared` porque los endpoints `/internal/**` los exponen varios módulos (Identity, Projects, Permissions, etc.), no solo Identity.

### Cadena 3 — Panel admin (`@Order(3)`)

Definida en [`PanelSecurityConfiguration`](../../apps/api/src/main/java/dev/unzor/nexus/admin/application/configuration/PanelSecurityConfiguration.java).

- Aplica a `/panel/**` y `/api/panel/**`.
- Autentica `NexusAccount` con form login en `/panel/login` y sesión HTTP.
- CSRF activo con cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`.
- La sesión del panel y la cookie persistente `JSESSIONID` duran siete días por
  defecto (`NEXUS_SESSION_TIMEOUT` y `NEXUS_SESSION_COOKIE_MAX_AGE`).
- `/api/panel/**` sin sesión responde **401** (sin redirección HTML).
- `/panel/**` HTML sin sesión redirige a `/panel/login`.
- Logout API: `POST /api/panel/v1/session/logout` (204, CSRF). Logout HTML: `POST /panel/logout`.
- `/admin/**` y `/api/admin/**` quedan reservados para un futuro panel exclusivo de `INSTANCE_ADMIN`.

### Cadena 4 — Proyecto reservado (`@Order(4)`)

Definida en [`ProjectSecurityConfiguration`](../../apps/api/src/main/java/dev/unzor/nexus/identity/application/configuration/ProjectSecurityConfiguration.java).

- Aplica a `/p/**` sin deshabilitar CSRF globalmente.
- `GET /p/{projectSlug}/login` es público y reservado; no hay login funcional de `ProjectUser`.
- No registra `ProjectUserUserDetailsService` como bean global.

### Cadena 5 — Residual (`@Order(5)`)

- Permite recursos estáticos, `/oauth2/authentication-required`, `/oauth2/bootstrap/callback` y `/error`.
- Deniega el resto (`denyAll`).

### Beans OAuth2 JDBC (`identity`)

| Bean | Responsabilidad | Estado |
|------|-----------------|--------|
| `RegisteredClientRepository` | Clientes OAuth en PostgreSQL | `JdbcRegisteredClientRepository` |
| `OAuth2AuthorizationService` | Autorizaciones OAuth | `JdbcOAuth2AuthorizationService` |
| `OAuth2AuthorizationConsentService` | Consentimientos | `JdbcOAuth2AuthorizationConsentService` |
| `OidcRegisteredClientBootstrap` | Cliente técnico idempotente | Bootstrap, no consumido por el panel |
| `jwkSource` | Firma JWT del Authorization Server | Par RSA en memoria por arranque (pendiente persistir) |

El cliente bootstrap se configura con `nexus.oauth.bootstrap.*`. El secreto se
almacena como `{bcrypt}…`. En cada arranque se reconcilian secreto, métodos de
autenticación, grants, redirects, scopes y consentimiento con la configuración
actual, conservando el ID interno del cliente persistido. La redirect URI por
defecto es `/oauth2/bootstrap/callback` en la API, no una ruta del panel Next.js.
El `client-id` público no se cambia automáticamente: si se modifica conservando
el mismo `registered-client-id`, el arranque falla con un mensaje explícito.

---

## Componentes OAuth2/OIDC

### Cliente bootstrap (técnico)

| Parámetro | Valor por defecto |
|-----------|-------------------|
| **Registered client ID** | `nexus-oidc-client` |
| **Client ID** | `oidc-client` |
| **Client Secret** | `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET` (codificado `{bcrypt}`) |
| **Redirect URI** | `http://127.0.0.1:8080/oauth2/bootstrap/callback` |
| **Scopes** | `openid`, `profile` |

> **Nota:** Este cliente prepara la persistencia OAuth para futuros flujos multi-issuer por proyecto. El panel Next.js **no** lo consume; usa sesión HTTP en `/panel/login`.

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

## Flujo del panel (implementado)

```
Next.js /register  ──POST /api/panel/v1/accounts + CSRF──►  API del panel
Next.js /login     ──redirect──►  GET /panel/login?continue=…
Usuario            ──POST /panel/login + CSRF──►  sesión HTTP (JSESSIONID)
Next.js /dashboard ──GET /api/panel/v1/me + cookies──►  401 → redirect login
                   ──POST /api/panel/v1/session/logout + CSRF──►  204
```

`POST /api/panel/v1/accounts` permanece público por diseño. Si todavía no existe
una cuenta administradora de instancia, la primera cuenta registrada recibe
automáticamente `instanceAdmin = true`.
El operador debe reclamar una instalación nueva antes de exponerla de forma general.

### Limitación CSRF cross-host

Si el frontend (`localhost:3000`) y la API (`localhost:8080`) usan hosts distintos, el navegador trata las cookies por origen. Las mutaciones deben usar `credentials: "include"` y la API debe exponer CORS con `allowCredentials`. En producción, alinear dominio/parent domain o usar un proxy same-origin.

## Flujo OAuth por proyecto (reservado)

```
/p/{slug}/oauth2/*  → issuer futuro por proyecto
/p/{slug}/login     → placeholder (sin ProjectUser funcional)
/oauth2/authorize   → redirige a /oauth2/authentication-required (no panel)
```

---

## Configuración (application.properties)

```properties
nexus.frontend-base-url=${NEXUS_FRONTEND_BASE_URL:http://localhost:3000}

nexus.oauth.bootstrap.registered-client-id=nexus-oidc-client
nexus.oauth.bootstrap.client-id=oidc-client
nexus.oauth.bootstrap.client-secret=${NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET:changeme-local-dev}
nexus.oauth.bootstrap.redirect-uri=${NEXUS_OAUTH_BOOTSTRAP_REDIRECT_URI:http://127.0.0.1:8080/oauth2/bootstrap/callback}
nexus.oauth.bootstrap.post-logout-redirect-uri=${nexus.frontend-base-url}/
```

### Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `NEXUS_FRONTEND_BASE_URL` | `http://localhost:3000` | URL base del panel Next.js (redirect post-login, validación `continue`) |
| `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET` | `changeme-local-dev` | Secreto del cliente bootstrap (se persiste como `{bcrypt}…`) |

---

## Testing con Postman

Existe una colección Postman en [`docs/api/postman/`](../api/postman/README.md) para probar el Authorization Server OAuth2/OIDC (discovery, authorize, token, userinfo, refresh). El panel Nexus usa sesión HTTP en `/panel/login`, no este flujo OIDC.

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

### ✅ Implementado

- [x] Authorization Server OAuth2/OIDC funcional.
- [x] Flujo Authorization Code + Refresh Token.
- [x] Firma de tokens JWT con RSA 2048.
- [x] Persistencia JDBC de clientes, autorizaciones y consentimientos OAuth2.
- [x] Cliente bootstrap técnico idempotente (`nexus.oauth.bootstrap.*`).
- [x] Panel Nexus con `NexusAccount`, sesión HTTP, CSRF y `/panel/login`.
- [x] Health-check interno del módulo.

### 🔲 Pendiente (multi-issuer)

- [ ] Issuer dinámico por proyecto (`/p/{slug}`).
- [ ] `ProjectUserUserDetailsService` con `projectId` obligatorio.
- [ ] Discovery/JWKS/claves de firma por proyecto.
- [ ] Login funcional de `ProjectUser` en `/p/{slug}/login`.
- [ ] Persistencia de claves JWK del Authorization Server.
- [ ] Integración con `permissions` y eventos de dominio de auth.

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
