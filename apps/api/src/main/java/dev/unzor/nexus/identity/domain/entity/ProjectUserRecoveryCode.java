package dev.unzor.nexus.identity.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Código de recuperación MFA de un {@link ProjectUser}. Single-use: se guarda como
 * hash SHA-256 hex (igual que los tokens de verify/reset) y se marca consumido al
 * usarse; un replay ya no matchea porque el índice parcial exige
 * {@code consumed_at IS NULL}.
 */
@Entity
@Table(name = "project_user_recovery_codes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProjectUserRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_user_id", nullable = false)
    private UUID projectUserId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public ProjectUserRecoveryCode(UUID projectUserId, String codeHash, Instant createdAt) {
        this.projectUserId = Objects.requireNonNull(projectUserId);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** Marca el código como consumido (single-use). */
    public void consume(Instant consumedAt) {
        this.consumedAt = Objects.requireNonNull(consumedAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
