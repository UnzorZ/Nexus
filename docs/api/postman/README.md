# Nexus OIDC Postman

Archivos:

- `nexus-oidc-local.postman_collection.json`
- `nexus-oidc-local.postman_environment.json`

## Uso rapido

1. Importa ambos archivos en Postman.
2. Selecciona el environment `Nexus OIDC Local`.
3. Ajusta `auth_base_url` si tu Authorization Server no corre en `http://127.0.0.1:8080`.
4. Ejecuta `1. Discovery`.
5. Ejecuta `2. Authorize (browser)`, inicia sesion y da consentimiento.
6. Copia el `code` de la URL de redireccion y pegalo en la variable `authorization_code`.
7. Ejecuta `3. Token (authorization_code)`.
8. Ejecuta `4. UserInfo`.
9. Ejecuta `5. Token (refresh_token)` para renovar token.

## Variables importantes

- `auth_base_url`: base del Authorization Server.
- `redirect_uri`: debe coincidir exactamente con el redirect URI registrado en el cliente.
- `client_id` y `client_secret`: valores de `nexus.oauth.bootstrap.client-id` y `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET` (por defecto `oidc-client` / `changeme-local-dev`).
- `redirect_uri`: debe coincidir con `nexus.oauth.bootstrap.redirect-uri` (por defecto `http://127.0.0.1:8080/oauth2/bootstrap/callback`).

El panel Next.js no usa este cliente; autentica con sesión HTTP en `/panel/login`.
