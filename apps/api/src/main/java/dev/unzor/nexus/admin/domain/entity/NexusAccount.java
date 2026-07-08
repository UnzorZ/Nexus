package dev.unzor.nexus.admin.domain.entity;

import dev.unzor.nexus.admin.domain.events.NexusAccountSessionsRevocationRequested;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

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
 *
 * <p>Extiende {@link AbstractAggregateRoot} para publicar eventos de dominio (p. ej.
 * la revocación de sesiones al suspender/desactivar la cuenta o retirar
 * {@code instanceAdmin}).</p>
 */
@Entity
@Table(
        name = "nexus_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_nexus_accounts_email",
                columnNames = "email"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NexusAccount extends AbstractAggregateRoot<NexusAccount> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
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

    /**
     * Secret TOTP cifrado (AES-256-GCM, reversible — hace falta el plaintext para computar
     * los códigos). Nulo mientras la MFA no está inscrita. El {@code mfaEnabled} de arriba
     * se mantiene sincronizado como bandera legible por los DTOs; el estado autoritativo
     * es {@link #totpEnabledAt}.
     */
    @Column(name = "totp_secret_enc")
    private String totpSecretEnc;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    @Column(name = "instance_admin", nullable = false)
    private boolean instanceAdmin;

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
     * Bloquea temporalmente la autenticación de la cuenta y solicita la revocación de
     * todas sus sesiones activas del panel.
     *
     * <p>Publica {@link NexusAccountSessionsRevocationRequested}. Para que la
     * publicación se materialice, el aggregate debe guardarse mediante el repositorio
     * ({@code NexusAccountRepository.save(...)}) dentro de la misma transacción; Spring
     * Data extrae los eventos registrados al guardar. La revocación es idempotente y se
     * reentrega si Redis falla tras el commit (ver
     * {@code PanelSessionRevocationRepublisher}).</p>
     */
    public void suspend() {
        status = NexusAccountStatus.SUSPENDED;
        registerEvent(new NexusAccountSessionsRevocationRequested(id));
    }

    /**
     * Desactiva la cuenta de forma indefinida y solicita la revocación de todas sus
     * sesiones activas del panel.
     *
     * <p>Igual que {@link #suspend()}: el evento se publica al guardar el aggregate con
     * el repositorio dentro de la misma transacción; la revocación es idempotente y se
     * reentrega si Redis falla tras el commit.</p>
     */
    public void disable() {
        status = NexusAccountStatus.DISABLED;
        registerEvent(new NexusAccountSessionsRevocationRequested(id));
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

    /**
     * Guarda el secret TOTP cifrado durante la inscripción (aún no activa).
     */
    public void storeTotpSecret(String encryptedSecret) {
        this.totpSecretEnc = encryptedSecret;
    }

    /**
     * Activa la MFA TOTP: fija el instante de activación y sincroniza la bandera
     * {@code mfaEnabled}. El secret debió almacenarse antes vía {@link #storeTotpSecret}.
     */
    public void enableTotp(Instant enabledAt) {
        this.totpEnabledAt = Objects.requireNonNull(enabledAt);
        this.mfaEnabled = true;
    }

    /**
     * Desactiva la MFA TOTP y borra el secret + recovery codes asociados (estos últimos
     * se borran por repositorio).
     */
    public void disableTotp() {
        this.totpEnabledAt = null;
        this.totpSecretEnc = null;
        this.mfaEnabled = false;
    }

    public void grantInstanceAdmin() {
        instanceAdmin = true;
    }

    /**
     * Retira el flag de administrador de instancia y solicita la revocación de todas
     * las sesiones activas del panel, ya que la autorización efectiva de la cuenta
     * cambia.
     *
     * <p>Igual que {@link #suspend()}: el evento se publica al guardar el aggregate con
     * el repositorio dentro de la misma transacción; la revocación es idempotente y se
     * reentrega si Redis falla tras el commit.</p>
     */
    public void revokeInstanceAdmin() {
        instanceAdmin = false;
        registerEvent(new NexusAccountSessionsRevocationRequested(id));
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
