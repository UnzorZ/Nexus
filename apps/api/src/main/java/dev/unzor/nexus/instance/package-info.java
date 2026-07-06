@org.springframework.modulith.ApplicationModule(
        displayName = "Instance",
        allowedDependencies = {
                "shared :: Security",
                "shared :: AuditEvents"
        }
)
package dev.unzor.nexus.instance;

/**
 * Configuración a nivel de instancia gestionable por el operador desde el
 * panel (rol {@code ROLE_INSTANCE_ADMIN}): el relay SMTP de la instancia
 * (delegado al módulo {@code notify}, que es quien lo resuelve al enviar) y un
 * status de sólo lectura de la demás config operativa (registro, sesión, vault,
 * keystore JWT, módulos por defecto). Es el primer conjunto de endpoints que
 * <em>requiere</em> ser admin de instancia (no sólo lo bypassa).
 */
