package dev.unzor.nexus.identity.domain.entity;

import dev.unzor.nexus.identity.domain.enums.ProjectUserStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario final perteneciente al realm OAuth/OIDC de un único proyecto.
 *
 * <p>Un usuario de proyecto no puede acceder al panel de Nexus y no se corresponde
 * automáticamente con una cuenta Nexus. El mismo email puede existir en varios
 * proyectos y representa una identidad distinta en cada uno.</p>
 *
 * <p>La identidad se aísla mediante {@code projectId}. Toda búsqueda utilizada
 * durante la autenticación debe incluir ese contexto de proyecto.</p>
 */
@Entity
@Table(
        name = "project_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_user_project_email",
                columnNames = {"project_id", "email"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 120)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectUserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "authz_version", nullable = false)
    private long authzVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectUser(UUID projectId, String email, String passwordHash, String displayName) {
        this.projectId = Objects.requireNonNull(projectId);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.displayName = Objects.requireNonNull(displayName);
        this.status = ProjectUserStatus.PENDING_VERIFICATION;
    }

    /**
     * Indica si el usuario puede autenticarse dentro de su proyecto.
     */
    public boolean canAuthenticate() {
        return status == ProjectUserStatus.ACTIVE;
    }

    /**
     * Registra la verificación del email y activa al usuario cuando seguía pendiente.
     */
    public void verifyEmail(Instant verifiedAt) {
        emailVerifiedAt = Objects.requireNonNull(verifiedAt);
        if (status == ProjectUserStatus.PENDING_VERIFICATION) {
            status = ProjectUserStatus.ACTIVE;
        }
    }

    /**
     * Bloquea temporalmente al usuario dentro de este proyecto.
     */
    public void suspend() {
        status = ProjectUserStatus.SUSPENDED;
    }

    /**
     * Desactiva al usuario de forma indefinida dentro de este proyecto.
     */
    public void disable() {
        status = ProjectUserStatus.DISABLED;
    }

    /**
     * Devuelve al usuario al estado operativo.
     */
    public void reactivate() {
        status = ProjectUserStatus.ACTIVE;
    }

    /**
     * Registra el instante del último inicio de sesión correcto.
     */
    public void recordLogin(Instant loggedInAt) {
        lastLoginAt = Objects.requireNonNull(loggedInAt);
    }

    /**
     * Invalida snapshots o cachés de permisos calculados con una versión anterior.
     *
     * <p>Debe invocarse cuando cambien roles, permisos directos u otra información
     * que altere los permisos efectivos del usuario.</p>
     */
    public void incrementAuthzVersion() {
        authzVersion++;
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
