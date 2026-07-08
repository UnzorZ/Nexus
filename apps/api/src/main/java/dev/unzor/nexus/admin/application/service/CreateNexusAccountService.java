package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.api.requests.CreateNexusAccountRequest;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.exception.NexusAccountEmailAlreadyExistsException;
import dev.unzor.nexus.admin.domain.exception.RegistrationClosedException;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.instance.application.service.InstanceSettingsService;
import dev.unzor.nexus.shared.validation.EmailNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Caso de uso para registrar una nueva cuenta Nexus.
 *
 * <p>Normaliza el email, comprueba duplicados, hashea la contraseña con
 * {@link PasswordEncoder} y persiste la entidad. Nunca acepta un hash de
 * contraseña desde la API.</p>
 */
@Service
public class CreateNexusAccountService {

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_nexus_accounts_email";

    private final NexusAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstanceAdminBootstrapService instanceAdminBootstrapService;
    private final InstanceSettingsService instanceSettings;

    public CreateNexusAccountService(
            NexusAccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            InstanceAdminBootstrapService instanceAdminBootstrapService,
            InstanceSettingsService instanceSettings
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.instanceAdminBootstrapService = instanceAdminBootstrapService;
        this.instanceSettings = instanceSettings;
    }

    /**
     * Crea una cuenta nueva y la activa de inmediato. Las cuentas del panel son
     * auto/provisionadas por el operador (o el bootstrap del primer admin) y nacen
     * activas; la verificación de email es una superficie del portal de usuario final
     * (ProjectUser, M2), no de las cuentas Nexus.
     */
    @Transactional
    public NexusAccountDetails create(CreateNexusAccountRequest request) {
        // Registro cerrado por el operador: rechaza nuevas altas, salvo el
        // bootstrap del primer admin (ADR-0010) si aún no existe ninguno.
        if (!instanceSettings.isRegistrationOpen() && accountRepository.existsByInstanceAdminTrue()) {
            throw new RegistrationClosedException();
        }

        String normalizedEmail = EmailNormalizer.normalize(request.email());
        String displayName = request.displayName().trim();

        if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new NexusAccountEmailAlreadyExistsException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        NexusAccount account = new NexusAccount(normalizedEmail, passwordHash, displayName);
        account.verifyEmail(Instant.now());

        NexusAccount saved;
        try {
            saved = accountRepository.saveAndFlush(account);
        }
        catch (DataIntegrityViolationException exception) {
            if (isUniqueEmailViolation(exception)) {
                throw new NexusAccountEmailAlreadyExistsException(normalizedEmail);
            }
            throw exception;
        }

        instanceAdminBootstrapService.grantInstanceAdminIfMissing(saved);

        return NexusAccountDetails.from(saved);
    }

    private static boolean isUniqueEmailViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && UNIQUE_EMAIL_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
