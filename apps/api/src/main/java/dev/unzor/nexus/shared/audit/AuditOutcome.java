package dev.unzor.nexus.shared.audit;

/**
 * Resultado de un evento auditado: {@link #SUCCESS} para mutaciones completadas,
 * {@link #FAILURE} para rechazos (p. ej. credenciales de API key inválidas).
 */
public enum AuditOutcome {
    SUCCESS,
    FAILURE
}
