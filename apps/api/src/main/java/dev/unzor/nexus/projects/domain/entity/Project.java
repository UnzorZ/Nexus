package dev.unzor.nexus.projects.domain.entity;

import dev.unzor.nexus.projects.domain.enums.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Límite de seguridad y configuración que representa una aplicación o producto
 * administrado por Nexus.
 *
 * <p>Un proyecto es propietario de sus usuarios OAuth, clientes, API keys,
 * permisos, módulos y membresías. El {@code slug} es su identificador legible y
 * estable para rutas públicas e issuers, mientras que {@code id} es la identidad
 * interna utilizada por persistencia.</p>
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @Column(name = "public_base_url", length = 2048)
    private String publicBaseUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Project(String slug, String name, String description, String publicBaseUrl) {
        this.slug = Objects.requireNonNull(slug);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.publicBaseUrl = publicBaseUrl;
        this.status = ProjectStatus.ACTIVE;
    }

    public boolean isOperational() {
        return status == ProjectStatus.ACTIVE;
    }

    /**
     * Actualiza los metadatos editables sin modificar el slug estable.
     */
    public void updateDetails(String name, String description, String publicBaseUrl) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void suspend() {
        status = ProjectStatus.SUSPENDED;
    }

    public void archive() {
        status = ProjectStatus.ARCHIVED;
    }

    public void reactivate() {
        status = ProjectStatus.ACTIVE;
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
