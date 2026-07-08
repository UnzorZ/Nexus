package dev.unzor.nexus.audit.application.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link AuditRetentionProperties}. */
@Configuration
@EnableConfigurationProperties(AuditRetentionProperties.class)
class AuditConfiguration {
}
