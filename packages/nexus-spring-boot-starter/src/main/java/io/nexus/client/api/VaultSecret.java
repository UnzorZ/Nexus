package io.nexus.client.api;

/**
 * Valor desencriptado de un secreto del vault ({@code GET
 * /api/v1/vault/secrets/{key}}, scope {@code vault:read}). Sólo {@code key} +
 * valor en claro (sin metadatos); el listado los aporta vía
 * {@link VaultSecretSummary}.
 */
public record VaultSecret(String key, String value) {
}
