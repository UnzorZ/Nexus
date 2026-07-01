package dev.unzor.nexus.metrics.domain.entity;

import dev.unzor.nexus.metrics.domain.MetricTagsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Punto de métrica de un proyecto (sólo append). La identidad es el id; la
 * consulta se hace por (project_id, name, recorded_at).
 */
@Entity
@Table(name = "project_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private double value;

    @Convert(converter = MetricTagsConverter.class)
    @Column(name = "tags")
    private Map<String, String> tags;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public ProjectMetric(UUID projectId, String name, double value, Map<String, String> tags, Instant recordedAt) {
        this.projectId = Objects.requireNonNull(projectId);
        this.name = Objects.requireNonNull(name);
        this.value = value;
        this.tags = tags == null ? Map.of() : tags;
        this.recordedAt = Objects.requireNonNull(recordedAt);
    }

    @PrePersist
    void onCreate() {
        // recordedAt se fija en el servicio ( Instant.now() del caller ); el id lo
        // genera JPA. No hay updated_at: la métrica es inmutable.
    }
}
