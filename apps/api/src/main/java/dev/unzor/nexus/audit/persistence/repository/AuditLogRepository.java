package dev.unzor.nexus.audit.persistence.repository;

import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
