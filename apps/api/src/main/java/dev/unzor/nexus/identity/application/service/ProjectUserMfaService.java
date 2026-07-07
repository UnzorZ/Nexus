package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.entity.ProjectUserRecoveryCode;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRecoveryCodeRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de inscripción/gestión de MFA TOTP para usuarios finales:
 * <ul>
 *   <li>{@link #beginEnrollment} — genera un secret, lo cifra y lo guarda (aún no activo).</li>
 *   <li>{@link #confirmEnrollment} — verifica el primer código, activa la MFA y emite
 *       recovery codes single-use (se devuelven en claro una sola vez).</li>
 *   <li>{@link #disable} — verifica un código (TOTP o recovery) y desactiva + borra.</li>
 * </ul>
 * El secret se cifra (reversible) vía {@link TotpCrypto}; los recovery codes se hashean
 * (single-use) vía {@link IdentityTokens#hash}.
 */
@Service
@Transactional
public class ProjectUserMfaService {

    /** Emisor que ven las apps de autenticador en el QR. */
    public static final String ISSUER = "Nexus";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 10; // → 16 chars base32 (80 bits)

    private final ProjectUserRepository userRepository;
    private final ProjectUserRecoveryCodeRepository recoveryCodeRepository;
    private final TotpCrypto totpCrypto;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public ProjectUserMfaService(
            ProjectUserRepository userRepository,
            ProjectUserRecoveryCodeRepository recoveryCodeRepository,
            TotpCrypto totpCrypto,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.totpCrypto = totpCrypto;
        this.eventPublisher = eventPublisher;
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    public record MfaStatus(boolean enabled, int recoveryCodesRemaining) {
    }

    /** Genera un secret nuevo (cifrado, aún no activo) y devuelve el provisioning. */
    public Enrollment beginEnrollment(UUID projectId, UUID userId) {
        ProjectUser user = requireUser(projectId, userId);
        if (user.isMfaEnabled()) {
            throw new IllegalStateException("MFA already enabled");
        }
        byte[] secret = TotpGenerator.generateSecret();
        String base32 = Base32.encode(secret);
        user.storeTotpSecret(totpCrypto.encrypt(base32));
        userRepository.save(user);
        String uri = TotpGenerator.provisioningUri(ISSUER, accountLabel(user), base32);
        return new Enrollment(base32, uri);
    }

    /**
     * Verifica el primer código contra el secret pendiente; si valida, activa la MFA y
     * emite {@value #RECOVERY_CODE_COUNT} recovery codes (devueltos en claro una sola vez).
     */
    public List<String> confirmEnrollment(UUID projectId, UUID userId, String code) {
        ProjectUser user = requireUser(projectId, userId);
        if (user.isMfaEnabled()) {
            throw new IllegalStateException("MFA already enabled");
        }
        if (user.getTotpSecretEnc() == null) {
            throw new IllegalStateException("No pending enrollment");
        }
        if (!verifyTotp(user, code, Instant.now())) {
            throw new IllegalArgumentException("Invalid code");
        }
        Instant now = Instant.now();
        user.enableTotp(now);
        userRepository.save(user);

        List<String> plaintext = issueRecoveryCodes(user.getId(), now);

        eventPublisher.publishEvent(new OutboundTransactionalEmail(
                projectId, user.getEmail(), "Nexus MFA enabled",
                "<p>Two-factor authentication (TOTP) was enabled on your account. "
                        + "If this wasn't you, disable it from your account security page.</p>"));
        return plaintext;
    }

    /** Desactiva la MFA tras verificar un código (TOTP o recovery) + borra los recovery codes. */
    public void disable(UUID projectId, UUID userId, String code) {
        ProjectUser user = requireUser(projectId, userId);
        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("MFA not enabled");
        }
        Instant now = Instant.now();
        boolean ok = verifyTotp(user, code, now);
        if (!ok) {
            ok = consumeRecoveryCode(user, code, now);
        }
        if (!ok) {
            throw new IllegalArgumentException("Invalid code");
        }
        user.disableTotp();
        userRepository.save(user);
        recoveryCodeRepository.deleteByProjectUserId(user.getId());

        eventPublisher.publishEvent(new OutboundTransactionalEmail(
                projectId, user.getEmail(), "Nexus MFA disabled",
                "<p>Two-factor authentication was disabled on your account.</p>"));
    }

    /** Lectura del estado (¿activa? cuántos recovery codes quedan). */
    @Transactional(readOnly = true)
    public MfaStatus status(UUID projectId, UUID userId) {
        ProjectUser user = requireUser(projectId, userId);
        int remaining = user.isMfaEnabled()
                ? recoveryCodeRepository.findByProjectUserIdAndConsumedAtIsNull(user.getId()).size()
                : 0;
        return new MfaStatus(user.isMfaEnabled(), remaining);
    }

    private boolean verifyTotp(ProjectUser user, String code, Instant now) {
        if (code == null || code.length() != TotpGenerator.DIGITS || user.getTotpSecretEnc() == null) {
            return false;
        }
        try {
            byte[] secret = Base32.decode(totpCrypto.decrypt(user.getTotpSecretEnc()));
            return TotpGenerator.verify(secret, code.trim(), now.getEpochSecond(), 1);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean consumeRecoveryCode(ProjectUser user, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return recoveryCodeRepository.findByCodeHashAndConsumedAtIsNull(IdentityTokens.hash(code.trim()))
                .map(rc -> { rc.consume(now); recoveryCodeRepository.save(rc); return true; })
                .orElse(false);
    }

    private List<String> issueRecoveryCodes(UUID userId, Instant now) {
        List<ProjectUserRecoveryCode> entities = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            byte[] bytes = new byte[RECOVERY_CODE_BYTES];
            random.nextBytes(bytes);
            String code = Base32.encode(bytes);
            plaintext.add(code);
            entities.add(new ProjectUserRecoveryCode(userId, IdentityTokens.hash(code), now));
        }
        recoveryCodeRepository.saveAll(entities);
        return plaintext;
    }

    private static String accountLabel(ProjectUser user) {
        return user.getEmail();
    }

    private ProjectUser requireUser(UUID projectId, UUID userId) {
        return userRepository.findByProjectIdAndId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project user not found"));
    }
}
