# Claims OIDC que emite Nexus

Nexus emite tokens OIDC (ID token y access token) para los usuarios finales de
cada proyecto. El endpoint `/api/p/{slug}/userinfo` muestra los mismos claims.

Este documento lista los claims que Nexus añade. El framework fija los claims
estándar (`iss`, `aud`, `exp`, `iat`, `sub`, `scope`).

## Claims de proyecto

Nexus añade estos claims cuando el principal es un usuario final del proyecto.
Si el principal no es un usuario final (por ejemplo, client credentials), Nexus
no añade estos claims.

| Claim | Tipo | Contenido |
|---|---|---|
| `sub` | string | El identificador del usuario. Hoy es el nombre de usuario (o el email). Este valor puede cambiar si cambia el nombre de usuario. |
| `project_id` | string (UUID) | El proyecto (realm) del usuario. |
| `authz_version` | integer | La versión de autorización. Aumenta cuando cambian los permisos o los roles. Invalida los tokens anteriores. |
| `permissions` | array de strings | Las claves de permiso efectivas. Incluye comodines (`orders.*`, `*`) en formato verbatim. |
| `roles` | array de strings | Las claves de los roles asignados al usuario (por ejemplo, `admin`). Lista vacía si el usuario no tiene roles. |
| `amr` | array de strings | Solo si el login uso MFA TOTP: `["pwd", "otp"]`. |

## Como se llenan `permissions` y `roles`

`ProjectIdTokenCustomizer` pide a `EffectiveAuthoritiesService.forUser` las
authorities efectivas del usuario. El servicio une los permisos de todos los
roles del usuario (`permissions`) y lista las claves de esos roles (`roles`). Un
cambio de roles o de permisos aumenta `authz_version`. Por eso, el usuario
recibe claims nuevos en el proximo login.

## Como los usa una app cliente

Una app (por ejemplo, FlipDaFile) lee los claims del JWT o de `/userinfo`:

- Para autorizar por permiso: compruebe si `permissions` contiene la clave (por
  ejemplo, `flipdafile.admin`).
- Para autorizar por rol: compruebe si `roles` contiene la clave (por ejemplo,
  `admin`).

El ejemplo `examples/spring-client-app` muestra el flujo como resource server.

## Configurar un rol para una app

Use la API del panel:

1. Cree un rol en el proyecto (por ejemplo, clave `admin`).
2. Asigne permisos al rol.
3. Asigne el rol al usuario:
   `PUT /api/panel/v1/projects/{projectId}/users/{userId}/roles`.

El cambio aumenta `authz_version`. El proximo token del usuario incluye el rol
en el claim `roles`.
