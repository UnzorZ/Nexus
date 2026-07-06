package dev.unzor.nexus.shared.security;

import java.util.UUID;

/**
 * Nombres de atributos de sesión y prefijos de índice compartidos entre el panel
 * (sesiones de cuenta Nexus, indexadas por {@code nexus.accountId}) y el flujo de
 * identidad (sesiones de usuario de proyecto, indexadas por el id del usuario).
 *
 * <p>Vive en {@code shared.security} (ya expuesto) para no tener que crear una nueva
 * interfaz nombrada de Modulith: exponer un paquete nuevo ({@code shared.web}) como
 * interfaz nombrada dispara la maquinaria AOP de Modulith y rompe la inicialización
 * de filtros en tests MockMvc.</p>
 */
public final class NexusSessionAttributes {

    private NexusSessionAttributes() {
    }

    /**
     * Atributo de sesión con el id de un {@code ProjectUser}; lo fija
     * {@code ProjectSessionAuthenticator} al autenticar.
     */
    public static final String PROJECT_USER_ID = "nexus.projectUserId";

    /**
     * Prefijo del valor de índice bajo el que se agrupan las sesiones de un
     * {@code ProjectUser}. Distinto del {@code accountId} del panel para que ambas
     * familias no colisionen en el mismo índice {@code PRINCIPAL_NAME} de Redis.
     */
    public static final String PROJECT_USER_INDEX_PREFIX = "project-user:";

    /**
     * Valor de índice completo para las sesiones de un ProjectUser.
     */
    public static String projectUserIndexValue(UUID userId) {
        return PROJECT_USER_INDEX_PREFIX + userId;
    }
}
