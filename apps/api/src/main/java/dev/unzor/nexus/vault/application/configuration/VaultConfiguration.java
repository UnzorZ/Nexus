package dev.unzor.nexus.vault.application.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link VaultProperties}. */
@Configuration
@EnableConfigurationProperties(VaultProperties.class)
class VaultConfiguration {
}
