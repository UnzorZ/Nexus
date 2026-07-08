package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.entity.NexusAccountRecoveryCode;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRecoveryCodeRepository;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.shared.security.Base32;
import dev.unzor.nexus.shared.security.SecureHashes;
import dev.unzor.nexus.shared.security.TotpCrypto;
import dev.unzor.nexus.shared.security.TotpGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de inscripción/gestión de MFA TOTP para cuentas del panel (NexusAccount).
 * Espejo de {@code ProjectUserMfaService} adaptado al panel:
 * <ul>
 *   <li>{@link #beginEnrollment} — genera un secret, lo cifra y lo guarda (aún no activo).</li>
 *   <li>{@link #confirmEnrollment} — verifica el primer código, activa la MFA y emite
 *       recovery codes single-use (devueltos en claro una sola vez).</li>
 *   <li>{@link #disable} — verifica un código (TOTP o recovery) y desactiva + borra.</li>
 * </ul>
 * El secret se cifra (reversible) vía {@link TotpCrypto}; los recovery codes se hashean
 * (single-use) vía {@link SecureHashes#sha256Hex}. No envía email de notificación (la
 * confirmación visual son los recovery codes mostrados una vez).
 */
@Service
@Transactional
public class NexusAccountMfaService {

    public static final String ISSUER = "Nexus";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 10; // → 16 chars base32 (80 bits)
    private static final int TOTP_WINDOW_STEPS = 1;

    private final NexusAccountRepository accountRepository;
    private final NexusAccountRecoveryCodeRepository recoveryCodeRepository;
    private final TotpCrypto totpCrypto;
    private final SecureRandom random = new SecureRandom();

    public NexusAccountMfaService(
            NexusAccountRepository accountRepository,
            NexusAccountRecoveryCodeRepository recoveryCodeRepository,
            TotpCrypto totpCrypto
    ) {
        this.accountRepository = accountRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.totpCrypto = totpCrypto;
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    public record MfaStatus(boolean enabled, int recoveryCodesRemaining) {
    }

    /** Genera un secret nuevo (cifrado, aún no activo) y devuelve el provisioning. */
    public Enrollment beginEnrollment(UUID accountId) {
        NexusAccount account = requireAccount(accountId);
        if (account.isMfaEnabled()) {
            throw new IllegalStateException("MFA already enabled");
        }
        byte[] secret = TotpGenerator.generateSecret();
        String base32 = Base32.encode(secret);
        account.storeTotpSecret(totpCrypto.encrypt(base32));
        accountRepository.save(account);
        String uri = TotpGenerator.provisioningUri(ISSUER, account.getEmail(), base32);
        return new Enrollment(base32, uri);
    }

    /**
     * Verifica el primer código contra el secret pendiente; si valida, activa la MFA y
     * emite {@value #RECOVERY_CODE_COUNT} recovery codes (devueltos en claro una sola vez).
     */
    public List<String> confirmEnrollment(UUID accountId, String code) {
        NexusAccount account = requireAccount(accountId);
        if (account.isMfaEnabled()) {
            throw new IllegalStateException("MFA already enabled");
        }
        if (account.getTotpSecretEnc() == null) {
            throw new IllegalStateException("No pending enrollment");
        }
        if (!verifyTotp(account, code, Instant.now())) {
            throw new IllegalArgumentException("Invalid code");
        }
        Instant now = Instant.now();
        account.enableTotp(now);
        accountRepository.save(account);
        return issueRecoveryCodes(account.getId(), now);
    }

    /** Desactiva la MFA tras verificar un código (TOTP o recovery) + borra los recovery codes. */
    public void disable(UUID accountId, String code) {
        NexusAccount account = requireAccount(accountId);
        if (!account.isMfaEnabled()) {
            throw new IllegalStateException("MFA not enabled");
        }
        Instant now = Instant.now();
        boolean ok = verifyTotp(account, code, now);
        if (!ok) {
            ok = consumeRecoveryCode(account, code, now);
        }
        if (!ok) {
            throw new IllegalArgumentException("Invalid code");
        }
        account.disableTotp();
        accountRepository.save(account);
        recoveryCodeRepository.deleteByNexusAccountId(account.getId());
    }

    /** Lectura del estado (¿activa? cuántos recovery codes quedan). */
    @Transactional(readOnly = true)
    public MfaStatus status(UUID accountId) {
        NexusAccount account = requireAccount(accountId);
        int remaining = account.isMfaEnabled()
                ? recoveryCodeRepository.findByNexusAccountIdAndConsumedAtIsNull(account.getId()).size()
                : 0;
        return new MfaStatus(account.isMfaEnabled(), remaining);
    }

    private boolean verifyTotp(NexusAccount account, String code, Instant now) {
        if (code == null || code.length() != TotpGenerator.DIGITS || account.getTotpSecretEnc() == null) {
            return false;
        }
        try {
            byte[] secret = Base32.decode(totpCrypto.decrypt(account.getTotpSecretEnc()));
            return TotpGenerator.verify(secret, code.trim(), now.getEpochSecond(), TOTP_WINDOW_STEPS);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean consumeRecoveryCode(NexusAccount account, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return recoveryCodeRepository.findByCodeHashAndConsumedAtIsNull(SecureHashes.sha256Hex(code.trim()))
                .map(rc -> { rc.consume(now); recoveryCodeRepository.save(rc); return true; })
                .orElse(false);
    }

    private List<String> issueRecoveryCodes(UUID accountId, Instant now) {
        List<NexusAccountRecoveryCode> entities = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            byte[] bytes = new byte[RECOVERY_CODE_BYTES];
            random.nextBytes(bytes);
            String code = Base32.encode(bytes);
            plaintext.add(code);
            entities.add(new NexusAccountRecoveryCode(accountId, SecureHashes.sha256Hex(code), now));
        }
        recoveryCodeRepository.saveAll(entities);
        return plaintext;
    }

    private NexusAccount requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Nexus account not found"));
    }
}
