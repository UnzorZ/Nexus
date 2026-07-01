package dev.unzor.nexus.vault.domain.entity;

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

/** Secreto cifrado de un proyecto. El valor plano nunca se persiste ni se expone. */
@Entity
@Table(
        name = "project_secrets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_secrets_project_key",
                columnNames = {"project_id", "key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 128)
    private String key;

    @Column(nullable = false)
    private String ciphertext;

    @Column(nullable = false)
    private String nonce;

    @Column(nullable = false, length = 24)
    private String cipher;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    public ProjectSecret(UUID projectId, String key, String ciphertext, String nonce, String cipher) {
        this.projectId = Objects.requireNonNull(projectId);
        this.key = Objects.requireNonNull(key);
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.cipher = Objects.requireNonNull(cipher);
    }

    public void rotate(String ciphertext, String nonce, String cipher) {
        this.ciphertext = Objects.requireNonNull(ciphertext);
        this.nonce = Objects.requireNonNull(nonce);
        this.cipher = Objects.requireNonNull(cipher);
        this.lastRotatedAt = Instant.now();
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
