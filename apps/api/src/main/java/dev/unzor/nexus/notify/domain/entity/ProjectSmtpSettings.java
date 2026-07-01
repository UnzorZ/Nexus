package dev.unzor.nexus.notify.domain.entity;

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
 * Configuración SMTP de un proyecto (canal email). Una fila por proyecto; si no
 * existe, el envío cae a la configuración SMTP global de la instancia. La
 * contraseña se guarda cifrada (AES-GCM) en {@code password_enc}.
 */
@Entity
@Table(name = "project_smtp_settings")
public class ProjectSmtpSettings {

    @Id
    private UUID projectId;

    private String host;
    private int port;
    private String username;

    @Column(name = "from_address")
    private String fromAddress;

    /** Cifrado (AES-GCM), formato {@code base64(nonce).base64(ciphertext)}. */
    @Column(name = "password_enc")
    private String passwordEnc;

    private Instant createdAt;
    private Instant updatedAt;

    protected ProjectSmtpSettings() {
    }

    public ProjectSmtpSettings(UUID projectId, String host, int port, String username,
                               String fromAddress, String passwordEnc) {
        this.projectId = projectId;
        this.host = host;
        this.port = port;
        this.username = username;
        this.fromAddress = fromAddress;
        this.passwordEnc = passwordEnc;
    }

    /** Sobrescribe todos los campos salvo la PK y los timestamps. */
    public void rewrite(String host, int port, String username, String fromAddress, String passwordEnc) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.fromAddress = fromAddress;
        this.passwordEnc = passwordEnc;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectSmtpSettings that)) return false;
        return Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId);
    }
}
