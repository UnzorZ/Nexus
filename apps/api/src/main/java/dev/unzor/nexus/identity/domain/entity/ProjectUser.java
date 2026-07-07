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

import java.time.Duration;
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

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "authz_version", nullable = false)
    private long authzVersion;

    // Tokens de verificación de email / reseteo de contraseña: hash SHA-256 hex (no
    // plaintext), single-use (se anulan al consumir), con expiración propia.
    @Column(name = "email_verification_token_hash", length = 64)
    private String emailVerificationTokenHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    // M5 TOTP MFA: secret compartido CIFRADO AES-256-GCM (reversible: hace falta el
    // plaintext para computar los códigos). totp_enabled_at != null => MFA activa y
    // el login exigirá el segundo factor.
    @Column(name = "totp_secret_enc")
    private String totpSecretEnc;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

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
     * ¿El usuario está temporalmente bloqueado por demasiados intentos fallidos?
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Registra un intento fallido de login: incrementa el contador y, al alcanzar
     * {@code maxAttempts}, fija el desbloqueo a {@code now + lockDuration}.
     *
     * <p>Si un bloqueo anterior ya expiró, reinicia el contador antes de contar este
     * intento, de modo que el usuario pueda volver a bloquearse tras la ventana. No se
     * invoca mientras el usuario esté bloqueado (el flujo de autenticación lo rechaza
     * antes vía {@link #isLocked}).</p>
     *
     * @return {@code true} si esta llamada deja al usuario bloqueado
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    /**
     * Reinicia el contador de intentos fallidos y libera cualquier bloqueo (login OK).
     */
    public void resetFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Actualiza el nombre público y, opcionalmente, el username del perfil.
     * No altera el estado ({@link ProjectUserStatus}) ni la contraseña.
     */
    public void updateProfile(String displayName, String username) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.username = username;
    }

    /**
     * Reemplaza el hash de la contraseña (debe llegar ya hasheada por el
     * {@code PasswordEncoder}); lo usa tanto el alta como el reset de contraseña.
     */
    public void updatePassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
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

    /**
     * Emite un token de verificación de email: guarda su hash (SHA-256) y la
     * expiración. No altera el estado ({@link ProjectUserStatus}).
     */
    public void issueEmailVerification(String tokenHash, Instant expiresAt) {
        this.emailVerificationTokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.emailVerificationExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Consume el token de verificación: verifica el email (flip PENDING→ACTIVE) y
     * anula el token para que sea single-use (un replay ya no matchea).
     */
    public void consumeEmailVerification(Instant verifiedAt) {
        this.emailVerificationTokenHash = null;
        this.emailVerificationExpiresAt = null;
        verifyEmail(verifiedAt);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * MFA activa: hay un secret TOTP inscrito y verificado (totp_enabled_at != null).
     * El login exigirá el segundo factor (paso step-up).
     */
    public boolean isMfaEnabled() {
        return totpEnabledAt != null;
    }

    /**
     * Guarda el secret TOTP cifrado durante la inscripción (aún no activa hasta
     * {@link #enableTotp}).
     */
    public void storeTotpSecret(String encryptedSecret) {
        this.totpSecretEnc = Objects.requireNonNull(encryptedSecret, "encryptedSecret");
    }

    /**
     * Activa la MFA tras verificar el primer código TOTP correcto.
     */
    public void enableTotp(Instant enabledAt) {
        this.totpEnabledAt = Objects.requireNonNull(enabledAt, "enabledAt");
    }

    /**
     * Desactiva la MFA y borra el secret (re-inscripción posible más adelante).
     */
    public void disableTotp() {
        this.totpSecretEnc = null;
        this.totpEnabledAt = null;
    }

    /**
     * Emite un token de reseteo de contraseña: guarda su hash y la expiración.
     */
    public void issuePasswordReset(String tokenHash, Instant expiresAt) {
        this.passwordResetTokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.passwordResetExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Consume el token de reseteo (single-use): lo anula. La contraseña la fija el
     * servicio llamador vía {@link #updatePassword}.
     */
    public void consumePasswordReset() {
        this.passwordResetTokenHash = null;
        this.passwordResetExpiresAt = null;
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
