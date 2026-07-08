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
     * Identificador público de gestión de una sesión, distinto del {@code JSESSIONID}
     * interno de Spring Session y del ID interno de Redis. Lo fijan tanto el login del
     * panel como el login de usuario final; se usa para listar y revocar sesiones sin
     * exponer identificadores internos.
     */
    public static final String SESSION_PUBLIC_ID = "nexus.sessionPublicId";

    /**
     * Cabecera {@code User-Agent} truncada, almacenada a efectos informativos en el
     * listado de sesiones.
     */
    public static final String USER_AGENT = "nexus.userAgent";

    /**
     * Longitud máxima del {@code User-Agent} almacenado en sesión.
     */
    public static final int USER_AGENT_MAX_LENGTH = 256;

    /**
     * Atributo de sesión que fija {@code ProjectSessionAuthenticator} cuando la
     * contraseña es correcta pero el usuario tiene MFA TOTP activa: guarda un ticket
     * efímero (interno de identity) con el usuario, el instante de verificación de la
     * contraseña y la expiración. <b>Crítico:</b> durante esta ventana NO se persiste
     * ningún {@code SecurityContext} autenticado, de modo que el Authorization Server
     * no puede reanudar {@code /oauth2/authorize} (la sesión sigue anónima para SAS);
     * sólo {@code POST /api/p/{slug}/login/mfa}, al verificar el TOTP, establece el
     * contexto completo.
     */
    public static final String MFA_PENDING = "nexus.mfaPending";

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

    /**
     * Trunca el {@code User-Agent} a la longitud máxima permitida; {@code null} se
     * normaliza a cadena vacía. Compartido por el login del panel y el de usuario final
     * para que ambas superficies almacenen el agente de la misma forma.
     */
    public static String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() <= USER_AGENT_MAX_LENGTH
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
