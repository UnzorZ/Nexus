package dev.unzor.nexus.metrics.persistence.repository;

import dev.unzor.nexus.metrics.domain.entity.ProjectMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a persistencia para las métricas de los proyectos (sólo append: no hay
 * update en el flujo normal; el delete es sólo el barrido de retención). Las
 * consultas se acotan por proyecto.
 */
public interface ProjectMetricRepository extends Repository<ProjectMetric, UUID> {

    ProjectMetric save(ProjectMetric metric);

    /** Los N puntos más recientes del proyecto (N = tamaño del Pageable) para componer las series. */
    List<ProjectMetric> findByProjectIdOrderByRecordedAtDesc(UUID projectId, Pageable pageable);

    /** Purga de retención: borra los puntos anteriores al {@code cutoff} (todos los proyectos). */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ProjectMetric m WHERE m.recordedAt < :cutoff")
    long deleteOlderThan(@Param("cutoff") Instant cutoff);
}
