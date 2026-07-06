package dev.unzor.nexus.registry.persistence.repository;

import dev.unzor.nexus.registry.domain.entity.ProjectHeartbeat;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repo mínimo de heartbeats (mismo patrón que el resto del repo: extiende
 * {@link Repository}, sin {@code findById} ni {@code JpaRepository}). Las
 * búsquedas se acotan por {@code projectId} o por la identidad de la instancia.
 */
public interface ProjectHeartbeatRepository extends Repository<ProjectHeartbeat, UUID> {

    ProjectHeartbeat save(ProjectHeartbeat heartbeat);

    Optional<ProjectHeartbeat> findByProjectIdAndInstanceId(UUID projectId, String instanceId);

    List<ProjectHeartbeat> findAllByProjectId(UUID projectId);

    /**
     * Instancias cuyo último latido es anterior a {@code before} y para las que aún
     * no se ha avisado de esta caída ({@code offlineNotifiedAt} null). Candidatas a
     * alerta offline; el barrido filtra además por la config de notify del proyecto.
     * Aprovecha el índice parcial {@code ix_project_heartbeats_offline_pending}.
     */
    @Query("select h from ProjectHeartbeat h where h.lastSeenAt < :before and h.offlineNotifiedAt is null")
    List<ProjectHeartbeat> findOfflineCandidates(@Param("before") Instant before);
}
