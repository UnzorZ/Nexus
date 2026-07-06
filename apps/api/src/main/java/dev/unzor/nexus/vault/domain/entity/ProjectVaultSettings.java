package dev.unzor.nexus.vault.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Override de la master key del vault para un proyecto (una fila por proyecto).
 * {@code masterKeyEnc} guarda la clave del proyecto envuelta con la master key
 * global (formato {@code base64(nonce).base64(ciphertext)}). Si es null, el
 * proyecto usa la master key global de la instancia.
 */
@Entity
@Table(name = "project_vault_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectVaultSettings {

    @Id
    private UUID projectId;

    @Column(name = "master_key_enc")
    private String masterKeyEnc;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectVaultSettings(UUID projectId, String masterKeyEnc) {
        this.projectId = projectId;
        this.masterKeyEnc = masterKeyEnc;
    }

    public void setMasterKeyEnc(String masterKeyEnc) {
        this.masterKeyEnc = masterKeyEnc;
    }

    @PrePersist
    void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
