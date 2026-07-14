package dev.unzor.nexus.metrics.application.service;

import dev.unzor.nexus.metrics.api.dto.MetricPoint;
import dev.unzor.nexus.metrics.api.dto.MetricSeries;
import dev.unzor.nexus.metrics.domain.entity.ProjectMetric;
import dev.unzor.nexus.metrics.persistence.repository.ProjectMetricRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso de las métricas de un proyecto: append de puntos y composición
 * de series agregadas por nombre para el panel. No audita (ruido de alta
 * frecuencia, como los latidos de registry).
 */
@Service
public class ProjectMetricsService {

    private static final int RECENT_WINDOW = 100;

    private final ProjectMetricRepository repository;
    private final ProjectLookupService projectLookupService;

    public ProjectMetricsService(ProjectMetricRepository repository, ProjectLookupService projectLookupService) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
    }

    @Transactional
    public MetricPoint record(UUID projectId, String name, double value, Map<String, String> tags) {
        projectLookupService.requireById(projectId);
        ProjectMetric saved = repository.save(new ProjectMetric(projectId, name, value, tags, Instant.now()));
        return MetricPoint.from(saved);
    }

    /**
     * Retención: borra los puntos anteriores al {@code cutoff} (todos los proyectos).
     * Lo invoca el job programado de retención; el borrado es global porque es una
     * preocupación de tamaño de la tabla, no de aislamiento.
     */
    @Transactional
    public long purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    @Transactional(readOnly = true)
    public List<MetricSeries> seriesForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        // Top N reciente (DESC); se agrupa por nombre preservando el orden de
        // primera aparición (el más reciente primero).
        List<ProjectMetric> recent = repository.findByProjectIdOrderByRecordedAtDesc(
                projectId, PageRequest.of(0, RECENT_WINDOW));
        Map<String, List<ProjectMetric>> byName = new LinkedHashMap<>();
        for (ProjectMetric metric : recent) {
            byName.computeIfAbsent(metric.getName(), k -> new ArrayList<>()).add(metric);
        }
        List<MetricSeries> series = new ArrayList<>();
        for (Map.Entry<String, List<ProjectMetric>> entry : byName.entrySet()) {
            List<ProjectMetric> desc = entry.getValue(); // DESC: el primero es el más reciente
            List<MetricPoint> asc = new ArrayList<>(desc.stream().map(MetricPoint::from).toList());
            Collections.reverse(asc); // cronológico para graficar
            ProjectMetric latest = desc.get(0);
            series.add(new MetricSeries(
                    entry.getKey(),
                    latest.getValue(),
                    latest.getRecordedAt(),
                    desc.size(),
                    asc
            ));
        }
        return series;
    }
}
