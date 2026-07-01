package dev.unzor.nexus.vault.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Clave maestra del vault ({@code nexus.vault.master-key}). */
@ConfigurationProperties("nexus.vault")
public record VaultProperties(String masterKey) {
}
