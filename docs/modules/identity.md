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
| **Estado** | OAuth persistente en PostgreSQL (multi-issuer por proyecto, ADR-0016); login/verify/reset/MFA/sesiones de `ProjectUser` en `/api/p/{slug}/**`; login de panel separado en `admin` |

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
│   ├── observability/                # Estado/readiness de claves JWK
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

`ProjectUser` y `ProjectUserPrincipal` definen el usuario aislado por proyecto.
La implementación vive repartida en `application/service` (login, MFA, verify,
reset, sesiones), `application/configuration` (cadenas de seguridad + SAS),
`persistence/repository` (JPA) e `infrastructure/security` (authenticators +
principals). Los paquetes `oauth/` y `sessions/` del árbol de arriba siguen
vacíos por decisión de co-localización (el código de OAuth/sesiones está en
`application` + `infrastructure`, no en subárboles separados).

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
`NexusAccount` inician sesión con login JSON en `POST /api/panel/v1/session/login`
(sesión HTTP + CSRF `XSRF-TOKEN`/`X-XSRF-TOKEN`; el form-login Thymeleaf
`/panel/login` fue eliminado). Los flujos OAuth por proyecto sirven a
`ProjectUser` bajo `/p/{projectSlug}/**` (multi-issuer implementado, ADR-0016).

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

### Cadena 4 — API de usuario final (`@Order(4)`)

Definida en [`ProjectEndUserSecurityConfiguration`](../../apps/api/src/main/java/dev/unzor/nexus/identity/application/configuration/ProjectEndUserSecurityConfiguration.java).

- Aplica a `/api/p/**` (la API JSON del portal de usuario final, consumida por Next.js con credenciales). Espejo de la cadena del panel (CORS + CookieCsrf + SameSite + 401 JSON) pero SIN `DaoAuthenticationProvider`: el login es manual vía `ProjectSessionAuthenticator`.
- `permitAll`: `GET /api/p/*/csrf`, y los `POST` de login, login/mfa, register, verify-email(+resend) y password-reset(+confirm). El resto requiere sesión.
- `ProjectSessionAuthenticator` persiste el `SecurityContext` en la sesión compartida — lo que el Authorization Server reconoce al reanudar `/p/{slug}/oauth2/authorize`.
- MFA TOTP: si el usuario la tiene activa, el login fija un ticket efímero y lanza `MfaRequiredException` **sin persistir contexto** (SAS no puede reanudar authorize); `POST /api/p/*/login/mfa` verifica el TOTP/recovery y entonces sí establece la sesión con ambos factores (`amr: [pwd, otp]`).

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
| `jwkSource` | Firma JWT del Authorization Server | Clave RSA cargada desde keystore PKCS12 configurable |
| `JwkSigningKeyHealthIndicator` | Readiness de la clave de firma | `UP` con keystore configurado; `DOWN` si se usa fallback efímero |

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

- Algoritmo: **RSA**.
- La clave activa se carga desde un keystore **PKCS12** configurado con
  `nexus.oauth.jwk.*`.
- El entorno local usa por defecto el keystore de desarrollo
  `classpath:keystore/dev-jwk.p12`, suficiente para desarrollo y tests.
- Producción debe configurar su propio keystore mediante las variables
  `NEXUS_OAUTH_JWK_*`; reutilizar el keystore de desarrollo en producción es
  inseguro.
- El `kid` es estable y determinista, derivado de la clave pública.
- El `JWKSet` expone una sola clave activa. La rotación sin solapamiento está
  aceptada por ADR-0011: los access tokens en vuelo firmados con la clave anterior
  dejan de validar hasta que el cliente use su refresh token persistido.
- Solo si `nexus.oauth.jwk.keystore-location` se configura explícitamente vacío,
  Nexus genera una clave efímera en memoria, registra una advertencia y marca la
  readiness `jwkSigningKey` como `DOWN`.

La motivación, límites de rotación y runbook de producción están documentados en
[`ADR-0011`](../adr/0011-persistent-jwt-signing-keys.md) y
[`JWT signing keys`](../deployment/jwt-signing-keys.md).

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
Next.js /login     ──POST /api/panel/v1/session/login + CSRF──►  sesión HTTP (JSESSIONID)
                        (si la cuenta tiene MFA → {code:mfa_required} → /session/login/mfa)
Next.js /projects  ──GET /api/panel/v1/me + cookies──►  401 → redirect login
                   ──POST /api/panel/v1/session/logout + CSRF──►  204
