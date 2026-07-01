package dev.unzor.nexus.documents.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plantilla de documento de un proyecto. El cuerpo usa sustitución
 * {@code {{var}}}; las apps aportan las variables al renderizar.
 */
@Entity
@Table(
        name = "document_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_templates_project_name",
                columnNames = {"project_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "template_body", nullable = false)
    private String templateBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DocumentTemplate(UUID projectId, String name, String contentType, String templateBody) {
        this.projectId = Objects.requireNonNull(projectId);
        this.name = Objects.requireNonNull(name);
        this.contentType = Objects.requireNonNull(contentType);
        this.templateBody = Objects.requireNonNull(templateBody);
    }

    public void rewrite(String name, String contentType, String templateBody) {
        this.name = Objects.requireNonNull(name);
        this.contentType = Objects.requireNonNull(contentType);
        this.templateBody = Objects.requireNonNull(templateBody);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
