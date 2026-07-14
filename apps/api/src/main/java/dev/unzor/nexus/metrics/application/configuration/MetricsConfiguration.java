package dev.unzor.nexus.metrics.application.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link MetricsRetentionProperties}. */
@Configuration
@EnableConfigurationProperties(MetricsRetentionProperties.class)
class MetricsConfiguration {
}
