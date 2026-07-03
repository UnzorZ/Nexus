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
 * Configuración SMTP a nivel de instancia (singleton, fila {@code id=1}). Es la
 * fuente por defecto del envío de email de todos los proyectos; un proyecto la
 * puede sobrescribir con {@link ProjectSmtpSettings}. Si no existe fila, el envío
 * cae a {@code nexus.notify.smtp.*} (env). La contraseña se guarda cifrada
 * (AES-GCM) en {@code password_enc}.
 *
 * <p>El singleton se refuerza con {@code CHECK (id = 1)} en la migración: nunca
 * puede haber más de una fila.</p>
 */
@Entity
@Table(name = "instance_smtp_settings")
public class InstanceSmtpSettings {

    /** Singleton: siempre 1 (reforzado por CHECK en la migración). */
    @Id
    private Short id = 1;

    private String host;
    private Integer port;
    private String username;

    @Column(name = "from_address")
    private String fromAddress;

    /** Cifrado (AES-GCM), formato {@code base64(nonce).base64(ciphertext)}. */
    @Column(name = "password_enc")
    private String passwordEnc;

    /**
     * Modo de confianza TLS (ADR-0013): {@code PUBLIC} (truststore público por
     * defecto) o {@code PINNED} (confiar sólo en {@code trustedCaPem}).
     */
    @Column(name = "tls_mode")
    private String tlsMode = "PUBLIC";

    /** CA (PEM) en la que confiar cuando {@code tlsMode = PINNED}; ignorable en PUBLIC. */
    @Column(name = "trusted_ca_pem")
    private String trustedCaPem;

    @Column(name = "updated_by")
    private UUID updatedBy;

    private Instant updatedAt;

    protected InstanceSmtpSettings() {
    }

    public InstanceSmtpSettings(String host, Integer port, String username, String fromAddress,
                                String passwordEnc, String tlsMode, String trustedCaPem, UUID updatedBy) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.fromAddress = fromAddress;
        this.passwordEnc = passwordEnc;
        this.tlsMode = tlsMode;
        this.trustedCaPem = trustedCaPem;
        this.updatedBy = updatedBy;
    }

    /** Sobrescribe todos los campos salvo la PK y {@code updatedAt}. */
    public void rewrite(String host, Integer port, String username, String fromAddress, String passwordEnc,
                        String tlsMode, String trustedCaPem, UUID updatedBy) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.fromAddress = fromAddress;
        this.passwordEnc = passwordEnc;
        this.tlsMode = tlsMode;
        this.trustedCaPem = trustedCaPem;
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

    public Short getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
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

    public String getTlsMode() {
        return tlsMode;
    }

    public String getTrustedCaPem() {
        return trustedCaPem;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanceSmtpSettings that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
