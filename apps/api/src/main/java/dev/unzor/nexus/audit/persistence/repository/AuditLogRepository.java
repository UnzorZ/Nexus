package dev.unzor.nexus.audit.persistence.repository;

import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repo mínimo de auditoría (mismo patrón que el resto del repo: extiende
 * {@link Repository}, sin {@code findById} ni {@code JpaRepository}). El listado
 * del panel se acota por {@code projectId}, opcionalmente por {@code since}, y se
 * PAGINA con {@link Pageable} (más recientes primero) devolviendo un
 * {@link Page} (con total de elementos/páginas).
 * <p>
 * Se exponen dos métodos (uno por cada rama de {@code since}) en vez del patrón
 * {@code (:since IS NULL OR …)} porque Postgres no puede inferir el tipo de un
 * parámetro nulo en una comparación {@code IS NULL} ("could not determine data
 * type of parameter").
 */
public interface AuditLogRepository extends Repository<AuditLogEntry, UUID> {

    AuditLogEntry save(AuditLogEntry entry);

    Page<AuditLogEntry> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);

    @Query("SELECT e FROM AuditLogEntry e "
            + "WHERE e.projectId = :projectId AND e.occurredAt >= :since "
            + "ORDER BY e.occurredAt DESC, e.id DESC")
    Page<AuditLogEntry> findByProjectAndSince(@Param("projectId") UUID projectId,
                                              @Param("since") Instant since,
                                              Pageable pageable);

    /**
     * Purga de retención: borra las entradas anteriores al {@code cutoff}. Bulk JPQL
     * ({@link Modifying}); el borrado se ejecuta en la tx del servicio que lo invoca.
     * Postgres aplica bloqueos a nivel de fila (no de tabla), por lo que un barrido
     * diario (típicamente 1 día de eventos) es seguro; para un backlog enorme en el
     * primer arranque, el operador puede aumentar progresivamente {@code retentionDays}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuditLogEntry e WHERE e.occurredAt < :cutoff")
    long deleteOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Export NDJSON por proyecto: devuelve {@link List} (no {@link Page}) con
     * {@link Pageable} para que Spring Data NO ejecute la consulta de COUNT. El
     * servicio pagina hasta que un slice viene con menos filas que el tamaño.
     */
    @Query("SELECT e FROM AuditLogEntry e WHERE e.projectId = :projectId "
            + "ORDER BY e.occurredAt DESC, e.id DESC")
    List<AuditLogEntry> findExportSliceByProject(@Param("projectId") UUID projectId, Pageable pageable);

    @Query("SELECT e FROM AuditLogEntry e "
            + "WHERE e.projectId = :projectId AND e.occurredAt >= :since "
            + "ORDER BY e.occurredAt DESC, e.id DESC")
    List<AuditLogEntry> findExportSliceByProjectAndSince(@Param("projectId") UUID projectId,
                                                         @Param("since") Instant since,
                                                         Pageable pageable);
}
