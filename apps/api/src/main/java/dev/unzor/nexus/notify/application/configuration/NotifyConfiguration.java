package dev.unzor.nexus.notify.application.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link NotifySmtpProperties}. */
@Configuration
@EnableConfigurationProperties(NotifySmtpProperties.class)
class NotifyConfiguration {
}
