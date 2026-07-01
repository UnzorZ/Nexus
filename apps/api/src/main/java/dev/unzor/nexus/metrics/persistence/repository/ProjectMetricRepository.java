package dev.unzor.nexus.metrics.persistence.repository;

import dev.unzor.nexus.metrics.domain.entity.ProjectMetric;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a persistencia para las métricas de los proyectos (sólo append: no hay
 * update ni delete en el flujo normal). Las consultas se acotan por proyecto.
 */
public interface ProjectMetricRepository extends Repository<ProjectMetric, UUID> {

    ProjectMetric save(ProjectMetric metric);

    /** Los 100 puntos más recientes del proyecto (para componer las series). */
    List<ProjectMetric> findTop100ByProjectIdOrderByRecordedAtDesc(UUID projectId);
}
