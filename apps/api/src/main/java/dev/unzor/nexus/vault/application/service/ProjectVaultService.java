package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.vault.api.dto.MasterKeyReveal;
import dev.unzor.nexus.vault.api.dto.SecretSummary;
import dev.unzor.nexus.vault.api.dto.SecretValue;
import dev.unzor.nexus.vault.api.dto.VaultEncryptionInfo;
import dev.unzor.nexus.vault.domain.entity.ProjectSecret;
import dev.unzor.nexus.vault.domain.enums.VaultCipher;
import dev.unzor.nexus.vault.domain.exception.VaultSecretAlreadyExistsException;
import dev.unzor.nexus.vault.domain.exception.VaultSecretNotFoundException;
import dev.unzor.nexus.vault.persistence.repository.ProjectSecretRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso del vault: escribe/rota/borra secretos cifrados, los lee
 * descifrados (runtime + reveal del panel, auditado), y gestiona la
 * configuración de cifrado (selector de cipher, reveal/rotación de master key).
 * El valor plano nunca se persiste; el cifrado lo hace {@link VaultCrypto} con la
 * master key resuelta por {@link VaultKeyResolver}.
 */
@Service
public class ProjectVaultService {

    private static final Set<String> KEY_UNIQUE_CONSTRAINTS = Set.of("uk_project_secrets_project_key");

    private final ProjectSecretRepository repository;
    private final ProjectLookupService projectLookupService;
    private final VaultCrypto crypto;
    private final VaultKeyResolver keyResolver;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectVaultService(
            ProjectSecretRepository repository,
            ProjectLookupService projectLookupService,
            VaultCrypto crypto,
            VaultKeyResolver keyResolver,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
        this.crypto = crypto;
        this.keyResolver = keyResolver;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<SecretSummary> listSecrets(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findAllByProjectId(projectId).stream().map(SecretSummary::from).toList();
    }

    @Transactional
    public SecretSummary createSecret(UUID projectId, String key, String value,
                                      VaultCipher cipher, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (repository.existsByProjectIdAndKey(projectId, key)) {
            throw new VaultSecretAlreadyExistsException(
                    "A secret named '" + key + "' already exists in this project.");
        }
        String masterKey = keyResolver.resolveMasterKey(projectId);
        VaultCrypto.Encrypted encrypted = crypto.encrypt(value, cipher, masterKey);
        try {
            ProjectSecret saved = repository.saveAndFlush(
                    new ProjectSecret(projectId, key, encrypted.ciphertextBase64(),
                            encrypted.nonceBase64(), cipher.name()));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "vault.secret.created", "vault_secret",
                    Objects.toString(saved.getId(), null), actorAccountId,
                    Map.of("key", key, "cipher", cipher.name())));
            return SecretSummary.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isKeyUniqueViolation(exception)) {
                throw new VaultSecretAlreadyExistsException(
                        "A secret named '" + key + "' already exists in this project.");
            }
            throw exception;
        }
    }

    @Transactional
    public SecretSummary rotateSecret(UUID projectId, String key, String value,
                                      VaultCipher cipher, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectSecret secret = requireSecret(projectId, key);
        String masterKey = keyResolver.resolveMasterKey(projectId);
        VaultCrypto.Encrypted encrypted = crypto.encrypt(value, cipher, masterKey);
        secret.rotate(encrypted.ciphertextBase64(), encrypted.nonceBase64(), cipher.name());
        SecretSummary summary = SecretSummary.from(repository.save(secret));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.secret.rotated", "vault_secret",
                Objects.toString(secret.getId(), null), actorAccountId,
                Map.of("key", key, "cipher", cipher.name())));
        return summary;
    }

    @Transactional
    public void deleteSecret(UUID projectId, String key, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectSecret secret = requireSecret(projectId, key);
        repository.delete(secret);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.secret.deleted", "vault_secret",
                Objects.toString(secret.getId(), null), actorAccountId, Map.of("key", key)));
    }

    /** Lectura descifrada en runtime (API key, sin cuenta). Cada lectura se audita. */
    @Transactional
    public SecretValue readSecretValue(UUID projectId, String key) {
        return readSecretValue(projectId, key, null, "vault.read");
    }

    /** Revela el valor desde el panel (cuenta; acción sensible auditada). */
    @Transactional
    public SecretValue revealSecretValue(UUID projectId, String key, UUID actorAccountId) {
        return readSecretValue(projectId, key, actorAccountId, "vault.secret.revealed");
    }

    private SecretValue readSecretValue(UUID projectId, String key, UUID actorAccountId, String action) {
        projectLookupService.requireById(projectId);
        ProjectSecret secret = requireSecret(projectId, key);
        VaultCipher cipher = VaultCipher.fromKey(secret.getCipher());
        String value = crypto.decrypt(secret.getCiphertext(), secret.getNonce(), cipher,
                keyResolver.resolveMasterKey(projectId));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, action, "vault_secret", key, actorAccountId, Map.of("key", key)));
        return new SecretValue(key, value);
    }

    @Transactional(readOnly = true)
    public VaultEncryptionInfo encryptionInfo(UUID projectId) {
        projectLookupService.requireById(projectId);
        int count = (int) repository.findAllByProjectId(projectId).stream().count();
        return new VaultEncryptionInfo(projectId, count, VaultCipher.AES_256_GCM,
                List.of(VaultCipher.values()), keyResolver.hasOverride(projectId));
    }

    @Transactional
    public MasterKeyReveal revealMasterKey(UUID projectId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        String masterKey = keyResolver.resolveMasterKey(projectId);
        boolean overridden = keyResolver.hasOverride(projectId);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.master_key.revealed", "vault_master_key",
                projectId.toString(), actorAccountId, Map.of("overridden", overridden)));
        return new MasterKeyReveal(masterKey, overridden);
    }

    /**
     * Rota la master key del proyecto: re-cifra todos los secretos con la nueva
     * clave (manteniendo el cipher de cada uno) y persiste el override.
     */
    @Transactional
    public VaultEncryptionInfo rotateMasterKey(UUID projectId, String newMasterKey, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        String oldMasterKey = keyResolver.resolveMasterKey(projectId);
        List<ProjectSecret> secrets = repository.findAllByProjectId(projectId);
        for (ProjectSecret secret : secrets) {
            VaultCipher cipher = VaultCipher.fromKey(secret.getCipher());
            String plaintext = crypto.decrypt(secret.getCiphertext(), secret.getNonce(), cipher, oldMasterKey);
            VaultCrypto.Encrypted reencrypted = crypto.encrypt(plaintext, cipher, newMasterKey);
            secret.rotate(reencrypted.ciphertextBase64(), reencrypted.nonceBase64(), cipher.name());
            repository.save(secret);
        }
        keyResolver.setOverride(projectId, newMasterKey);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.master_key.rotated", "vault_master_key",
                projectId.toString(), actorAccountId, Map.of("secrets", secrets.size())));
        return encryptionInfo(projectId);
    }

    private ProjectSecret requireSecret(UUID projectId, String key) {
        return repository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new VaultSecretNotFoundException(
                        "Secret '" + key + "' not found in project " + projectId + "."));
    }

    private static boolean isKeyUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && KEY_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
