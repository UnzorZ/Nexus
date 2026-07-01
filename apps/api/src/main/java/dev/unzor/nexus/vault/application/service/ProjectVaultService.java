package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.vault.api.dto.SecretSummary;
import dev.unzor.nexus.vault.api.dto.SecretValue;
import dev.unzor.nexus.vault.domain.entity.ProjectSecret;
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
 * Casos de uso del vault: escribe/rota/borra secretos cifrados (panel, sin leer
 * el valor) y los lee descifrados (runtime, auditado). El cifrado lo hace
 * {@link VaultCrypto}; el valor plano nunca se persiste ni se devuelve al panel.
 */
@Service
public class ProjectVaultService {

    private static final Set<String> KEY_UNIQUE_CONSTRAINTS = Set.of("uk_project_secrets_project_key");

    private final ProjectSecretRepository repository;
    private final ProjectLookupService projectLookupService;
    private final VaultCrypto crypto;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectVaultService(
            ProjectSecretRepository repository,
            ProjectLookupService projectLookupService,
            VaultCrypto crypto,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
        this.crypto = crypto;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<SecretSummary> listSecrets(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findAllByProjectId(projectId).stream().map(SecretSummary::from).toList();
    }

    @Transactional
    public SecretSummary createSecret(UUID projectId, String key, String value, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (repository.existsByProjectIdAndKey(projectId, key)) {
            throw new VaultSecretAlreadyExistsException(
                    "A secret named '" + key + "' already exists in this project.");
        }
        VaultCrypto.Encrypted encrypted = crypto.encrypt(value);
        try {
            ProjectSecret saved = repository.saveAndFlush(
                    new ProjectSecret(projectId, key, encrypted.ciphertextBase64(), encrypted.nonceBase64()));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "vault.secret.created", "vault_secret",
                    Objects.toString(saved.getId(), null), actorAccountId, Map.of("key", key)));
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
    public SecretSummary rotateSecret(UUID projectId, String key, String value, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectSecret secret = requireSecret(projectId, key);
        VaultCrypto.Encrypted encrypted = crypto.encrypt(value);
        secret.rotate(encrypted.ciphertextBase64(), encrypted.nonceBase64());
        SecretSummary summary = SecretSummary.from(repository.save(secret));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.secret.rotated", "vault_secret",
                Objects.toString(secret.getId(), null), actorAccountId, Map.of("key", key)));
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

    @Transactional
    public SecretValue readSecretValue(UUID projectId, String key) {
        projectLookupService.requireById(projectId);
        ProjectSecret secret = requireSecret(projectId, key);
        String value = crypto.decrypt(secret.getCiphertext(), secret.getNonce());
        // Cada lectura runtime se audita (acción sensible).
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "vault.read", "vault_secret", key, null, Map.of("key", key)));
        return new SecretValue(key, value);
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
