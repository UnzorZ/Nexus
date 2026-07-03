package dev.unzor.nexus.instance.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuración de instancia gestionable desde el panel del operador (singleton,
 * fila {@code id=1}): política de registro, módulos por defecto para proyectos
 * nuevos y defaults de heartbeat de instancia. Singleton reforzado por
 * {@code CHECK (id = 1)} en la migración.
 */
@Entity
@Table(name = "instance_settings")
public class InstanceSettings {

    @Id
    private Short id = 1;

    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen = true;

    /** Claves csv (p. ej. "identity,vault"); null = usar los defaults del catálogo. */
    @Column(name = "default_modules")
    private String defaultModules;

    @Column(name = "heartbeat_interval_seconds")
    private Integer heartbeatIntervalSeconds;

    @Column(name = "heartbeat_timeout_seconds")
    private Integer heartbeatTimeoutSeconds;

    @Column(name = "updated_by")
    private UUID updatedBy;

    private Instant updatedAt;

    protected InstanceSettings() {
    }

    /** Nueva fila singleton con defaults (registro abierto, sin overrides). */
    public static InstanceSettings create() {
        return new InstanceSettings();
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public String getDefaultModules() {
        return defaultModules;
    }

    public Integer getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public Integer getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setRegistrationOpen(boolean registrationOpen, UUID updatedBy) {
        this.registrationOpen = registrationOpen;
        this.updatedBy = updatedBy;
    }

    public void setDefaultModules(String defaultModules, UUID updatedBy) {
        this.defaultModules = defaultModules;
        this.updatedBy = updatedBy;
    }

    public void setHeartbeat(Integer intervalSeconds, Integer timeoutSeconds, UUID updatedBy) {
        this.heartbeatIntervalSeconds = intervalSeconds;
        this.heartbeatTimeoutSeconds = timeoutSeconds;
        this.updatedBy = updatedBy;
    }

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanceSettings that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
