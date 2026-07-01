package dev.unzor.nexus.documents.domain.entity;

import dev.unzor.nexus.documents.domain.DocumentVariablesConverter;
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
 * Resultado de un render (histórico). Denormaliza {@code templateName} para que
 * el historial sobreviva al borrado de la plantilla.
 */
@Entity
@Table(name = "document_renders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentRender {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Convert(converter = DocumentVariablesConverter.class)
    @Column(name = "variables")
    private Map<String, String> variables;

    @Column(name = "output", nullable = false)
    private String output;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DocumentRender(UUID projectId, UUID templateId, String templateName,
                          Map<String, String> variables, String output) {
        this.projectId = Objects.requireNonNull(projectId);
        this.templateId = templateId;
        this.templateName = Objects.requireNonNull(templateName);
        this.variables = variables == null ? Map.of() : variables;
        this.output = Objects.requireNonNull(output);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
