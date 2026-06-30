package dev.unzor.nexus.shared.audit;

/**
 * Severidad de un evento de auditoría (reemplaza al binario SUCCESS/FAILURE).
 * El panel la usa para clasificar y filtrar el log.
 * <p>
 * Se DERIVA del {@code action} vía {@link #forAction(String)} para que los puntos
 * de emisión no tengan que clasificarla a mano:
 * <ul>
 *   <li>{@link #INFO} — ciclo de vida normal (created/updated/invited/role_*…).</li>
 *   <li>{@link #WARNING} — acciones destructivas o de revocación
 *       (removed/deleted/archived) y rechazos de auth (api_key.auth_*).</li>
 *   <li>{@link #MODERATE} — transferencia de propiedad (alto impacto).</li>
 *   <li>{@link #CRITICAL} — reservado para eventos críticos futuros.</li>
 * </ul>
 */
public enum Severity {
    INFO,
    WARNING,
    MODERATE,
    CRITICAL;

    /** Severidad por defecto según el nombre de la acción. */
    public static Severity forAction(String action) {
        if (action == null || action.isBlank()) {
            return INFO;
        }
        if (action.contains("ownership_transferred")) {
            return MODERATE;
        }
        if (action.endsWith(".removed")
                || action.endsWith(".deleted")
                || action.endsWith(".archived")
                || action.startsWith("api_key.auth_")) {
            return WARNING;
        }
        return INFO;
    }
}
