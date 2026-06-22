# Nexus Postman

Colección de requests para la API de Nexus (panel + Authorization Server OIDC).

Archivos:

- `nexus.postman_collection.json` — la colección.
- `nexus-local.postman_environment.json` — environment con host y datos semilla.

> Sustituye a la antigua colección `nexus-oidc-local.*` (eliminada). El flujo OIDC
> sigue incluido dentro de la carpeta **4 · OIDC / OAuth**.

## Puesta en marcha

1. Importa en Postman los **dos** archivos (colección + environment).
2. Arriba a la derecha selecciona el environment **Nexus Local**.
3. Ajusta `api_base_url` si tu backend no corre en `http://127.0.0.1:8080`.
4. En **1 · Auth & Session** ejecuta en orden:
   1. **Get CSRF token** — el backend emite la cookie `XSRF-TOKEN` y el test script
      guarda el token en la variable de colección `csrf_token`.
   2. **Create account** — crea la cuenta con `account_email` / `account_password`
      / `account_display_name`. Si ya existe devuelve `409`; ignóralo.
   3. **Login** — autentica y deja la sesión (`JSESSIONID`) en la cookie jar.
5. A partir de aquí el resto de endpoints funcionan solos: los **test scripts**
   autocompletan `account_id`, `project_id`, `session_id` y los tokens OIDC.

## Qué se autopuebla

| Variable (colección) | La fija… |
| --- | --- |
| `csrf_token` | Get CSRF token |
| `account_id` | Create account / Login |
| `instance_admin` | Login |
| `project_id`, `project_slug` | Create project (o List si ya existía) |
| `session_id` | List sessions |
| `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint`, `jwks_uri` | OIDC Discovery |
| `access_token`, `refresh_token`, `id_token` | Token (authorization_code) / Token (refresh_token) |

La única que hay que pegar a mano es `authorization_code` (viene del `?code=` de la
URL de redirección tras **Authorize (browser)**).

## Cómo funciona la auth sin tocar nada

- **CSRF (double-submit):** un *pre-request script* a nivel de colección inyecta la
  cabecera `X-XSRF-TOKEN` desde `csrf_token` en cada request. La cookie `XSRF-TOKEN`
  la gestiona la cookie jar de Postman (activada por defecto). Coinciden header y
  cookie → Spring Security valida.
- **Sesión:** la cookie jar reenvía `JSESSIONID`, así que tras el Login la sesión
  queda activa para `/me`, `/projects`, `/sessions`, etc.
- Por eso es importante **no desactivar la cookie jar** y ejecutar **Get CSRF token**
  antes de cualquier escritura.

## Estructura

```
1 · Auth & Session   → csrf, alta de cuenta, login, me, logout
2 · Projects         → crear / listar / obtener
3 · Sessions         → listar / revocar una / revocar todas
4 · OIDC / OAuth     → discovery, authorize, token, userinfo, refresh, jwks
```

## Variables del environment

- `api_base_url` — host del backend (Spring Boot).
- `account_email` / `account_password` / `account_display_name` — cuenta semilla.
- `project_slug` / `project_name` — proyecto semilla.
- `client_id` / `client_secret` — cliente OIDC bootstrap
  (`nexus.oauth.bootstrap.client-id` / `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET`).
- `redirect_uri` — debe coincidir con
  `nexus.oauth.bootstrap.redirect-uri` (por defecto `http://127.0.0.1:8080/oauth2/bootstrap/callback`).
- `scope`, `state`, `nonce` — parámetros del flujo authorize.

El panel Next.js **no** usa el cliente OIDC; autentica por sesión HTTP en
`/api/panel/v1/session/login`. Esa carpeta solo sirve para probar el
Authorization Server.
