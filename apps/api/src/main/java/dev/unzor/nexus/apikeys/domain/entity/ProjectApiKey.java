package dev.unzor.nexus.apikeys.domain.entity;

import dev.unzor.nexus.apikeys.domain.ScopeListConverter;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * API key de un proyecto (spec §9.3). Identifica al proyecto frente a Nexus en
 * el API de proyecto ({@code X-Nexus-Api-Key}). Solo se persisten
 * {@code key_prefix} (búsqueda) y {@code key_hash} (verificación, SHA-256); el
 * secreto completo se muestra una única vez al crear/rotar.
 */
@Entity
@Table(
        name = "project_api_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_api_keys_project_prefix",
                columnNames = {"project_id", "key_prefix"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 128)
    private String keyHash;

    @Convert(converter = ScopeListConverter.class)
    @Column(nullable = false)
    private List<String> scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApiKeyStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_by_account_id")
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectApiKey(
            UUID projectId,
            String name,
            String keyPrefix,
            String keyHash,
            List<String> scopes,
            Instant expiresAt,
            UUID createdByAccountId
    ) {
        this.projectId = Objects.requireNonNull(projectId);
        this.name = Objects.requireNonNull(name);
        this.keyPrefix = Objects.requireNonNull(keyPrefix);
        this.keyHash = Objects.requireNonNull(keyHash);
        this.scopes = List.copyOf(scopes);
        this.expiresAt = expiresAt;
        this.createdByAccountId = createdByAccountId;
        this.status = ApiKeyStatus.ACTIVE;
    }

    public void disable() {
        this.status = ApiKeyStatus.DISABLED;
    }

    public void enable() {
        this.status = ApiKeyStatus.ACTIVE;
    }

    /** Rotación: reemplaza el prefijo y el hash (el secreto anterior deja de validar). */
    public void rotatePrefixAndHash(String keyPrefix, String keyHash) {
        this.keyPrefix = Objects.requireNonNull(keyPrefix);
        this.keyHash = Objects.requireNonNull(keyHash);
        this.status = ApiKeyStatus.ACTIVE;
    }

    public void touchUsed() {
        this.lastUsedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public void rename(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public void expireAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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
