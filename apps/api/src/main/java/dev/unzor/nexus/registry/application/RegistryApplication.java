package dev.unzor.nexus.registry.application;

import dev.unzor.nexus.registry.application.configuration.HeartbeatProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cablea {@link HeartbeatProperties} en el contexto del módulo registry. El
 * {@code @ApplicationModule} vive en el {@code package-info}.
 */
@Configuration
@EnableConfigurationProperties(HeartbeatProperties.class)
class RegistryApplication {
}