```

`POST /api/panel/v1/accounts` permanece público por diseño. Si todavía no existe
una cuenta administradora de instancia, la primera cuenta registrada recibe
automáticamente `instanceAdmin = true`.
El operador debe reclamar una instalación nueva antes de exponerla de forma general.

### Limitación CSRF cross-host

Si el frontend (`localhost:3000`) y la API (`localhost:8080`) usan hosts distintos, el navegador trata las cookies por origen. Las mutaciones deben usar `credentials: "include"` y la API debe exponer CORS con `allowCredentials`. En producción, alinear dominio/parent domain o usar un proxy same-origin.

## Flujo OAuth por proyecto (implementado)

```
GET  /p/{slug}/oauth2/authorize   → sin sesión → entry point redirige a
                                     {frontend}/p/{slug}/login?continue=<authorize>
POST /api/p/{slug}/login          → ProjectSessionAuthenticator establece sesión
                                     (o mfa_required → /login/mfa)
GET  /p/{slug}/oauth2/authorize   → con sesión → ConsentController →
                                     {frontend}/p/{slug}/oauth2/consent → code
POST /p/{slug}/oauth2/token       → access + id_token (amr refleja MFA)
```

Cada proyecto es su propio issuer (`CompositeRegisteredClientRepository` resuelve
el cliente por `client_id` → proyecto; `ProjectOauthClientsService` gestiona los
clientes por proyecto desde el panel).

---

## Configuración (application.properties)

```properties
nexus.frontend-base-url=${NEXUS_FRONTEND_BASE_URL:http://localhost:3000}

nexus.oauth.bootstrap.registered-client-id=nexus-oidc-client
nexus.oauth.bootstrap.client-id=oidc-client
nexus.oauth.bootstrap.client-secret=${NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET:changeme-local-dev}
nexus.oauth.bootstrap.redirect-uri=${NEXUS_OAUTH_BOOTSTRAP_REDIRECT_URI:http://127.0.0.1:8080/oauth2/bootstrap/callback}
nexus.oauth.bootstrap.post-logout-redirect-uri=${nexus.frontend-base-url}/

nexus.oauth.jwk.keystore-location=${NEXUS_OAUTH_JWK_KEYSTORE_LOCATION:classpath:keystore/dev-jwk.p12}
nexus.oauth.jwk.keystore-password=${NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD:devpassword}
nexus.oauth.jwk.key-alias=${NEXUS_OAUTH_JWK_KEY_ALIAS:nexus-dev-key}
nexus.oauth.jwk.key-password=${NEXUS_OAUTH_JWK_KEY_PASSWORD:devpassword}
```

### Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `NEXUS_FRONTEND_BASE_URL` | `http://localhost:3000` | URL base del panel Next.js (redirect post-login, validación `continue`) |
| `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET` | `changeme-local-dev` | Secreto del cliente bootstrap (se persiste como `{bcrypt}…`) |
| `NEXUS_OAUTH_JWK_KEYSTORE_LOCATION` | `classpath:keystore/dev-jwk.p12` | Ubicación del keystore PKCS12 de firma JWT |
| `NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD` | `devpassword` | Password del keystore de desarrollo; producción debe sobrescribirlo |
| `NEXUS_OAUTH_JWK_KEY_ALIAS` | `nexus-dev-key` | Alias de la clave activa |
| `NEXUS_OAUTH_JWK_KEY_PASSWORD` | `devpassword` | Password de la clave activa; producción debe sobrescribirlo |

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
- [x] Firma JWT con keystore PKCS12 persistente y `kid` estable.
- [x] Persistencia JDBC de clientes, autorizaciones y consentimientos OAuth2.
- [x] Cliente bootstrap técnico idempotente (`nexus.oauth.bootstrap.*`).
- [x] **Multi-issuer por proyecto** (`/p/{slug}`) — `CompositeRegisteredClientRepository` + `ProjectOauthClientsService` (ADR-0016).
- [x] **Login funcional de `ProjectUser`** en `/api/p/{slug}/login` (`ProjectSessionAuthenticator`, `ProjectUserUserDetailsService` con `projectId` obligatorio).
- [x] **Verificación de email + registro dual + reseteo de contraseña** self-service (M2/M3).
- [x] **TOTP MFA** end-user (step-up + inscripción + recovery codes; `amr: [pwd, otp]`) (M5).
- [x] **Consent** branded vía redirect a Next.js + **gestión de sesiones** end-user (list/revoke) (M4 + sesiones).
- [x] Panel Nexus con `NexusAccount` (login JSON + sesión HTTP + CSRF + MFA TOTP propia).
- [x] **Rate-limiting** per-IP (bucket4j) en endpoints de auth pública + **backups** PostgreSQL (`scripts/backup-db.sh` + runbook) (M6).
- [x] Health-check interno del módulo.

### 🔲 Pendiente

- Sin pendientes relevantes tras M6 (rate-limiting + backups entregados).

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
