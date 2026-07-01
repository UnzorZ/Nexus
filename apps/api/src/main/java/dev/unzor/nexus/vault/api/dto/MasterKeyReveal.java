package dev.unzor.nexus.vault.api.dto;

/** Revelación de la master key efectiva del proyecto (acción sensible, auditada). */
public record MasterKeyReveal(
        String masterKey,
        boolean overridden
) {
}
