package dev.unzor.nexus.admin.domain.entity;

import dev.unzor.nexus.admin.domain.enums.NexusAccountStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta humana utilizada para acceder al panel de control de Nexus.
 *
 * <p>La cuenta representa identidad global dentro de una instancia Nexus, pero no
 * concede acceso por sí sola a ningún proyecto. El acceso a proyectos se modela en
 * el módulo {@code projects} mediante membresías referenciadas por el identificador
 * de esta cuenta.</p>
 *
 * <p>Una cuenta Nexus es independiente de los usuarios OAuth de cada proyecto.
 * Aunque compartan email, sus credenciales, sesiones y ciclos de vida no se
 * combinan.</p>
 */
@Entity
@Table(name = "nexus_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NexusAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NexusAccountStatus status;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NexusAccount(String email, String passwordHash, String displayName) {
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.displayName = Objects.requireNonNull(displayName);
        this.status = NexusAccountStatus.PENDING_VERIFICATION;
    }

    /**
     * Indica si la cuenta se encuentra en un estado apto para autenticarse.
     */
    public boolean canAuthenticate() {
        return status == NexusAccountStatus.ACTIVE;
    }

    /**
     * Registra la verificación del email y activa la cuenta cuando seguía pendiente.
     */
    public void verifyEmail(Instant verifiedAt) {
        emailVerifiedAt = Objects.requireNonNull(verifiedAt);
        if (status == NexusAccountStatus.PENDING_VERIFICATION) {
            status = NexusAccountStatus.ACTIVE;
        }
    }

    /**
     * Bloquea temporalmente la autenticación de la cuenta.
     */
    public void suspend() {
        status = NexusAccountStatus.SUSPENDED;
    }

    /**
     * Desactiva la cuenta de forma indefinida.
     */
    public void disable() {
        status = NexusAccountStatus.DISABLED;
    }

    /**
     * Devuelve una cuenta suspendida o desactivada al estado operativo.
     */
    public void reactivate() {
        status = NexusAccountStatus.ACTIVE;
    }

    /**
     * Registra el instante del último inicio de sesión correcto.
     */
    public void recordLogin(Instant loggedInAt) {
        lastLoginAt = Objects.requireNonNull(loggedInAt);
    }

    public void enableMfa() {
        mfaEnabled = true;
    }

    public void disableMfa() {
        mfaEnabled = false;
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
