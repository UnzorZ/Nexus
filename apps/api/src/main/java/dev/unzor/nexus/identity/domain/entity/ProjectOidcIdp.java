package dev.unzor.nexus.identity.domain.entity;

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
 * Configuration of an external OIDC identity provider (Google) for a project.
 *
 * <p>One row per project. The {@code clientSecretEnc} column holds the Google client
 * secret encrypted at rest (AES-256-GCM, {@code base64(nonce).base64(ciphertext)}); the
 * plaintext is never persisted. A project with no row has federation disabled.</p>
 */
@Entity
@Table(name = "project_oidc_idp")
public class ProjectOidcIdp {

    @Id
    private UUID projectId;

    private String issuer;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret_enc", nullable = false)
    private String clientSecretEnc;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "auto_provision", nullable = false)
    private boolean autoProvision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectOidcIdp() {
    }

    public ProjectOidcIdp(UUID projectId, String issuer, String clientId, String clientSecretEnc,
                          String scope, boolean enabled, boolean autoProvision) {
        this.projectId = projectId;
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecretEnc = clientSecretEnc;
        this.scope = scope;
        this.enabled = enabled;
        this.autoProvision = autoProvision;
    }

    /** Overwrites every field except the primary key and the timestamps. */
    public void rewrite(String issuer, String clientId, String clientSecretEnc,
                        String scope, boolean enabled, boolean autoProvision) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecretEnc = clientSecretEnc;
        this.scope = scope;
        this.enabled = enabled;
        this.autoProvision = autoProvision;
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

    public String getIssuer() {
        return issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecretEnc() {
        return clientSecretEnc;
    }

    public String getScope() {
        return scope;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoProvision() {
        return autoProvision;
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
        if (!(o instanceof ProjectOidcIdp that)) return false;
        return Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId);
    }
}
