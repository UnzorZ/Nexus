package dev.unzor.nexus.vault.application.service;

import dev.unzor.nexus.vault.domain.enums.VaultCipher;
import dev.unzor.nexus.vault.domain.entity.ProjectVaultSettings;
import dev.unzor.nexus.vault.persistence.repository.ProjectVaultSettingsRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve la master key efectiva de un proyecto: el override del proyecto (si
 * existe y está configurado) envuelto con la master key global, o la master key
 * global de la instancia en caso contrario. El override se guarda como
 * {@code base64(nonce).base64(ciphertext)} cifrado con AES-256-GCM + clave global.
 */
@Component
public class VaultKeyResolver {

    private static final String SEPARATOR = ".";

    private final ProjectVaultSettingsRepository settingsRepository;
    private final VaultCrypto crypto;

    public VaultKeyResolver(ProjectVaultSettingsRepository settingsRepository, VaultCrypto crypto) {
        this.settingsRepository = settingsRepository;
        this.crypto = crypto;
    }

    /** true si el proyecto tiene un override de master key (no usa la global). */
    public boolean hasOverride(UUID projectId) {
        return settingsRepository.findByProjectId(projectId)
                .map(s -> s.getMasterKeyEnc() != null && !s.getMasterKeyEnc().isBlank())
                .orElse(false);
    }

    public String resolveMasterKey(UUID projectId) {
        return settingsRepository.findByProjectId(projectId)
                .map(ProjectVaultSettings::getMasterKeyEnc)
                .filter(enc -> enc != null && !enc.isBlank())
                .map(this::unwrap)
                .orElseGet(crypto::globalMasterKey);
    }

    /** Persiste (o reemplaza) el override de master key del proyecto. */
    public void setOverride(UUID projectId, String newMasterKey) {
        String wrapped = wrap(newMasterKey);
        ProjectVaultSettings settings = settingsRepository.findByProjectId(projectId)
                .orElse(new ProjectVaultSettings(projectId, wrapped));
        settings.setMasterKeyEnc(wrapped);
        settingsRepository.save(settings);
    }

    private String wrap(String projectMasterKey) {
        VaultCrypto.Encrypted enc = crypto.encrypt(projectMasterKey, VaultCipher.AES_256_GCM, crypto.globalMasterKey());
        return enc.nonceBase64() + SEPARATOR + enc.ciphertextBase64();
    }

    private String unwrap(String combined) {
        int sep = combined.indexOf(SEPARATOR);
        if (sep < 0) {
            throw new IllegalStateException("Malformed vault master-key override");
        }
        String nonce = combined.substring(0, sep);
        String ciphertext = combined.substring(sep + 1);
        return crypto.decrypt(ciphertext, nonce, VaultCipher.AES_256_GCM, crypto.globalMasterKey());
    }
}
