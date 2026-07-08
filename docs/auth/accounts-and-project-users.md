# Cuentas Nexus y usuarios de proyecto

Nexus mantiene separadas la identidad del plano de control y la identidad de los
usuarios finales. Que una persona use el mismo email en ambos contextos no
convierte esas identidades en una sola cuenta.

## Vista general

```text
NexusAccount
    |
    +-- ProjectMembership --> Project
    |
    +-- instanceAdmin (privilegio global)

Project
    |
    +-- ProjectUser --> OAuth/OIDC, roles y permisos del proyecto
```

| Tipo | Para qué sirve | Módulo propietario | Accede al panel |
|------|-----------------|--------------------|-----------------|
| `NexusAccount` | Identifica a una persona que utiliza Nexus | `admin` | Sí |
| `ProjectMembership` | Autoriza a una cuenta Nexus sobre un proyecto | `projects` | Con el alcance de su rol |
| `ProjectUser` | Identifica a un usuario final dentro de un realm OAuth | `identity` | No |

Las entidades no implementan `UserDetails`. Los principals de infraestructura
adaptan las entidades al contrato de Spring Security sin acoplar el dominio al
framework.

## NexusAccount

Archivo:
[`NexusAccount.java`](../../apps/api/src/main/java/dev/unzor/nexus/admin/domain/entity/NexusAccount.java)

Representa una cuenta humana del panel de Nexus. Es la identidad usada para
crear proyectos, consultar los proyectos disponibles y realizar acciones de
administración.

Responsabilidades:

- almacenar email, hash de contraseña y nombre visible;
- controlar si la cuenta puede autenticarse;
- registrar la verificación del email y el último inicio de sesión;
- mantener el estado de MFA;
- indicar si administra la instancia completa;
- conservar fechas de creación y actualización.

El email es único para toda la instancia de Nexus. La contraseña se almacena
exclusivamente como hash.

`NexusAccount` no determina por sí sola qué proyectos puede administrar. Esa
decisión pertenece a `ProjectMembership`. El privilegio global de administración
de instancia se representa con `instanceAdmin`; no existe un catálogo de roles
globales porque no se planean privilegios adicionales.

### NexusAccountStatus

Archivo:
[`NexusAccountStatus.java`](../../apps/api/src/main/java/dev/unzor/nexus/admin/domain/enums/NexusAccountStatus.java)

| Estado | Significado |
|--------|-------------|
| `PENDING_VERIFICATION` | La cuenta existe, pero todavía no ha verificado su email |
| `ACTIVE` | Puede autenticarse en Nexus |
| `SUSPENDED` | Está bloqueada temporalmente por una decisión administrativa |
| `DISABLED` | Está desactivada de forma indefinida |

`canAuthenticate()` solo devuelve `true` para `ACTIVE`.
`verifyEmail()` activa una cuenta pendiente, mientras que `suspend()`,
`disable()` y `reactivate()` expresan sus transiciones operativas.

### NexusAccountPrincipal

Archivo:
[`NexusAccountPrincipal.java`](../../apps/api/src/main/java/dev/unzor/nexus/admin/infrastructure/security/NexusAccountPrincipal.java)

Es el adaptador entre `NexusAccount` y Spring Security. Implementa
`UserDetails` y contiene únicamente la información necesaria durante una
autenticación:

- `accountId`;
- email utilizado como `username`;
- hash de contraseña;
- authorities calculadas;
- indicador de cuenta habilitada.

Las authorities no se persisten dentro del principal. Se calculan al cargar la
cuenta a partir de grants globales y, cuando corresponda, membresías de proyecto.

## ProjectMembership

Archivo:
[`ProjectMembership.java`](../../apps/api/src/main/java/dev/unzor/nexus/projects/domain/entity/ProjectMembership.java)

Representa la relación entre una `NexusAccount` y un proyecto. Responde a esta
pregunta:

> ¿Qué puede hacer esta cuenta Nexus dentro de este proyecto?

Almacena `projectId` y `nexusAccountId` como UUID, sin asociaciones JPA con
entidades de otros módulos. Esto mantiene independientes los límites de
`projects` y `admin`.

Solo puede existir una membresía por pareja `(project_id, nexus_account_id)`.
Desactivar una membresía impide acceder a ese proyecto, pero no desactiva la
cuenta Nexus ni afecta a sus demás proyectos.

### ProjectMembershipRole

Archivo:
[`ProjectMembershipRole.java`](../../apps/api/src/main/java/dev/unzor/nexus/projects/domain/enums/ProjectMembershipRole.java)

| Rol | Alcance previsto |
|-----|------------------|
| `OWNER` | Control total, incluida la gestión de membresías y propiedad |
| `ADMIN` | Gestión operativa del proyecto y sus recursos |
| `MEMBER` | Acceso limitado según las políticas que se definan |

Los roles indican capacidad; no indican si la membresía está operativa. Esa
responsabilidad corresponde a `ProjectMembershipStatus`.

### ProjectMembershipStatus

Archivo:
[`ProjectMembershipStatus.java`](../../apps/api/src/main/java/dev/unzor/nexus/projects/domain/enums/ProjectMembershipStatus.java)

| Estado | Significado |
|--------|-------------|
| `INVITED` | La invitación existe, pero aún no se ha aceptado |
| `ACTIVE` | La membresía concede acceso al proyecto |
| `SUSPENDED` | El acceso está bloqueado temporalmente |
| `REVOKED` | La membresía ha sido retirada |

