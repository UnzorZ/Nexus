package dev.unzor.nexus.admin.domain.entity;

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
 * Código de recuperación MFA de una {@link NexusAccount}. Single-use: se guarda como
 * hash SHA-256 hex y se marca consumido al usarse; un replay ya no matchea porque el
 * índice parcial exige {@code consumed_at IS NULL}. Espejo de
 * {@code ProjectUserRecoveryCode} para el panel.
 */
@Entity
@Table(name = "nexus_account_recovery_codes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class NexusAccountRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nexus_account_id", nullable = false)
    private UUID nexusAccountId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public NexusAccountRecoveryCode(UUID nexusAccountId, String codeHash, Instant createdAt) {
        this.nexusAccountId = Objects.requireNonNull(nexusAccountId);
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
