package dev.unzor.nexus.vault.api.dto;

import dev.unzor.nexus.vault.domain.enums.VaultCipher;

import java.util.List;
import java.util.UUID;

/**
 * Vista de la configuración de cifrado del vault de un proyecto (sin revelar la
 * master key). La master key se revela por separado vía {@code GET /master-key}.
 */
public record VaultEncryptionInfo(
        UUID projectId,
        int secretCount,
        VaultCipher defaultCipher,
        List<VaultCipher> cipherOptions,
        boolean masterKeyOverridden
) {
}
