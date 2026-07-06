package dev.unzor.nexus.shared.audit;

import java.util.UUID;

/**
 * Señala que las authorities efectivas de un usuario de proyecto cambiaron y su
 * {@code authz_version} debe incrementarse (para invalidar snapshots/tokens
 * calculados con la versión anterior).
 *
 * <p>Lo publica el módulo {@code permissions} al asignar/quitar roles a un
 * usuario, al cambiar los permisos de un rol (afecta a todos sus asignatarios)
 * y al borrar un rol; lo consume de forma síncrona el módulo {@code identity},
 * que incrementa el {@code authz_version} del {@code ProjectUser} afectado
 * dentro de la misma transacción.
 *
 * <p>Vive en {@code shared.audit} — ya publicado como
 * {@code @NamedInterface("AuditEvents")} y ya consumido por {@code identity} —
 * para que ambos módulos lo referencien sin crear una dependencia cíclica en
 * compilación ni declarar un nuevo {@code @NamedInterface} (que provocaría el
 * CGLIB-proxy trap de Modulith sobre filtros {@code GenericFilterBean}).</p>
 */
public record ProjectUserAuthoritiesChanged(UUID projectId, UUID userId) {
}