`grantsProjectAccess()` solo devuelve `true` para `ACTIVE`.

Actualmente el constructor crea una membresía activa. Cuando se implemente el
flujo de invitaciones, las invitaciones deberán crearse explícitamente con
estado `INVITED`.

## ProjectUser

Archivo:
[`ProjectUser.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/domain/entity/ProjectUser.java)

Representa a un usuario final autenticado dentro del realm OAuth/OIDC de un
proyecto. Por ejemplo, sería un cliente de F-Shop o un usuario de GarageLab, no
una persona que administra Nexus.

Responsabilidades:

- pertenecer exactamente a un proyecto;
- almacenar credenciales y perfil para el login OAuth/OIDC;
- controlar la verificación y el estado de acceso;
- registrar el último inicio de sesión;
- mantener `authzVersion` para invalidar snapshots de permisos.

La unicidad del email es por proyecto mediante
`(project_id, email)`. Por tanto, el mismo email puede representar usuarios
distintos en proyectos distintos.

`authzVersion` debe incrementarse cada vez que cambien los roles, permisos
directos u otra información que altere los permisos efectivos del usuario.

### ProjectUserStatus

Archivo:
[`ProjectUserStatus.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/domain/enums/ProjectUserStatus.java)

| Estado | Significado |
|--------|-------------|
| `PENDING_VERIFICATION` | El usuario aún no ha verificado su email |
| `ACTIVE` | Puede autenticarse en el realm del proyecto |
| `SUSPENDED` | Está bloqueado temporalmente dentro del proyecto |
| `DISABLED` | Está desactivado de forma indefinida |

El estado solo afecta a ese `ProjectUser`. No afecta a una posible
`NexusAccount` con el mismo email ni a usuarios de otros proyectos.

### ProjectUserPrincipal

Archivo:
[`ProjectUserPrincipal.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/infrastructure/security/ProjectUserPrincipal.java)

Adapta `ProjectUser` a `UserDetails`. Incluye tanto `projectId` como `userId`
para conservar el contexto de aislamiento durante la autenticación.

Utiliza `username` como login cuando está informado y recurre al email en caso
contrario. La búsqueda del usuario nunca debe realizarse globalmente solo por
email o username: siempre debe incluir el proyecto resuelto desde el issuer o
la ruta OAuth.

## Límites entre módulos

Las entidades viven en el módulo que controla su ciclo de vida:

```text
admin/domain/entity/NexusAccount.java
projects/domain/entity/ProjectMembership.java
identity/domain/entity/ProjectUser.java
```

Otros módulos deben guardar IDs y consultar servicios públicos. No deben:

- importar repositorios de otro módulo;
- crear relaciones JPA `@ManyToOne` entre módulos;
- mover estas entidades a `shared`;
- asumir que un email identifica globalmente a un `ProjectUser`;
- conceder acceso al panel a un `ProjectUser`.

`shared` puede contener en el futuro identificadores tipados, referencias de
actor y primitivas de validación estables.

## Persistencia

La migración
[`V2__create_account_and_user_tables.sql`](../../apps/api/src/main/resources/db/migration/V2__create_account_and_user_tables.sql)
crea:

- `nexus_accounts`;
- `project_memberships`;
- `project_users`.

La clave foránea desde `project_memberships.nexus_account_id` protege la
referencia a la cuenta Nexus. Las referencias `project_id` recibirán su clave
foránea cuando se cree la tabla propietaria `projects`.

## Trabajo pendiente

- rate-limiting en los endpoints públicos de auth + backups gestionados (Track B / M6).

## Implementado

- repositorios JPA para `NexusAccount`, `ProjectMembership` y `ProjectUser`;
- indicador persistente `instanceAdmin` (bootstrap en la primera cuenta);
- registro de `NexusAccount` con CSRF (`POST /api/panel/v1/accounts`);
- `NexusAccountUserDetailsService` solo en la cadena del panel;
- login JSON del panel (`POST /api/panel/v1/session/login` [+ `/login/mfa`]),
  sesión HTTP, logout API y CSRF — el form-login Thymeleaf `/panel/login` fue eliminado;
- **MFA TOTP del panel** (`NexusAccount`: inscripción + step-up + recovery codes);
- persistencia JDBC de clientes OAuth, autorizaciones y consentimientos;
- **multi-issuer OAuth por proyecto** (`CompositeRegisteredClientRepository`,
  `ProjectOauthClientsService`; ADR-0016);
- **login funcional de `ProjectUser`** con contexto obligatorio de proyecto
  (`ProjectSessionAuthenticator`), verificación de email, registro dual y reseteo
  de contraseña self-service (M2/M3);
- **TOTP MFA end-user** (step-up + inscripción + recovery codes; `amr: [pwd, otp]`, M5);
- **consent** branded vía redirect a Next.js y **gestión de sesiones** end-user (list/revoke);
- normalización de emails en registro.

### Bootstrap público de la instancia

El registro de cuentas es público de forma intencionada. Cuando la instancia aún
no tiene ninguna cuenta con `instanceAdmin = true`, la primera cuenta creada
recibe ese privilegio global.
Las siguientes cuentas se registran sin privilegios de administración de instancia.

Este comportamiento permite inicializar una instalación sin credenciales
preconfiguradas. Como consecuencia, una instancia nueva debe exponerse únicamente
cuando su operador esté preparado para registrar inmediatamente la primera cuenta.
El lock transaccional y el índice único parcial de base de datos garantizan que
solo una cuenta tenga el indicador activo durante registros concurrentes.
